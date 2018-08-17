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

  lazy val machineId: Option[String] = {
    val serialRegex = "Serial\\s*\\:\\s*0*([^0][0-9a-fA-F]+)".r
    for {
      cpuinfo <- Try(File("/proc/cpuinfo").slurp()).toOption
      firstMatch <- serialRegex.findFirstMatchIn(cpuinfo)
    } yield "fijnstof-" + firstMatch.group(1)
  }

  def runFlow(isTest: Boolean)(config: Config): Unit = {
    val uartDevice = config.getString("device")
    log.info(s"Connecting to UART (Serial) device: $uartDevice")

    val handlers = Seq(
      config.as[Option[Config]]("domoticz").map(Domoticz(_)),
      config.as[Option[Config]]("luftdaten").map(Luftdaten(_))
    ).collect { case Some(handler) => handler }

    def handleMeasurement(measurement: Measurement): Unit = {
      log.debug(s"Measurement: ${measurement.toString}")
      handlers.foreach(_.handle(measurement))
    }

    val sourceType = config.getString("type")
    val interval = config.as[Option[Int]]("interval").getOrElse(90)
    val batchSize = config.as[Option[Int]]("batchSize").getOrElse(interval)

    if (sourceType.equalsIgnoreCase("sds011")) {
      val source = MeasurementSource(sourceType)

      Serial.connect(uartDevice) match {
        case Some(is) if isTest => source.stream(is).headOption.foreach(handleMeasurement)
        case Some(is) => source.stream(is).sliding(batchSize, interval).map(_.toList).map(Sds011Measurement.average).foreach(handleMeasurement)
        case None => log.error("Serial device not found")
      }
    } else if (sourceType.equalsIgnoreCase("mhz19")) {
      val source = new MHZ19Reader

      Serial.connect(uartDevice) match {
        case Some(is) if isTest => source.stream(is).headOption.foreach(handleMeasurement)
        case Some(is) => source.stream(is).sliding(batchSize, interval).map(_.toList).map(CO2Measurement.average).foreach(handleMeasurement)
        case None => log.error("Serial device not found")
      }
    }
  }

  log.info(s"Starting fijnstof, machine id: $machineId")

  if (args.contains("list")) {
    Serial.listPorts.foreach(port => log.info(s"Serial port: ${port.getName}"))
  } else {
    val isTest = args.contains("test")
    ConfigFactory.load().getConfigList("devices").forEach(runFlow(isTest))
  }

  system.terminate()
}
