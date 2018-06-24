import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

class Domoticz(host: String, port: Int, pm25Idx: String, pm10Idx: String)(implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContextExecutor)  extends MeasurementHandler {

  private val log = LoggerFactory.getLogger("Domoticz")

  log.info(s"Domoticz host: $host, port: $port")
  log.info(s"PM2.5 IDX: $pm25Idx, PM10 IDX: $pm10Idx")

  override def handle(measurement: Measurement): Unit = measurement match {
    case sds011Measurement: Sds011Measurement => {
      try {
        log.debug("Measurement: " + sds011Measurement)
        val get1 = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm25Idx&nvalue=&svalue=${sds011Measurement.pm25str}"
        log.debug(get1)
        val response1Future = Http().singleRequest(HttpRequest(uri = get1))
        response1Future.onComplete {
          case Success(response) => log.debug("PM2.5 update: " + response.toString())
          case Failure(e) => log.error("Domoticz PM2.5 failed", e)
        }
        val response2Future = Http().singleRequest(HttpRequest(uri = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm10Idx&nvalue=&svalue=${sds011Measurement.pm10str}"))
        response2Future.onComplete {
          case Success(response) => log.debug("PM10 update: " + response.toString())
          case Failure(e) => log.error("Domoticz PM10 failed", e)
        }
      } catch {
        case e: Exception => log.error("Could not update sensors in Domoticz", e)
      }
    }
  }
}

object Domoticz {
  def apply(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContextExecutor): Domoticz = {
    val host = config.getString("host")
    val port = config.getInt("port")
    val pm25Idx = config.getString("pm25Idx")
    val pm10Idx = config.getString("pm10Idx")
    new Domoticz(host, port, pm25Idx, pm10Idx)
  }
}
