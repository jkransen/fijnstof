package nl.kransen.fijnstof

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.reflect.io.File
import scala.util.Try

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

    val targets: Seq[ActorRef] = Seq(
      config.as[Option[Config]]("domoticz").map(config => Domoticz.props(config)).map(system.actorOf(_)),
      config.as[Option[Config]]("luftdaten").map(config => Luftdaten.props(config)).map(system.actorOf(_))
    ).collect { case Some(target) => target }

    val sourceType = config.getString("type")
    // val interval = config.as[Option[Int]]("interval").getOrElse(90)
    // val batchSize = config.as[Option[Int]]("batchSize").getOrElse(interval)

    Serial.findPort(uartDevice) match {
      case Some(port) =>
        val source: Option[Props] = if (sourceType.equalsIgnoreCase("sds011")) {
          Some(Sds011Actor.props(port.getInputStream, config.as[Option[Int]]("interval").getOrElse(90), targets))
        } else if (sourceType.equalsIgnoreCase("mhz19")) {
          Some(Mhz19Actor.props(port.getInputStream, port.getOutputStream, targets))
        } else {
          log.error(s"Source type $sourceType unknown")
          None
        }
        source.foreach(system.actorOf(_, s"${sourceType}_source"))
      case None => log.error("Serial device not found")
    }
  }

  log.info(s"Starting fijnstof, machine id: $machineId")

  if (args.contains("list")) {
    Serial.listPorts.foreach(port => log.info(s"Serial port: ${port.getName}"))
  } else {
    val isTest = args.contains("test")
    ConfigFactory.load().getConfigList("devices").forEach(runFlow(isTest))
  }

  // system.terminate()
}
