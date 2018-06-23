import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class Luftdaten(id: String)(implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContextExecutor) extends MeasurementHandler {

  private val log = LoggerFactory.getLogger("Luftdaten")

  log.debug(s"Luftdaten ID: $id")

  val postUrl = "https://api.luftdaten.info/v1/push-sensor-data/"

  override def handle(measurement: Measurement): Unit = measurement match {
    case report: Sds011Measurement =>
      val id = "fijnstof-" + report.id // machineId.getOrElse("fijnstof-" + report.id)))

      val json = s"""
                  |{
                  |    "software_version": "fijnstof 1.0",
                  |    "sensordatavalues": [
                  |        {"value_type": "P1", "value": "${report.pm10str}"},
                  |        {"value_type": "P2", "value": "${report.pm25str}"}
                  |    ]
                  |}
       """.stripMargin

      log.debug(s"JSON: $json")

      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = postUrl, method = HttpMethods.POST)
        .withHeaders(RawHeader("X-PIN", "1"), RawHeader("X-Sensor", id))
        .withEntity(entity = HttpEntity(ContentTypes.`application/json`, json)))

      responseFuture.onComplete {
        case Success(response) =>
          response.entity.toStrict(FiniteDuration(1, "second")).map(entity =>
            log.info(s"Luftdaten succeeded: $entity"))
        case Failure(e) => log.error("Luftdaten failed", e)
      }
    }
}

object Luftdaten {

  def apply(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContextExecutor): Luftdaten = {
    val id = config.getString("id")
    new Luftdaten(id)
  }
}