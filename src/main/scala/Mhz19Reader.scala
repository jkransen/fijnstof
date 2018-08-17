import java.io.{InputStream, OutputStream}

import org.slf4j.LoggerFactory

import scala.annotation.tailrec

case class CO2Measurement(ppm: Int) extends Measurement

object CO2Measurement {
  def average(ms: List[CO2Measurement]): CO2Measurement = {
    val ppmSum = ms.foldRight(0)((nxt, sum) => nxt.ppm + sum)
    CO2Measurement(ppmSum / ms.size)
  }
}

class MHZ19Reader extends MeasurementSource[CO2Measurement] {

  private val log = LoggerFactory.getLogger("MHZ19Reader")

  override def stream(in: InputStream): Stream[CO2Measurement] = next(in) #:: stream(in)

  @tailrec
  private def next(in: InputStream): CO2Measurement = {
    val b0: Int = in.read
    if (b0 == 0xff) {
      val b1 = in.read
      if (b1 == 0x86) {
        val b2 = in.read
        val b3 = in.read
        val ppm = (b2 * 256) + b3
        val b4 = in.read
        val b5 = in.read
        val b6 = in.read
        val b7 = in.read
        val expectedChecksum = 0xff - b1 - b2 - b3 - b4 - b5 - b6 - b7 + 1
        val b8 = in.read
        if (b8 == expectedChecksum) {
          return CO2Measurement(ppm)
        } else {
          log.trace(s"Checksum, expected: $expectedChecksum, actual: $b8")
        }
      }
    }
    next(in)
  }
}

import scala.annotation.tailrec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Poller {

  private val command = Array(0xff, 0x01, 0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79).map(_.toByte)

  @tailrec
  private def pollRec(interval: Int, out: OutputStream): Nothing = {
    out.write(command)
    Thread.sleep(interval * 1000)
    pollRec(interval, out)
  }

  def poll(interval: Int, out: OutputStream): Future[Nothing] = {
    Future {
      pollRec(interval, out)
    }
  }
}
