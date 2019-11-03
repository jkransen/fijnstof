package nl.kransen.fijnstof

import java.io.InputStream

import org.slf4j.LoggerFactory


case class Pm25Measurement(id: Int, pm25: Int) {
  val pm25str = s"${pm25 / 10}.${pm25 % 10}"

  override def toString: String = s"Sds011 id=$id pm2.5=$pm25str"
}

case class Pm10Measurement(id: Int, pm10: Int) {
  val pm10str = s"${pm10 / 10}.${pm10 % 10}"

  override def toString: String = s"Sds011 id=$id pm10=$pm10str"
}


import java.io.IOException

import zio.console.Console
import zio.{DefaultRuntime, ZIO}


import zio.blocking._
import zio.console._

object Sds011Protocol extends zio.App {

  private val log = LoggerFactory.getLogger("Sds011Protocol")

  private val ports = Serial.listPorts.map(_.getName)
  println(ports.mkString(" "))

//  def renderState(state: State): ZIO[Console with Blocking, IOException, State] = {
//    putStrLn("Bye")
//    ZIO.succeed(state)
//  }

  private val sds = Serial.findPort("ttyUSB1").get

  private val is = sds.getInputStream

  private val readByte = effectBlocking(is.read()).refineToOrDie[IOException]

  def measurementLoop(state: State): ZIO[Console with Blocking, IOException, CompleteMeasurement] = for {
    byte       <- readByte
    _          <- putStr( s"${byte.toHexString} ")
    nextState  <- ZIO.succeed(state.nextState(byte))
    endState   <- if (nextState.isInstanceOf[CompleteMeasurement]) ZIO.succeed(nextState) else measurementLoop(nextState)
  } yield endState.asInstanceOf[CompleteMeasurement]

  val programLoop: ZIO[Console with Blocking, IOException, Unit] = for {
    meas <- measurementLoop(Init)
    _ <- putStrLn(s"Measurement PM2.5: ${meas.pm25.pm25str} PM10: ${meas.pm10.pm10str}")
    _ <- programLoop
  } yield ()

  override def run(args: List[String]) =
    programLoop.fold(_ => 1, _ => 0)

  def average(ms: List[(Pm25Measurement, Pm10Measurement)]): (Pm25Measurement, Pm10Measurement) = {
    val (pm25agg, pm10agg) = ms.foldRight((0,0))((nxt, agg) => (nxt._1.pm25 + agg._1, nxt._2.pm10 + agg._2))
    (Pm25Measurement(ms.head._1.id, pm25agg / ms.size), Pm10Measurement(ms.head._2.id, pm10agg / ms.size))
  }

  sealed trait State {
    def nextState(nextByte: Int): State
  }

  case object Init extends State {
    def nextState(nextByte: Int): State = {
      if (nextByte == 0xaa) {
        Head
      } else {
        Init
      }
    }
  }

  case object Head extends State {
    def nextState(nextByte: Int): State = {
      if (nextByte == 0xc0) {
        PM25Low
      } else {
        Init
      }
    }
  }

  case object PM25Low extends State {
    def nextState(nextByte: Int): State = {
      PM25High(nextByte)
    }
  }

  case class PM25High(pm25Value: Int) extends State {
    def nextState(nextByte: Int): State = {
      PM10Low(pm25Value + nextByte * 256, pm25Value + nextByte)
    }
  }

  case class PM10Low(pm25Value: Int, checksum: Int) extends State {
    def nextState(nextByte: Int): State = {
      PM10High(pm25Value, nextByte, checksum + nextByte)
    }
  }

  case class PM10High(pm25Value: Int, pm10Value: Int, checksum: Int) extends State {
    def nextState(nextByte: Int): State = {
      IdLow(pm25Value, pm10Value + nextByte * 256, checksum + nextByte)
    }
  }

  case class IdLow(pm25Value: Int, pm10Value: Int, checksum: Int) extends State {
    def nextState(nextByte: Int): State = {
      IdHigh(pm25Value, pm10Value, nextByte, checksum + nextByte)
    }
  }

  case class IdHigh(pm25Value: Int, pm10Value: Int, idValue: Int, checksum: Int) extends State {
    def nextState(nextByte: Int): State = {
      Checksum(pm25Value, pm10Value, idValue + nextByte * 256, checksum + nextByte)
    }
  }

  case class Checksum(pm25Value: Int, pm10Value: Int, idValue: Int, checksum: Int) extends State {
    def nextState(nextByte: Int): State = {
      if (nextByte == (checksum & 0xff)) {
        Tail(pm25Value, pm10Value, idValue)
      } else {
        log.error("Checksum mismatch")
        Init
      }
    }
  }

  case class Tail(pm25Value: Int, pm10Value: Int, idValue: Int) extends State {
    def nextState(nextByte: Int): State = {
      if (nextByte == 0xab) {
        CompleteMeasurement(Pm25Measurement(idValue, pm25Value), Pm10Measurement(idValue, pm10Value))
      } else {
        log.error("Unexpected tail")
        Init
      }
    }
  }

  case class CompleteMeasurement(pm25: Pm25Measurement, pm10: Pm10Measurement) extends State {
    // Unused!
    def nextState(nextByte: Int): State = {
      log.error("Should not continue with completed measurement")
      Init
    }
  }
}
