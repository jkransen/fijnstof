import java.io._

import org.slf4j.LoggerFactory
import purejavacomm.{CommPortIdentifier, SerialPort}

object Serial {

  val log = LoggerFactory.getLogger("Serial")

  def connect(portName: String, handleInput: InputStream => Any) {
    findPort(portName) match {
      case Some(port) => {
        log.info(s"Port found: $portName")
        val in = port.getInputStream
        handleInput(in)
      }
      case None => {
        log.error(s"Could not find port $portName")
      }
    }
  }

  def findPort(portName: String): Option[SerialPort] = {
    import java.util._
    def findPort0(ports: Enumeration[CommPortIdentifier]): Option[SerialPort] = {
      if (ports.hasMoreElements) {
        val nextPortId: CommPortIdentifier = ports.nextElement
        if (nextPortId.getName().equalsIgnoreCase(portName)) {
          Some(openPort(nextPortId))
        } else {
          findPort0(ports)
        }
      } else {
        None
      }
    }
    findPort0(CommPortIdentifier.getPortIdentifiers)
  }

  def openPort(portId: CommPortIdentifier): SerialPort = {
    val port: SerialPort = portId.open("Fijnstof", 1000).asInstanceOf[SerialPort]
    port.notifyOnDataAvailable(true)
    port.setFlowControlMode(SerialPort.FLOWCONTROL_XONXOFF_IN + SerialPort.FLOWCONTROL_XONXOFF_OUT)
    port
  }
}
