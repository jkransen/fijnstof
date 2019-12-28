package nl.kransen.fijnstof

import cats.effect.IO
import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.syntax._
import net.ceedubs.ficus.Ficus._
import nl.kransen.fijnstof.Luftdaten.toJson
import nl.kransen.fijnstof.Main.AppTypes
import nl.kransen.fijnstof.Main.AppTypes.{AppTask, MeasurementTarget}
import nl.kransen.fijnstof.Sds011.SdsMeasurement
import org.slf4j.LoggerFactory
import sttp.client._
import zio.{RIO, console}

class Luftdaten private (luftdatenId: Option[String]) extends MeasurementTarget {

  private val log = LoggerFactory.getLogger("Luftdaten")

  implicit val backend = HttpURLConnectionBackend()

  private val request = basicRequest
    .post(uri"https://api.luftdaten.info/v1/push-sensor-data/")
    .header("X-PIN", "1")
    .contentType("application/json")

  def savePM(pmMeasurement: SdsMeasurement): AppTask[Unit] = {
    val id = luftdatenId.getOrElse("fijnstof-" + pmMeasurement.id)
    for {
      json     <- RIO.effect(toJson(pmMeasurement))
      _        <- console.putStrLn(s"JSON: $json")
      response <- RIO.effect(request.header("X-Sensor", id).body(json).send())
      _        <- if (response.isSuccess) {
                    console.putStrLn(s"Luftdaten succeeded: ${response.body}")
                  } else {
                    console.putStrLn(s"Luftdaten failed: ${response.statusText}")
                  }
    } yield ()
  }

  override def save(measurement: AppTypes.Measurement): AppTask[Unit] = measurement match {
    case sds @ SdsMeasurement(_, _, _) => savePM(sds)
  }
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
case class LuftdatenPayload(sensordatavalues: List[SensorDataValue], software_version: String = "fijnstof 1.2")
