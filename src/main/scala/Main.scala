import com.typesafe.config.ConfigFactory

import scala.reflect.io.File

object Main extends App {

  val cpuinfo = File("/proc/cpuinfo").slurp()
  val serialRegex = "Serial\\s*\\:\\s*(\\d+)".r
  val id = serialRegex.findFirstMatchIn(cpuinfo).map(_.group(1)).map("raspi-" + _)
  println(s"id: $id")

  private val config = ConfigFactory.load()
  val host = config.getString("domoticz.host")
  val port = config.getString("domoticz.port")
  val pm25Idx = config.getString("domoticz.pm25Idx")
  val pm10Idx = config.getString("domoticz.pm25Idx")
  val uartDevice = config.getString("uart.device")

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
  }
}