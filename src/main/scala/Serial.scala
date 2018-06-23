
import java.io.InputStream

import org.slf4j.LoggerFactory
import purejavacomm.{CommPortIdentifier, SerialPort}

import scala.collection.JavaConverters._

object Serial {

  private val log = LoggerFactory.getLogger("Serial")

  def connect(portName: String): Option[InputStream] = {
    findPort(portName) map (_.getInputStream)
  }

  def findPort(portName: String): Option[SerialPort] = {
    val portOption = listPorts.find(_.getName.equalsIgnoreCase(portName))
    portOption match {
      case Some(port) => log.info(s"Serial port found: $portName")
      case None => log.error(s"Serial port $portName not found")
    }
    portOption map openPort
  }

  def openPort(portId: CommPortIdentifier): SerialPort = {
    val port: SerialPort = portId.open("Fijnstof", 1000).asInstanceOf[SerialPort]
    port.setFlowControlMode(SerialPort.FLOWCONTROL_XONXOFF_IN + SerialPort.FLOWCONTROL_XONXOFF_OUT)
    port
  }

  def listPorts = CommPortIdentifier.getPortIdentifiers.asScala.toList
}
