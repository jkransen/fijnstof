package nl.kransen.fijnstof

import java.io.IOException
import java.util.concurrent.{Executors, ScheduledThreadPoolExecutor}

import com.typesafe.config.{Config, ConfigFactory}
import fs2.Stream
import net.ceedubs.ficus.Ficus._
import nl.kransen.fijnstof.Main.AppTypes.MeasurementTarget
import nl.kransen.fijnstof.SdsStateMachine.SdsMeasurement
import org.slf4j.LoggerFactory
import purejavacomm.SerialPort

import scala.util.Try
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console._

import scala.concurrent.ExecutionContext
import scala.io.Source.fromFile

object Main extends App {

  object AppTypes {
    type AppEnv = Clock with Blocking with Console
    type Measurement
    type MeasurementSource
    type MeasurementTarget
  }

  private val log = LoggerFactory.getLogger("Main")

  implicit val ex: ScheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  lazy val machineId: Option[String] = {
    val serialRegex = "Serial\\s*\\:\\s*0*([^0][0-9a-fA-F]+)".r
    for {
      cpuinfo <- Try(fromFile("/proc/cpuinfo").mkString).toOption
      firstMatch <- serialRegex.findFirstMatchIn(cpuinfo)
    } yield "fijnstof-" + firstMatch.group(1)
  }

  def runFlow(isTest: Boolean)(config: Config): Unit = {
    val uartDevice = config.getString("device")
    log.info(s"Connecting to UART (Serial) device: $uartDevice")

    val targets: Seq[MeasurementTarget] = Seq(
      config.as[Option[Config]]("domoticz").map(config => Domoticz(config)),
      config.as[Option[Config]]("luftdaten").map(config => Luftdaten(config))
    ).collect { case Some(target) => target }

    val sourceType = config.getString("type")
    val interval = config.as[Option[Int]]("interval").getOrElse(90)
    // val batchSize = config.as[Option[Int]]("batchSize").getOrElse(interval)

    for {
      port <- Serial.findPort(uartDevice)
      source <- getSource(port)
    } yield source

    def getSource(port: SerialPort) = sourceType.toLowerCase match {
      case "sds011" => Sds011(port, interval)
      case "mhz19" => Mhz19(port, interval)
      case _ => Task.fail(new IOException(s"Source type $sourceType unknown"))
    }
  }

  def myAppLogic(args: List[String]): Task[Unit] = {
    log.info(s"Starting fijnstof, machine id: $machineId")
    if (args.contains("list")) {
      ZIO.effect(Serial.listPorts.foreach(port => log.info(s"Serial port: ${port.getName}")))
    } else {
      val isTest = args.contains("test")
      ZIO.effect(ConfigFactory.load().getConfigList("devices"))
        .flatMap(  (runFlow(isTest)))
    }
  }

  def run(args: List[String]) =
    myAppLogic(args).fold(_ => 1, _ => 0)
}
