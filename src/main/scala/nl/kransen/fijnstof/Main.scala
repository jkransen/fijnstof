package nl.kransen.fijnstof

import java.io.IOException
import java.util.concurrent.{Executors, ScheduledThreadPoolExecutor}

import cats._
import cats.effect._
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import fs2.Stream
import net.ceedubs.ficus.Ficus._
import nl.kransen.fijnstof.SdsStateMachine.SdsMeasurement
import org.slf4j.LoggerFactory
import purejavacomm.SerialPort

import scala.util.Try
import scala.concurrent.ExecutionContext
import scala.io.Source.fromFile
import scala.collection.JavaConverters._

object Main extends IOApp {

  import AppTypes._

  object AppTypes {
    trait Measurement

    type MeasurementSource

    trait MeasurementTarget {
      def save(measurement: Measurement)
    }
  }

  private val log = LoggerFactory.getLogger("Main")

  implicit val ex: ScheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  lazy val machineId: Option[String] = {
    val serialRegex = "Serial\\s*\\:\\s*0*([^0][0-9a-fA-F]+)".r
    for {
      cpuinfo <- Try(fromFile("/proc/cpuinfo").mkString).toOption
      firstMatch <- serialRegex.findFirstMatchIn(cpuinfo)
    } yield "fijnstof-" + firstMatch.group(1)
  }

  def runFlow(isTest: Boolean)(config: Config): IO[Unit] = {
    val uartDevice = config.getString("device")
    val sourceType = config.getString("type")
    val interval = config.as[Option[Int]]("interval").getOrElse(90)
    val batchSize = config.as[Option[Int]]("batchSize").getOrElse(interval)
    log.info(s"Connecting to UART (Serial) device: $uartDevice type=$sourceType interval=$interval")

    def getSource(port: SerialPort): Stream[IO, SdsMeasurement] = sourceType.toLowerCase match {
      case "sds011" => Sds011(port, interval)
      case "mhz19" => Mhz19(port, interval)
      // case _ => Stream.raiseError(new IOException(s"Source type $sourceType unknown"))
    }

    val targets: Seq[MeasurementTarget] = Seq(
      config.as[Option[Config]]("domoticz").map(config => Domoticz(config)),
      config.as[Option[Config]]("luftdaten").map(config => Luftdaten(config))
    ).collect { case Some(target) => target }

    val source: Stream[IO, SdsMeasurement] = for {
      port <- Stream.eval(Serial.findPort(uartDevice))
      source <- getSource(port)
    } yield source

    source.compile.drain // .scan(meas => targets.foreach(target => target.save(meas)))
  }

  override def run(args: List[String]): IO[ExitCode] = {
    log.info(s"Starting fijnstof, machine id: $machineId")
    if (args.contains("list")) {
      for {
        ports <- Serial.listPorts
        _ <- IO(ports.traverse(port => IO(log.info(s"Serial port: ${port.getName}"))))
      } yield ExitCode.Success
    } else {
      val isTest = args.contains("test")
      for {
        configs <- IO(ConfigFactory.load().getConfigList("devices").asScala.toList)
        _ <- IO(configs.traverse(runFlow(isTest)))
      } yield ExitCode.Success
    }
  }
}
