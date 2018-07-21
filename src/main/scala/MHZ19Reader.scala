import java.io.{InputStream, OutputStream}

import org.slf4j.LoggerFactory

import scala.annotation.tailrec

case class MHZ19Measurement(ppm: Int) extends Measurement

object MHZ19Measurement {
  def average(ms: List[MHZ19Measurement]): MHZ19Measurement = {
    val ppmSum = ms.foldRight(0)((nxt, sum) => nxt.ppm + sum)
    MHZ19Measurement(ppmSum / ms.size)
  }
}

class MHZ19Reader extends MeasurementSource[MHZ19Measurement] {

  private val log = LoggerFactory.getLogger("MHZ19Reader")

  override def stream(in: InputStream): Stream[MHZ19Measurement] = next(in) #:: stream(in)

  @tailrec
  private def next(in: InputStream): MHZ19Measurement = {
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
        // TODO calculate checksum
        val expectedChecksum = (b2 + b3 + b4 + b5 + b6 + b7) & 0xff
        val b8 = in.read
        if (true || b8 == expectedChecksum) {
          return MHZ19Measurement(ppm)
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

  def poll(interval: Int, out: OutputStream): Nothing = {
    Future {
      pollRec(interval, out)
    }
    println("Poll interval is running")
  }
}
