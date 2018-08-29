package nl.kransen.fijnstof

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.syntax._
import net.ceedubs.ficus.Ficus._
import nl.kransen.fijnstof.Luftdaten.toJson
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Luftdaten(luftdatenId: Option[String])(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer) extends Actor {

  private val log = LoggerFactory.getLogger("Luftdaten")

  log.info(s"Luftdaten ID: $luftdatenId")

  val postUrl = "https://api.luftdaten.info/v1/push-sensor-data/"

  def save(pm25Measurement: Pm25Measurement, pm10Measurement: Pm10Measurement): Unit = {
    val id = luftdatenId.getOrElse("fijnstof-" + pm25Measurement.id)
    val json = toJson(pm25Measurement, pm10Measurement)
    log.trace(s"JSON: $json")

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = postUrl, method = HttpMethods.POST)
      .withHeaders(RawHeader("X-PIN", "1"), RawHeader("X-Sensor", id))
      .withEntity(HttpEntity(ContentTypes.`application/json`, json)))

    responseFuture.onComplete {
      case Success(response) =>
        response.entity.toStrict(FiniteDuration(5, "seconds")).map(entity =>
          log.debug(s"Luftdaten succeeded: $entity"))
      case Failure(e) => log.error("Luftdaten failed", e)
    }
  }

  override def receive: Receive = {
    case (pm25Measurement: Pm25Measurement, pm10Measurement: Pm10Measurement) => save(pm25Measurement, pm10Measurement)
  }
}

object Luftdaten {

  def props(config: Config)(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer): Props = {
    val id: Option[String] = config.as[Option[String]]("id").orElse(Main.machineId)
    Props(new Luftdaten(id))
  }

  def toJson(pm25Measurement: Pm25Measurement, pm10Measurement: Pm10Measurement): String = {
    LuftdatenPayload(SensorDataValue("P1", pm10Measurement.pm10str) :: SensorDataValue("P2", pm25Measurement.pm25str) :: Nil).asJson.noSpaces
  }
}

case class SensorDataValue(value_type: String, value: String)
case class LuftdatenPayload(sensordatavalues: List[SensorDataValue], software_version: String = "fijnstof 1.0")