package nl.kransen.fijnstof

import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.syntax._
import net.ceedubs.ficus.Ficus._
import nl.kransen.fijnstof.Luftdaten.toJson
import nl.kransen.fijnstof.Main.AppTypes
import nl.kransen.fijnstof.Main.AppTypes.MeasurementTarget
import nl.kransen.fijnstof.SdsStateMachine.SdsMeasurement
import org.slf4j.LoggerFactory
import sttp.client._

class Luftdaten private (luftdatenId: Option[String]) extends MeasurementTarget {

  private val log = LoggerFactory.getLogger("Luftdaten")

  implicit val backend = HttpURLConnectionBackend()

  log.info(s"Luftdaten ID: $luftdatenId")

  private val request = basicRequest
    .post(uri"https://api.luftdaten.info/v1/push-sensor-data/")
    .header("X-PIN", "1")
    .contentType("application/json")

  def save(pmMeasurement: SdsMeasurement): Unit = {
    val id = luftdatenId.getOrElse("fijnstof-" + pmMeasurement.id)
    val json = toJson(pmMeasurement)
    log.trace(s"JSON: $json")

    val response = request.header("X-Sensor", id).body(json).send()

    if (response.isSuccess) {
      log.debug(s"Luftdaten succeeded: ${response.body}")
    } else {
      log.error("Luftdaten failed: ${response.statusText}")
    }
  }

  override def save(measurement: AppTypes.Measurement): Unit = ???
}

object Luftdaten {

  def apply(config: Config): MeasurementTarget = {
    val id: Option[String] = config.as[Option[String]]("id").orElse(Main.machineId)
    new Luftdaten(id)
  }

  def toJson(pmMeasurement: SdsMeasurement): String = {
    LuftdatenPayload(SensorDataValue("P1", pmMeasurement.pm10str) :: SensorDataValue("P2", pmMeasurement.pm25str) :: Nil).asJson.noSpaces
  }
}

case class SensorDataValue(value_type: String, value: String)
case class LuftdatenPayload(sensordatavalues: List[SensorDataValue], software_version: String = "fijnstof 1.0")
