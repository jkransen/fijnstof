package nl.kransen.fijnstof

import sttp.client._
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import nl.kransen.fijnstof.Main.AppTypes.MeasurementTarget
import nl.kransen.fijnstof.SdsStateMachine.SdsMeasurement
import org.slf4j.LoggerFactory

class Domoticz private(host: String, port: Int, maybePm25Idx: Option[String], maybePm10Idx: Option[String], maybeCo2Idx: Option[String]) extends MeasurementTarget {

  private val log = LoggerFactory.getLogger("Domoticz")

  log.info(s"Domoticz host: $host, port: $port")
  log.info(s"PM2.5 IDX: $maybePm25Idx, PM10 IDX: $maybePm10Idx, CO2 IDX: $maybeCo2Idx")

  implicit val backend = HttpURLConnectionBackend() // AsyncHttpClientZioBackend()

  def save(pmMeasurement: SdsMeasurement): Unit = {
    maybePm25Idx match {
      case Some(pm25Idx) =>
        log.debug("PM2.5 Measurement: " + pmMeasurement.pm25str)
        val get1 = s""
        log.trace(get1)
        val request = basicRequest
          .post(uri"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm25Idx&nvalue=&svalue=${pmMeasurement.pm25str}")
        val response = request.send()
        if (response.isSuccess) {
          log.trace("Domoticz PM2.5 response: " + response.toString())
          log.info(s"PM2.5 update for IDX $pm25Idx successful: ${pmMeasurement.pm25str} µg/m³")
        } else {
          log.error(s"Domoticz PM2.5 failed: ${response.statusText}")
        }
      case None =>
        log.warn("Received PM2.5 measurement, but no IDX set")
    }

    maybePm10Idx match {
      case Some(pm10Idx) =>
        log.debug("PM10 Measurement: " + pmMeasurement.pm10str)
        val get1 = s""
        log.trace(get1)
        val request = basicRequest
          .post(uri"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm10Idx&nvalue=&svalue=${pmMeasurement.pm10str}")
        val response = request.send()
        if (response.isSuccess) {
          log.trace("Domoticz PM10 response: " + response.toString())
          log.info(s"PM10 update for IDX $pm10Idx successful: ${pmMeasurement.pm10str} µg/m³")
        } else {
          log.error(s"Domoticz PM10 failed: ${response.statusText}")
        }
      case None =>
        log.warn("Received PM10 measurement, but no IDX set")
    }
  }

  def save(co2Measurement: CO2Measurement): Unit = {
    maybeCo2Idx match {
      case Some(co2Idx) =>
        log.debug("CO2 Measurement: " + co2Measurement)
        val request = basicRequest
          .post(uri"http://$host:$port/json.htm?type=command&param=udevice&idx=$co2Idx&nvalue=&svalue=${co2Measurement.str}")
        val response = request.send()
        if (response.isSuccess) {
          log.trace("Domoticz CO2 response: " + response.toString())
          log.info(s"CO2 update for IDX $co2Idx successful: ${co2Measurement.str} ppm")
        } else {
          log.error(s"Domoticz CO2 failed: ${response.statusText}")
        }
      case None =>
        log.warn("Received PM2.5 measurement, but no IDX set")
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
