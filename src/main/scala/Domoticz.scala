import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

class Domoticz(host: String, port: Int, pm25Idx: String, pm10Idx: String) extends Actor {

  private val log = LoggerFactory.getLogger("Domoticz")

  log.info(s"Domoticz host: $host, port: $port")
  log.info(s"PM2.5 IDX: $pm25Idx, PM10 IDX: $pm10Idx")

  def save(pm25Measurement: Pm25Measurement): Unit = {
      log.debug("PM2.5 Measurement: " + pm25Measurement)
      val get1 = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm25Idx&nvalue=&svalue=${pm25Measurement.pm25str}"
      log.trace(get1)
      val response1Future = Http().singleRequest(HttpRequest(uri = get1))
      response1Future.onComplete {
        case Success(response) =>
          log.debug(s"PM2.5 update for IDX $pm25Idx successful")
          log.trace("Domoticz PM2.5 response: " + response.toString())
        case Failure(e) => log.error("Domoticz PM2.5 failed", e)
      }
    }

  def save(pm10Measurement: Pm10Measurement): Unit = {
    log.debug("PM10 Measurement: " + pm10Measurement)
    val get1 = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm25Idx&nvalue=&svalue=${pm10Measurement.pm10str}"
    log.trace(get1)
    val response2Future = Http().singleRequest(HttpRequest(uri = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm10Idx&nvalue=&svalue=${pm10Measurement.pm10str}"))
    response2Future.onComplete {
      case Success(response) =>
        log.debug(s"PM10 update for IDX $pm10Idx successful")
        log.trace("Domoticz PM10 response: " + response.toString())
      case Failure(e) => log.error("Domoticz PM10 failed", e)
    }
  }

  override def receive: Receive = {
    case pm25Measurement: Pm25Measurement => save(pm25Measurement)
    case pm10Measurement: Pm25Measurement => save(pm10Measurement)
  }
}

object Domoticz {

  def props(config: Config): Props = {
    val host = config.getString("host")
    val port = config.getInt("port")
    val pm25Idx = config.getString("pm25Idx")
    val pm10Idx = config.getString("pm10Idx")
    Props(new Domoticz(host, port, pm25Idx, pm10Idx))
  }
}
