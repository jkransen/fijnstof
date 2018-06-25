import java.io.InputStream

import org.slf4j.LoggerFactory

import scala.annotation.tailrec

case class Sds011Measurement(id: Int, pm25: Int, pm10: Int) extends Measurement {
  val pm10str = s"${pm10 / 10}.${pm10 % 10}"
  val pm25str = s"${pm25 / 10}.${pm25 % 10}"
}

object Sds011Reader extends MeasurementSource[Sds011Measurement] {

  private val log = LoggerFactory.getLogger("Sds011Reader")

  override def stream(in: InputStream): Stream[Sds011Measurement] = next(in) #:: stream(in)

  @tailrec
  private def next(in: InputStream): Sds011Measurement = {
    log.trace("Reading serial input")
    val b0: Int = in.read
    if (b0 == 0xaa) {
      val b1 = in.read
      if (b1 == 0xc0) {
        val b2 = in.read
        val b3 = in.read
        val pm25 = (b3 * 256) + b2
        val b4 = in.read
        val b5 = in.read
        val pm10 = (b5 * 256) + b4
        val b6 = in.read
        val b7 = in.read
        val id = (b6 * 256) + b7
        val expectedChecksum = (b2 + b3 + b4 + b5 + b6 + b7) & 0xff
        val b8 = in.read
        if (b8 == expectedChecksum) {
          val b9 = in.read
          if (b9 == 0xab) {
            return Sds011Measurement(id, pm25, pm10)
          } else {
            log.error(s"Wrong tail: $b9")
          }
        } else {
          log.error(s"Checksum, expected: $expectedChecksum, actual: $b8")
        }
      }
    }
    next(in)
  }
}
