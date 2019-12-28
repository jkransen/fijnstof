package nl.kransen.fijnstof

import java.io.IOException
import java.util.concurrent.{Executors, ScheduledThreadPoolExecutor}

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import fs2.Stream
import net.ceedubs.ficus.Ficus._
import org.slf4j.LoggerFactory
import purejavacomm.SerialPort
import zio.blocking.Blocking
import zio.console.Console
import zio.{App, RIO, UIO, ZIO, console}
import zio.interop.catz._

import scala.util.Try
import scala.concurrent.ExecutionContext
import scala.io.Source.fromFile
import scala.collection.JavaConverters._

object Main extends App {

  import AppTypes._

  object AppTypes {
    type AppEnv = Blocking with Console
    type AppTask[A] = RIO[AppEnv, A]
    type MeasurementSource

    trait Measurement

    trait MeasurementTarget {
      def save(measurement: Measurement): AppTask[Unit]
    }
  }

  private val log = LoggerFactory.getLogger("Main")

  implicit val ex: ScheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  lazy val machineId: Option[String] = {
    val serialRegex = "Serial\\s*\\:\\s*0*([^0][0-9a-fA-F]+)".r
    for {
      cpuinfo    <- Try(fromFile("/proc/cpuinfo").mkString).toOption
      firstMatch <- serialRegex.findFirstMatchIn(cpuinfo)
    } yield "fijnstof-" + firstMatch.group(1)
  }

  def runStream(isTest: Boolean)(config: Config): AppTask[Unit] = {
    val uartDevice = config.getString("device")
    val sourceType = config.getString("type")
    val interval = if (isTest) 1 else config.as[Option[Int]]("interval").getOrElse(90)
    log.info(s"Connecting to UART (Serial) device: $uartDevice type=$sourceType interval=$interval")

    def getSource(port: SerialPort): Stream[AppTask, Measurement] = sourceType.toLowerCase match {
      case "sds011" => Sds011(port, interval)
      case "mhz19" => Mhz19(port, interval)
//      case _ => Stream.raiseError[AppTask](new IOException(s"Source type $sourceType unknown"))
    }

    val targets: Seq[MeasurementTarget] = Seq(
      config.as[Option[Config]]("domoticz").map(config => Domoticz(config)),
      config.as[Option[Config]]("luftdaten").map(config => Luftdaten(config))
    ).collect { case Some(target) => target }

    val infiniteSource: Stream[AppTask, Measurement] = for {
      port     <- Stream.eval(Serial.findPort(uartDevice))
      source   <- getSource(port)
    } yield source

    val source =  if (isTest) infiniteSource.take(1) else infiniteSource
    source.evalMap(meas => targets.toList.parTraverse(t => t.save(meas)))
        .compile.drain
  }

  val listFlow: AppTask[Int] = {
    for {
      _       <- console.putStrLn("Starting")
      _       <- console.putStrLn(s"Starting fijnstof, listing serial ports, machine id: $machineId")
      ports   <- Serial.listPorts
      _       <- ports.parTraverse(port => console.putStrLn(s"Serial port: ${port.getName}"))
    } yield 0
  }

  def runFlow(isTest: Boolean): AppTask[Int] = {
    for {
      _       <- console.putStrLn("Starting")
      _       <- console.putStrLn(s"Starting fijnstof, test mode: $isTest, machine id: $machineId")
      configs <- ZIO(ConfigFactory.load().getConfigList("devices").asScala.toList)
      _       <- configs.parTraverse(runStream(isTest))
    } yield 0
  }

  override def run(args: List[String]): ZIO[AppEnv, Nothing, Int] = {
    val task = if (args.contains("list")) {
      listFlow
    } else {
      val isTest = args.contains("test")
      runFlow(isTest)
    }
    task.fold(_ => 1, _ => 0)
  }
}
