package nl.kransen.fijnstof

import fs2._
import org.slf4j.LoggerFactory
import zio.{App, TaskR, ZIO}
import cats.implicits._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.interop.catz._

object AppTypes {
  type AppEnv = Clock with Blocking with Console
  type AppTask[A] = TaskR[AppEnv, A]
}

object Fs2Sds011 extends App {

  import AppTypes._

  override def run(args: List[String]): ZIO[AppEnv, Nothing, Int] = {
    val sds = Serial.findPort("TEST").get
    val program = for {
      _ <- console.putStrLn("Starting")
      converter <- ZIO.runtime[AppEnv].flatMap { implicit rts =>
        val is = ZIO.effect(sds.getInputStream)
        io.readInputStream(is, 1, rts.Platform.executor.asEC)
          .map(_.toInt & 0xff)
          .through(SdsStateMachine.collectMeasurements())
          .map(m => m.toString)
          .evalMap(s => ZIO.effectTotal(println(s.toString)))
          .compile
          .drain
      }
    } yield converter
    program.fold(_ => 1, _ => 0)
  }
}

object SdsStateMachine {

  private val log = LoggerFactory.getLogger("Sds011Protocol")

  def collectMeasurements[F[_]](): Pipe[F, Int, SdsMeasurement] = {

    def go(state: SdsState): Stream[F, Int] => Pull[F, SdsMeasurement, Unit] =
      _.pull.uncons1.flatMap {
        case Some((nextByte: Int, tail)) =>
          val nextState = state.nextState(nextByte)
          log.debug(f"Next byte: ${nextByte}%02x, next state: $nextState")
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

  case class SdsMeasurement(id: Int, pm25: Int, pm10: Int) {
    val pm25str = s"${pm25 / 10}.${pm25 % 10}"
    val pm10str = s"${pm10 / 10}.${pm10 % 10}"

    override def toString: String =
      s"Sds011 id=$id pm2.5=$pm25str pm10=$pm10str"
  }

  sealed trait SdsState {
    def nextState(nextByte: Int): SdsState
  }

  case object Init extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      if (nextByte == 0xaa) {
        Head
      } else {
        Init
      }
    }
  }

  case object Head extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      if (nextByte == 0xc0) {
        PM25Low
      } else {
        Init
      }
    }
  }

  case object PM25Low extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      PM25High(nextByte)
    }
  }

  case class PM25High(pm25Value: Int) extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      PM10Low(pm25Value + nextByte * 256, pm25Value + nextByte)
    }
  }

  case class PM10Low(pm25Value: Int, checksum: Int) extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      PM10High(pm25Value, nextByte, checksum + nextByte)
    }
  }

  case class PM10High(pm25Value: Int, pm10Value: Int, checksum: Int)
      extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      IdLow(pm25Value, pm10Value + nextByte * 256, checksum + nextByte)
    }
  }

  case class IdLow(pm25Value: Int, pm10Value: Int, checksum: Int)
      extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      IdHigh(pm25Value, pm10Value, nextByte, checksum + nextByte)
    }
  }

  case class IdHigh(pm25Value: Int, pm10Value: Int, idValue: Int, checksum: Int)
      extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      Checksum(
        pm25Value,
        pm10Value,
        idValue + nextByte * 256,
        checksum + nextByte
      )
    }
  }

  case class Checksum(pm25Value: Int,
                      pm10Value: Int,
                      idValue: Int,
                      checksum: Int)
      extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      if (nextByte == (checksum & 0xff)) {
        CompleteMeasurement(SdsMeasurement(idValue, pm25Value, pm10Value))
      } else {
        log.error(s"Checksum mismatch, expected=$checksum actual=$nextByte")
        Init
      }
    }
  }

  case class CompleteMeasurement(measurement: SdsMeasurement) extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      if (nextByte != 0xab) {
        log.error(s"Unexpected tail: $nextByte")
      }
      Init
    }
  }
}
