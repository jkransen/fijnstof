import java.io.InputStream
import org.slf4j.LoggerFactory
import scala.annotation.tailrec

case class Sds011Measurement(id: Int, pm25: Int, pm10: Int) extends Measurement {
  val pm10str = s"${pm10 / 10}.${pm10 % 10}"
  val pm25str = s"${pm25 / 10}.${pm25 % 10}"

  override def toString: String = s"Sds011 pm2.5=$pm25str pm10=$pm10str"
}

object Sds011Measurement {
  def average(ms: List[Sds011Measurement]): Sds011Measurement = {
    val (pm25agg, pm10agg) = ms.foldRight((0,0))((nxt, agg) => (nxt.pm25 + agg._1, nxt.pm10 + agg._2))
    Sds011Measurement(ms.head.id, pm25agg / ms.size, pm10agg / ms.size)
  }
}

class Sds011Reader extends MeasurementSource[Sds011Measurement] {

  private val log = LoggerFactory.getLogger("Sds011Reader")

  override def stream(in: InputStream): Stream[Sds011Measurement] = next(in) #:: stream(in)

  @tailrec
  private def next(in: InputStream): Sds011Measurement = {
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
            log.trace(s"Wrong tail: $b9")
          }
        } else {
          log.trace(s"Checksum, expected: $expectedChecksum, actual: $b8")
        }
      }
    }
    next(in)
  }
}
