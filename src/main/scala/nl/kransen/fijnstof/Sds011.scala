package nl.kransen.fijnstof

import cats.effect._
import nl.kransen.fijnstof.Main.AppTypes.Measurement
import purejavacomm.SerialPort
import fs2._
import nl.kransen.fijnstof.Sds011.SdsMeasurement
import org.slf4j.LoggerFactory

object Sds011 {

  case class SdsMeasurement(id: Int, pm25: Int, pm10: Int) extends Measurement {
    val pm25str = s"${pm25 / 10}.${pm25 % 10}"
    val pm10str = s"${pm10 / 10}.${pm10 % 10}"

    override def toString: String =
      s"SDS011 id=$id pm2.5=$pm25str pm10=$pm10str"
  }

  def apply(sds: SerialPort, interval: Int)(implicit cs: ContextShift[IO]): Stream[IO, SdsMeasurement] =
    for {
       blocker <- Stream.resource(Blocker[IO])
       stream <- io.readInputStream(IO(sds.getInputStream), 1, blocker)
        .map(_.toInt & 0xff)
        .through(SdsStateMachine.collectMeasurements)
           .chunkN(interval, allowFewer = true)
           .map(ch => Sds011.average(ch.toVector))
    } yield stream

  def average(ms: Iterable[SdsMeasurement]): SdsMeasurement = {
    val (pm25Sum, pm10Sum) = ms.foldRight((0, 0))((nxt, sum) => (nxt.pm25 + sum._1, nxt.pm10 + sum._2))
    SdsMeasurement(ms.head.id, pm25Sum / ms.size, pm10Sum / ms.size)
  }
}

object SdsStateMachine {

  private val log = LoggerFactory.getLogger("SDS011")

  val collectMeasurements: Pipe[IO, Int, SdsMeasurement] = {

    def go(state: SdsState): Stream[IO, Int] => Pull[IO, SdsMeasurement, Unit] =
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

  case class PM10High(pm25Value: Int, pm10Value: Int, checksum: Int) extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      IdLow(pm25Value, pm10Value + nextByte * 256, checksum + nextByte)
    }
  }

  case class IdLow(pm25Value: Int, pm10Value: Int, checksum: Int) extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      IdHigh(pm25Value, pm10Value, nextByte, checksum + nextByte)
    }
  }

  case class IdHigh(pm25Value: Int, pm10Value: Int, idValue: Int, checksum: Int) extends SdsState {
    def nextState(nextByte: Int): SdsState = {
      Checksum(
        pm25Value,
        pm10Value,
        idValue + nextByte * 256,
        checksum + nextByte
      )
    }
  }

  case class Checksum(pm25Value: Int, pm10Value: Int, idValue: Int, checksum: Int) extends SdsState {
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
