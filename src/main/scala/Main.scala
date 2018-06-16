import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import scalaj.http.{Http, HttpOptions}

import scala.reflect.io.File

object Main extends App {



  val log = LoggerFactory.getLogger("Main")
  val cpuinfo = File("/proc/cpuinfo").slurp()
  val serialRegex = "Serial\\s*\\:\\s*0*(\\d+)".r
  val id = serialRegex.findFirstMatchIn(cpuinfo).map(_.group(1)).map("fijnstof-" + _)
  log.info(s"id: $id")

  private val config = ConfigFactory.load()
  val host = config.getString("domoticz.host")
  val port = config.getString("domoticz.port")
  val pm25Idx = config.getString("domoticz.pm25Idx")
  val pm10Idx = config.getString("domoticz.pm10Idx")
  val uartDevice = config.getString("uart.device")

  log.info(s"UART (Serial) device: $uartDevice")
  log.info(s"PM2.5 IDX: $pm25Idx, PM10 IDX: $pm10Idx")
  log.info(s"Domoticz host: $host, port: $port")

  var count = 0

  Serial.connect(uartDevice, in => Sds021Listener.listen(in, report => {
    if (count == 0) {
      handleReport(report)
      count = 30
    }
    count = count - 1
  }))

  def handleReport(report: Report): Unit = {
    println(report)
    val get1 = s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm25Idx&nvalue=&svalue=${report.pm25 / 10}.${report.pm25 % 10}"
    println(get1)
    val response1 = io.Source.fromURL(get1).mkString
    println(response1)
    val response2 = io.Source.fromURL(s"http://$host:$port/json.htm?type=command&param=udevice&idx=$pm10Idx&nvalue=&svalue=${report.pm10 / 10}.${report.pm10 % 10}").mkString
    println(response2)
    sendLuftdaten(report)
  }

  def sendLuftdaten(report: Report): Unit = {
    val postUrl = "https://api.luftdaten.info/v1/push-sensor-data/"
    val response = Http(postUrl).headers(Seq("X-PIN" -> "1", "X-Sensor" -> s"fijnstof-${report.id}")).postForm(Seq("P1" -> report.pm10.toString, "P2" -> report.pm25.toString)).option(HttpOptions.allowUnsafeSSL).asString
    log.debug(s"Luftdaten: $response")
  }
}