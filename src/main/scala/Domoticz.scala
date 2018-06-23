import com.typesafe.config.Config
import org.slf4j.LoggerFactory

class Domoticz {

  private val log = LoggerFactory.getLogger("Domoticz")

  def apply(config: Config): Domoticz = {
    val host = config.getString("host")
    val port = config.getString("port")
    val pm25Idx = config.getString("pm25Idx")
    val pm10Idx = config.getString("pm10Idx")
    log.info(s"Domoticz host: $host, port: $port")
    log.info(s"PM2.5 IDX: $pm25Idx, PM10 IDX: $pm10Idx")

  }
}
