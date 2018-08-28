import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import net.ceedubs.ficus.Ficus._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class Domoticz(host: String, port: Int, maybePm25Idx: Option[String], maybePm10Idx: Option[String], maybeCo2Idx: Option[String])(implicit system: ActorSystem, ec: ExecutionContext) extends Actor {

  private val log = LoggerFactory.getLogger("Domoticz")

  log.info(s"Domoticz host: $host, port: $port")
  log.info(s"PM2.5 IDX: $maybePm25Idx, PM10 IDX: $maybePm10Idx, CO2 IDX: $maybeCo2Idx")

  def save(pm25Measurement: Pm25Measurement): Unit = {
    maybePm25Idx match {
      case Some(pm25Idx) =>
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
      case None =>
        log.warn("Received PM2.5 measurement, but no IDX set")
    }
  }

  def save(pm10Measurement: Pm10Measurement): Unit = {
    maybePm10Idx match {
      case Some(pm10Idx) =>
        log.debug("PM10 Measurement: " + pm10Measurement)
        val get1 = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm10Idx&nvalue=&svalue=${pm10Measurement.pm10str}"
        log.trace(get1)
        val response2Future = Http().singleRequest(HttpRequest(uri = get1))
        response2Future.onComplete {
          case Success(response) =>
            log.debug(s"PM10 update for IDX $pm10Idx successful")
            log.trace("Domoticz PM10 response: " + response.toString())
          case Failure(e) => log.error("Domoticz PM10 failed", e)
        }
      case None =>
        log.warn("Received PM10 measurement, but no IDX set")
    }
  }

  def save(co2Measurement: CO2Measurement): Unit = {
    maybeCo2Idx match {
      case Some(co2Idx) =>
        log.debug("CO2 Measurement: " + co2Measurement)
        val get1 = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$co2Idx&nvalue=${co2Measurement.str}"
        log.trace(get1)
        val response2Future = Http().singleRequest(HttpRequest(uri = get1))
        response2Future.onComplete {
          case Success(response) =>
            log.debug(s"CO2 update for IDX $co2Idx successful")
            log.trace("Domoticz CO2 response: " + response.toString())
          case Failure(e) => log.error("Domoticz CO2 failed", e)
        }
      case None =>
        log.warn("Received CO2 measurement, but no IDX set")
    }
  }

  override def receive: Receive = {
    case pm25Measurement: Pm25Measurement => save(pm25Measurement)
    case pm10Measurement: Pm10Measurement => save(pm10Measurement)
    case (pm25Measurement: Pm25Measurement, pm10Measurement: Pm10Measurement) =>
      save(pm25Measurement)
      save(pm10Measurement)
    case co2Measurement: CO2Measurement => save(co2Measurement)
  }
}

object Domoticz {

  def props(config: Config)(implicit system: ActorSystem, ec: ExecutionContext): Props = {
    val host = config.getString("host")
    val port = config.getInt("port")
    val pm25Idx = config.as[Option[String]]("pm25Idx")
    val pm10Idx = config.as[Option[String]]("pm10Idx")
    val co2Idx = config.as[Option[String]]("co2Idx")
    Props(new Domoticz(host, port, pm25Idx, pm10Idx, co2Idx))
  }
}
