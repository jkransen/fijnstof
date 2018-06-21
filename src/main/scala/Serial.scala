
import java.io.InputStream

import akka.stream.IOResult
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import purejavacomm.{CommPortIdentifier, SerialPort}

import scala.annotation.tailrec
import scala.concurrent.Future

object Serial {

  private val log = LoggerFactory.getLogger("Serial")

  def connect(portName: String): Option[InputStream] = {
    findPort(portName) map (_.getInputStream)
  }

  def findPort(portName: String): Option[SerialPort] = {
    @tailrec
    def findPort0(ports: java.util.Enumeration[CommPortIdentifier]): Option[SerialPort] = {
      if (ports.hasMoreElements) {
        val nextPortId: CommPortIdentifier = ports.nextElement
        if (nextPortId.getName.equalsIgnoreCase(portName)) {
          log.info(s"Serial port found: $portName")
          Some(openPort(nextPortId))
        } else {
          findPort0(ports)
        }
      } else {
        log.info(s"Serial port $portName not found")
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
