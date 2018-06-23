import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import scala.reflect.io.File

object Main extends App {

  private val log = LoggerFactory.getLogger("Main")

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  def executeConfig(config: Config ): Unit = {
    val uartDevice = config.getString("device")
    log.info(s"UART (Serial) device: $uartDevice")


    val domoticz = if (config.hasPath("domoticz")) {
      Some(Domoticz(config.getConfig("domoticz")))
    } else None
    val luftdaten = if (config.hasPath("luftdaten")) {
      Some(Luftdaten(config.getConfig("luftdaten")))
    } else None

    var count = 0

    def handleReport(report: Measurement): Unit = {
      if (count <= 0) {
        try {
          domoticz.foreach(_.handle(report))
          luftdaten.foreach(_.handle(report))
          count = 150
        } catch {
          case e: Exception => log.error("Could not send measurement")
        }
      }
      count = count - 1
    }

    if (args.contains("list")) {
      Serial.listPorts.foreach(port => log.info(s"Serial port: ${port.getName}"))
    } else {
      Serial.connect(uartDevice) match {
        case Some(is) if args.contains("test") => Sds011Reader.stream(is).headOption.foreach(handleReport)
        case Some(is) => Sds011Reader.stream(is).foreach(handleReport)
        case None => log.error("Serial device not found")
      }
    }
  }

  val machineId: Option[String] = {
    val serialRegex = "Serial\\s*\\:\\s*0*([^0][0-9a-fA-F]+)".r
    for {
      cpuinfo <- Try(File("/proc/cpuinfo").slurp()).toOption
      firstMatch <- serialRegex.findFirstMatchIn(cpuinfo)
    } yield "fijnstof-" + firstMatch.group(1)
  }

  log.info(s"Machine id: $machineId")

  ConfigFactory.load().getConfigList("devices").forEach(executeConfig)
}
