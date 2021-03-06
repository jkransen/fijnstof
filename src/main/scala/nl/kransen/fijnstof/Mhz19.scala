package nl.kransen.fijnstof

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import cats.effect.{Blocker, ContextShift, IO}
import fs2.{Pipe, Pull, Stream, io}
import nl.kransen.fijnstof.Main.AppTypes
import org.slf4j.LoggerFactory
import purejavacomm.SerialPort

import scala.concurrent.ExecutionContext

case class CO2Measurement(ppm: Int) extends AppTypes.Measurement {
  val str: String = ppm.toString
}

object CO2Measurement {
  def average(ms: List[CO2Measurement]): CO2Measurement = {
    val ppmSum = ms.foldRight(0)((nxt, sum) => nxt.ppm + sum)
    CO2Measurement(ppmSum / ms.size)
  }
}

object Mhz19 {

  def apply(mhz19: SerialPort, interval: Int)(implicit ec: ExecutionContext, ex: ScheduledThreadPoolExecutor, cs: ContextShift[IO]): Stream[IO, CO2Measurement] = {

    val sendReadCommand: Runnable = new Runnable {
      private val readCommand = Array(0xff, 0x01, 0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79)
      def run(): Unit = {
        val out = mhz19.getOutputStream
        readCommand.map(_ & 0xff).foreach(out.write)
      }
    }
    ex.scheduleAtFixedRate(sendReadCommand, 0, 4, TimeUnit.SECONDS)

    for {
      blocker <- Stream.resource(Blocker[IO])
      stream <- io.readInputStream(IO(mhz19.getInputStream), 1, blocker)
        .map(_.toInt & 0xff)
        .through(Mhz19StateMachine.collectMeasurements())
    } yield stream
  }
}

object Mhz19StateMachine {

  import scala.language.higherKinds
  
  private val log = LoggerFactory.getLogger("MH-Z19")

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
      CO2Low(nextByte * 256)
    }
  }

  case class CO2Low(co2Value: Int) extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Ignore1(co2Value + nextByte, co2Value + nextByte)
    }
  }

  case class Ignore1(co2Value: Int, checksum: Int) extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Ignore2(co2Value, checksum + nextByte)
    }
  }

  case class Ignore2(co2Value: Int, checksum: Int) extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Ignore3(co2Value, checksum + nextByte)
    }
  }

  case class Ignore3(co2Value: Int, checksum: Int) extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Ignore4(co2Value, checksum + nextByte)
    }
  }

  case class Ignore4(co2Value: Int, checksum: Int) extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      Checksum(co2Value, checksum + nextByte)
    }
  }

  case class Checksum(co2Value: Int, checksum: Int) extends Mhz19State {
    def nextState(nextByte: Int): Mhz19State = {
      if (nextByte == (checksum & 0xff)) {
        CompleteMeasurement(CO2Measurement(co2Value))
      } else {
        log.error(s"Checksum mismatch, expected=${checksum & 0xff} actual=$nextByte")
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
