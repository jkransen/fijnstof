package nl.kransen.fijnstof

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import fs2.{Pipe, Pull, Stream, io}
import nl.kransen.fijnstof.SdsStateMachine.SdsMeasurement
import org.slf4j.LoggerFactory
import purejavacomm.SerialPort
import zio.{Task, ZIO}
import zio.interop.catz.console

import scala.concurrent.ExecutionContext

case class CO2Measurement(ppm: Int) {
  val str: String = ppm.toString
}

object CO2Measurement {
  def average(ms: List[CO2Measurement]): CO2Measurement = {
    val ppmSum = ms.foldRight(0)((nxt, sum) => nxt.ppm + sum)
    CO2Measurement(ppmSum / ms.size)
  }
}

object Mhz19 {
  private val log = LoggerFactory.getLogger("MH-Z19")

  def apply(sds: SerialPort, interval: Int)(implicit ec: ExecutionContext, ex: ScheduledThreadPoolExecutor): Task[Stream[SdsMeasurement]] = {

    val sendReadCommand: Runnable = new Runnable {
      val readCommand = Array(0xff, 0x01, 0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79)
      def run(): Unit = {
        val out = sds.getOutputStream
        readCommand.map(_ & 0xff).foreach(out.write)
      }
    }
    ex.scheduleAtFixedRate(sendReadCommand, 1, 1, TimeUnit.SECONDS)

    for {
      _      <- console.putStrLn("Starting SDS011")
      is     <- ZIO.effect(sds.getInputStream)
      stream <- io.readInputStream(is, 1, ec)
        .map(_.toInt & 0xff)
        .through(SdsStateMachine.collectMeasurements())
    } yield stream
  }
}

object Mhz19StateMachine {

  private val log = LoggerFactory.getLogger("SDS011")

  def collectMeasurements[F[_]](): Pipe[F, Int, CO2Measurement] = {

    def go(state: Mhz19State): Stream[F, Int] => Pull[F, CO2Measurement, Unit] =
      _.pull.uncons1.flatMap {
        case Some((nextByte: Int, tail)) =>
          val nextState = state.nextState(nextByte)
          log.trace(f"Next byte: ${nextByte}%02x, next state: $nextState")
          nextState match {
            case CompleteMeasurement(measurement) =>
              Pull.output1(measurement) >> go(nextState)(tail)
            case _ =>
              go(nextState)(tail)
          }
        case None =>
          Pull.done
      }

    go(Init)(_).stream
  }

  sealed trait Mhz19State {
    def nextState(nextByte: Int): Mhz19State
  }

  case object Init extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      if (nextByte == 0xff) {
        Head
      } else {
        Init
      }
    }
  }

  case object Head extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      if (nextByte == 0x86) {
        CO2High
      } else {
        Init
      }
    }
  }

  case object CO2High extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      CO2Low(nextByte)
    }
  }

  case class CO2Low(co2Value: Int) extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Ignore1(co2Value * 256 + nextByte, co2Value + nextByte)
    }
  }

  case class Ignore1(co2Value: Int, checksum: Int) extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Ignore2(co2Value, checksum + nextByte)
    }
  }

  case class Ignore2(co2Value: Int, checksum: Int)
    extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Ignore3(co2Value, checksum + nextByte)
    }
  }

  case class Ignore3(co2Value: Int, checksum: Int)
    extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Ignore4(co2Value, checksum + nextByte)
    }
  }

  case class Ignore4(co2Value: Int, checksum: Int)
    extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Checksum(co2Value, checksum + nextByte)
    }
  }

  case class Checksum(co2Value: Int, checksum: Int)
    extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      if (nextByte == (checksum & 0xff)) {
        CompleteMeasurement(CO2Measurement(co2Value))
      } else {
        log.error(s"Checksum mismatch, expected=$checksum actual=$nextByte")
        Init
      }
    }
  }

  case class CompleteMeasurement(measurement: CO2Measurement) extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      if (nextByte != 0xab) {
        log.error(s"Unexpected tail: $nextByte")
      }
      Init
    }
  }
}
