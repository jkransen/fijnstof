package nl.kransen.fijnstof

import cats._
import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import sttp.client._
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import nl.kransen.fijnstof.Main.AppTypes.{Measurement, MeasurementTarget}
import nl.kransen.fijnstof.Sds011.SdsMeasurement
import org.slf4j.LoggerFactory

class Domoticz private (host: String, port: Int, maybePm25Idx: Option[String], maybePm10Idx: Option[String], maybeCo2Idx: Option[String]) extends MeasurementTarget {

  private val log = LoggerFactory.getLogger("Domoticz")

  log.info(s"Domoticz host: $host, port: $port")
  log.info(s"PM2.5 IDX: $maybePm25Idx, PM10 IDX: $maybePm10Idx, CO₂ IDX: $maybeCo2Idx")

  implicit val backend = HttpURLConnectionBackend() // TODO  AsyncHttpClientCatsBackend // AsyncHttpClientFs2Backend // HttpURLConnectionBackend

  def savePM25(sdsMeasurement: SdsMeasurement): IO[Unit] = {
    val response = for {
      pm25Idx  <- OptionT.fromOption[IO](maybePm25Idx)
      _        <- OptionT.liftF(IO(log.debug("PM2.5 Measurement: " + sdsMeasurement.pm25str)))
      response <- OptionT.liftF(IO(basicRequest.post(uri"http://$host:$port/json.htm?type=command&param=udevice&idx=${pm25Idx}&nvalue=&svalue=${sdsMeasurement.pm25str}").send()))
      _        <- OptionT.liftF(IO(log.debug("Response: " + response.statusText)))
    } yield ()
    response.getOrElseF(IO(log.error("Domoticz PM2.5 failed")))
  }

  def savePM10(sdsMeasurement: SdsMeasurement): IO[Unit] = {
    val response = for {
      pm10Idx  <- OptionT.fromOption[IO](maybePm10Idx)
      _        <- OptionT.liftF(IO(log.debug("PM10 Measurement: " + sdsMeasurement.pm10str)))
      response <- OptionT.liftF(IO(basicRequest.post(uri"http://$host:$port/json.htm?type=command&param=udevice&idx=${pm10Idx}&nvalue=&svalue=${sdsMeasurement.pm10str}").send()))
      _        <- OptionT.liftF(IO(log.debug("Response: " + response.statusText)))
    } yield ()
    response.getOrElseF(IO(log.error("Domoticz PM10 failed")))
  }

  def saveCO2(co2Measurement: CO2Measurement): IO[Unit] = {
    val response = for {
      co2Idx   <- OptionT.fromOption[IO](maybeCo2Idx)
      _        <- OptionT.liftF(IO(log.debug("CO₂ Measurement: " + co2Measurement)))
      response <- OptionT.liftF(IO(basicRequest.post(uri"http://$host:$port/json.htm?type=command&param=udevice&idx=${co2Idx}&nvalue=${co2Measurement.str}").send()))
      _        <- OptionT.liftF(IO(log.debug("Response: " + response.statusText)))
    } yield ()
    response.getOrElseF(IO(log.error("Domoticz CO₂ failed")))
  }

  override def save(measurement: Measurement): IO[Unit] = {
    measurement match {
      case s@SdsMeasurement(_, _, _) => for {
        _ <- savePM25(s)
        _ <- savePM10(s)
      } yield ()
      case m@CO2Measurement(_) => saveCO2(m)
    }
  }
}

object Domoticz {

  def apply(config: Config): MeasurementTarget = {
    val host = config.getString("host")
    val port = config.getInt("port")
    val pm25Idx = config.as[Option[String]]("pm25Idx")
    val pm10Idx = config.as[Option[String]]("pm10Idx")
    val co2Idx = config.as[Option[String]]("co2Idx")
    new Domoticz(host, port, pm25Idx, pm10Idx, co2Idx)
  }
}
