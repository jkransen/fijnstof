import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import net.ceedubs.ficus.Ficus._

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import scala.reflect.io.File

object Main extends App {

  private val log = LoggerFactory.getLogger("Main")

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  def runFlow(isTest: Boolean)(config: Config): Unit = {
    val uartDevice = config.getString("device")
    log.info(s"UART (Serial) device: $uartDevice")

    val sinks = Seq(
      config.as[Option[Config]]("domoticz").map(Domoticz(_)),
      config.as[Option[Config]]("luftdaten").map(Luftdaten(_))
    ).collect { case Some(sink) => sink }

    def handleMeasurement(measurement: Measurement): Unit = {
      sinks.foreach(_.handle(measurement))
    }

    Serial.connect(uartDevice) match {
      case Some(is) if isTest => Sds011Reader.stream(is).headOption.foreach(handleMeasurement)
      case Some(is) => Sds011Reader.stream(is).grouped(90).map(_.head).foreach(handleMeasurement)
      case None => log.error("Serial device not found")
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

  if (args.contains("list")) {
    Serial.listPorts.foreach(port => log.info(s"Serial port: ${port.getName}"))
  } else {
    val isTest = args.contains("test")
    ConfigFactory.load().getConfigList("devices").forEach(runFlow(isTest))
  }

  system.terminate()
}
