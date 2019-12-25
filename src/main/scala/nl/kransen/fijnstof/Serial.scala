package nl.kransen.fijnstof

import java.io.{IOException, InputStream, OutputStream, PipedInputStream, PipedOutputStream}

import cats.effect.IO
import purejavacomm.{CommPortIdentifier, SerialPort, SerialPortEventListener}

import scala.collection.JavaConverters._

object Serial {

  def findPort(portName: String): IO[SerialPort] = {
    if ("TEST_SDS011".equals(portName)) {
      IO(new TestSdsSerialPort())
    } else if ("TEST_MHZ19".equals(portName)) {
      IO(new TestMhzSerialPort())
    } else {
      for {
        ports <- listPorts
        port  <- IO(ports.find(_.getName.equalsIgnoreCase(portName))
                   .getOrElse(throw new IOException(s"Port not found: $portName")))
      } yield openPort(port)
    }
  }

  private def openPort(portId: CommPortIdentifier): SerialPort = {
    val port: SerialPort = portId.open("Fijnstof", 1000).asInstanceOf[SerialPort]
    port.setFlowControlMode(SerialPort.FLOWCONTROL_XONXOFF_IN + SerialPort.FLOWCONTROL_XONXOFF_OUT)
    port
  }

  def listPorts: IO[List[CommPortIdentifier]] = IO(CommPortIdentifier.getPortIdentifiers.asScala.toList)
}

class TestSdsSerialPort extends SerialPortAdapter {
  import java.util.concurrent._

  private val ex = new ScheduledThreadPoolExecutor(1)

  override def getInputStream: InputStream = {
    val validPayload: Array[Int] = Array(0xaa, 0xc0, 1, 2, 3, 4, 5, 6, 21, 0xab)
    val pos = new PipedOutputStream()
    val task = new Runnable {
      def run(): Unit = validPayload.foreach(pos.write)
    }
    ex.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS)
    new PipedInputStream(pos)
  }
}

class TestMhzSerialPort extends SerialPortAdapter {
  import java.util.concurrent._

  private val posIn = new PipedOutputStream()
  private val posOut = new PipedOutputStream()

  private val is = new PipedInputStream(posIn)
  private val validPayload: Array[Int] = Array(0xff, 0x86, 2, 3, 0, 0, 0, 0, 3, 0xab)

  private val expectTask = new Runnable {
    def run(): Unit = {
      while (true) {
        try {
          val nextByte = is.read()
          if (nextByte == 0x79) {
            validPayload.foreach(posOut.write)
          }
        } catch {
          case _: IOException =>
            Thread.currentThread().interrupt()
        }
      }
    }
  }

  private val ex = Executors.newSingleThreadExecutor()
  ex.execute(expectTask)

  override def getOutputStream: OutputStream = posIn

  override def getInputStream: InputStream = new PipedInputStream(posOut)
}

class LiteralSerialPort(literalOutput: String) extends SerialPortAdapter {

  override def getInputStream: InputStream = {
    val pos = new PipedOutputStream()
    val pis = new PipedInputStream(pos)
    literalOutput.split("\\s").foreach(s => pos.write(Integer.parseInt(s, 16)))
    pis
  }
}

class SerialPortAdapter extends SerialPort {

  def getInputStream: InputStream = ???

  def getOutputStream: OutputStream = ???

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

  def getReceiveFramingByte = ???

  def getReceiveThreshold = ???

  def getReceiveTimeout = ???

  def isReceiveFramingEnabled = ???

  def isReceiveThresholdEnabled = ???

  def isReceiveTimeoutEnabled = ???

  def setInputBufferSize(size: Int) = ???

  def setOutputBufferSize(size: Int) = ???
}

class TestSerialPort extends SerialPort {

  import java.util.concurrent._

  val ex = new ScheduledThreadPoolExecutor(1)

  def getInputStream: InputStream = {
    val validPayload: Array[Byte] = Array(170, 192, 1, 2, 3, 4, 5, 6, 21, 171).map(_.toByte)
    val pos = new PipedOutputStream()
    val task = new Runnable {
      def run(): Unit = pos.write(validPayload)
    }
    val f = ex.scheduleAtFixedRate(task, 1, 1, TimeUnit.SECONDS)
    // f.cancel(false)
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