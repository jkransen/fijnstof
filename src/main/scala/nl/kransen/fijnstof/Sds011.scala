package nl.kransen.fijnstof

import java.io.InputStream
import java.util.concurrent.Executors

import zio.{ZIO, _}
import nl.kransen.fijnstof.Main.AppTypes.{AppEnv, Measurement}
import purejavacomm.SerialPort
import fs2._
import nl.kransen.fijnstof.Sds011.SdsStateMachine.SdsMeasurement
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object Sds011 {

  private val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def apply(sds: SerialPort, interval: Int): Stream[RIO[AppEnv, SdsMeasurement], SdsMeasurement] = {
    val program = io.readInputStream(ZIO.runtime[AppEnv]
      .flatMap { implicit rts =>
        ZIO(sds.getInputStream)
      },1, ec)
      .map(_.toInt & 0xff)
      .through(SdsStateMachine.collectMeasurements())
    program
  }

  object SdsStateMachine {

    private val log = LoggerFactory.getLogger("SDS011")

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

    case class SdsMeasurement(id: Int, pm25: Int, pm10: Int) extends Measurement {
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
}