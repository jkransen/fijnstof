package nl.kransen.fijnstof

import java.io.{InputStream, PipedInputStream, PipedOutputStream}

import javax.xml.bind.DatatypeConverter
import org.slf4j.LoggerFactory
import purejavacomm.{CommPortIdentifier, SerialPort, SerialPortEventListener}

import scala.collection.JavaConverters._

object Serial {

  private val log = LoggerFactory.getLogger("Serial")

  def findPort(portName: String): Option[SerialPort] = {
    if ("TEST".equals(portName)) {
      Some(new TestSerialPort())
    } else {
      val portOption = listPorts.find(_.getName.equalsIgnoreCase(portName))
      portOption match {
        case Some(port) => log.info(s"Serial port found: $portName")
        case None => log.error(s"Serial port $portName not found")
      }
      portOption map openPort
    }
  }

  private def openPort(portId: CommPortIdentifier): SerialPort = {
    val port: SerialPort = portId.open("Fijnstof", 1000).asInstanceOf[SerialPort]
    port.setFlowControlMode(SerialPort.FLOWCONTROL_XONXOFF_IN + SerialPort.FLOWCONTROL_XONXOFF_OUT)
    port
  }

  def listPorts: List[CommPortIdentifier] = CommPortIdentifier.getPortIdentifiers.asScala.toList
}

class TestSerialPort extends SerialPort {

  import java.util.concurrent._

  val ex = new ScheduledThreadPoolExecutor(1)

  def getInputStream: InputStream = {
    //                                    aa   c0 01 02 03 04 05 06  15   ab
    val validPayload: Array[Int] = Array(170, 192, 1, 2, 3, 4, 5, 6, 21, 171)
    val pos = new PipedOutputStream()
    val task = new Runnable {
      def run(): Unit = validPayload.foreach(pos.write)
    }
    val f = ex.scheduleAtFixedRate(task, 1, 1, TimeUnit.SECONDS)
    new PipedInputStream(pos)
  }

  def addEventListener(listener: SerialPortEventListener) = ???

  def getBaudRate = ???

  def getDataBits = ???

  def getFlowControlMode = ???

  def getParity = ???

  def getStopBits = ???

  def isCD = ???

  def isCTS = ???

  def isDSR = ???

  def isDTR = ???

  def isRI = ???

  def isRTS = ???

  def notifyOnBreakInterrupt(enable: Boolean) = ???

  def notifyOnCarrierDetect(enable: Boolean) = ???

  def notifyOnCTS(enable: Boolean) = ???

  def notifyOnDataAvailable(enable: Boolean) = ???

  def notifyOnDSR(enable: Boolean) = ???

  def notifyOnFramingError(enable: Boolean) = ???

  def notifyOnOutputEmpty(enable: Boolean) = ???

  def notifyOnOverrunError(enable: Boolean) = ???

  def notifyOnParityError(enable: Boolean) = ???

  def notifyOnRingIndicator(enable: Boolean) = ???

  def removeEventListener() = ???

  def sendBreak(duration: Int) = ???

  def setDTR(state: Boolean) = ???

  def setFlowControlMode(flowcontrol: Int) = ???

  def setRTS(state: Boolean) = ???

  def setSerialPortParams(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int) = ???

  def disableReceiveFraming() = ???

  def disableReceiveThreshold() = ???

  def disableReceiveTimeout() = ???

  def enableReceiveFraming(framingByte: Int) = ???

  def enableReceiveThreshold(threshold: Int) = ???

  def enableReceiveTimeout(rcvTimeout: Int) = ???

  def getInputBufferSize = ???

  def getOutputBufferSize = ???

  def getOutputStream = ???

  def getReceiveFramingByte = ???

  def getReceiveThreshold = ???

  def getReceiveTimeout = ???

  def isReceiveFramingEnabled = ???

  def isReceiveThresholdEnabled = ???

  def isReceiveTimeoutEnabled = ???

  def setInputBufferSize(size: Int) = ???

  def setOutputBufferSize(size: Int) = ???
}
