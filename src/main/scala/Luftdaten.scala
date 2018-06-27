import Luftdaten.toJson
import Main.machineId
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import net.ceedubs.ficus.Ficus._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import io.circe.generic.auto._
import io.circe.syntax._

class Luftdaten(luftdatenId: Option[String])(implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContextExecutor) extends MeasurementHandler {

  private val log = LoggerFactory.getLogger("Luftdaten")

  log.debug(s"Luftdaten ID: $luftdatenId")

  val postUrl = "https://api.luftdaten.info/v1/push-sensor-data/"

  override def handle(measurement: Measurement): Unit = measurement match {
    case sds011Measurement: Sds011Measurement =>
      val id = luftdatenId.getOrElse(machineId.getOrElse("fijnstof-" + sds011Measurement.id))
      val json = toJson(sds011Measurement)
      log.debug(s"JSON: $json")

      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = postUrl, method = HttpMethods.POST)
        .withHeaders(RawHeader("X-PIN", "1"), RawHeader("X-Sensor", id))
        .withEntity(HttpEntity(ContentTypes.`application/json`, json)))

      responseFuture.onComplete {
        case Success(response) =>
          response.entity.toStrict(FiniteDuration(1, "second")).map(entity =>
            log.debug(s"Luftdaten succeeded: $entity"))
        case Failure(e) => log.error("Luftdaten failed", e)
      }
    }
}

object Luftdaten {

  def apply(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContextExecutor): Luftdaten = {
    val id = config.as[Option[String]]("id")
    new Luftdaten(id)
  }

  def toJson(sds011Measurement: Sds011Measurement): String = {
    LuftdatenPayload(SensorDataValue("P1", sds011Measurement.pm10str) :: SensorDataValue("P2", sds011Measurement.pm25str) :: Nil).asJson.noSpaces
  }
}

case class SensorDataValue(value_type: String, value: String)
case class LuftdatenPayload(sensordatavalues: List[SensorDataValue], software_version: String = "fijnstof 1.0")