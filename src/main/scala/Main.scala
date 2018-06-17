import Main.count
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import scala.reflect.io.File

object Main extends App {

  val log = LoggerFactory.getLogger("Main")

  val serialRegex = "Serial\\s*\\:\\s*0*([^0][0-9a-fA-F]+)".r
  val id = for {
    cpuinfo <- Try(File("/proc/cpuinfo").slurp()).toOption // TODO allow test to use file under test/resources
    firstMatch <- serialRegex.findFirstMatchIn(cpuinfo)
  } yield "fijnstof-" + firstMatch.group(1)
  log.info(s"Machine id: $id")

  private val config = ConfigFactory.load()
  val host = config.getString("domoticz.host")
  val port = config.getString("domoticz.port")
  val pm25Idx = config.getString("domoticz.pm25Idx")
  val pm10Idx = config.getString("domoticz.pm10Idx")
  val uartDevice = config.getString("uart.device")

  log.info(s"UART (Serial) device: $uartDevice")
  log.info(s"PM2.5 IDX: $pm25Idx, PM10 IDX: $pm10Idx")
  log.info(s"Domoticz host: $host, port: $port")

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  println(s"Machine ID: $id")

  // Serial.connect(uartDevice, in => Sds021Listener.listen(in, handleReport))

  var count = 0

  def handleReport(report: Report): Unit = {
    if (count == 0) {
      try {
        log.debug("Report: " + report)
        val get1 = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm25Idx&nvalue=&svalue=${report.pm25str}"
        log.debug(get1)
        val response1Future = Http().singleRequest(HttpRequest(uri = get1))
        response1Future.onComplete {
          case Success(response) => log.info("PM2.5 update: " + response.toString())
          case Failure(e) => log.error("Domoticz PM2.5 failed", e)
        }
        val response2Future = Http().singleRequest(HttpRequest(uri = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm10Idx&nvalue=&svalue=${report.pm10str}"))
        response2Future.onComplete {
          case Success(response) => log.info("PM10 update: " + response.toString())
          case Failure(e) => log.error("Domoticz PM10 failed", e)
        }
        sendLuftdaten(report)
      } catch {
        case e: Exception => log.error("Could not update sensors", e)
      }
      count = 30
    }
    count = count - 1
  }

  def sendLuftdaten(report: Report): Unit = {
    val postUrl = "https://api.luftdaten.info/v1/push-sensor-data/"
    val id = "fijnstof-" + report.id // id.getOrElse("fijnstof-" + report.id)))
    log.debug(s"Luftdaten ID: $id")

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
