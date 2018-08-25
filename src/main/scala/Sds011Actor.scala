import java.io.InputStream

import Sds011Actor.Tick
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.duration._

case class Pm25Measurement(id: Int, pm25: Int) extends Measurement {
  val pm25str = s"${pm25 / 10}.${pm25 % 10}"

  override def toString: String = s"Sds011 id=$id pm2.5=$pm25str"
}

case class Pm10Measurement(id: Int, pm10: Int) extends Measurement {
  val pm10str = s"${pm10 / 10}.${pm10 % 10}"

  override def toString: String = s"Sds011 id=$id pm10=$pm10str"
}

object Sds011Actor {

  case object Tick


  def props(in: InputStream)(particulateListener: ActorRef): Props = Props(new Sds011Actor(in, particulateListener))
}

class Sds011Actor(in: InputStream, particulateListener: ActorRef) extends Actor {

  private val log = LoggerFactory.getLogger("Sds011Actor")

  override def receive: Receive = {
    case Tick =>
      val (pm25, pm10) = readNext(in)
      particulateListener ! (pm25, pm10)
      context.system.scheduler.scheduleOnce(1 second, self, Tick)
  }

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(0 seconds, self, Tick)
  }

  @tailrec
  private def readNext(in: InputStream): (Pm25Measurement, Pm10Measurement) = {
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
        val id = (b7 * 256) + b6
        val expectedChecksum = (b2 + b3 + b4 + b5 + b6 + b7) & 0xff
        val b8 = in.read
        if (b8 == expectedChecksum) {
          val b9 = in.read
          if (b9 == 0xab) {
            return (Pm25Measurement(id, pm25), Pm10Measurement(id, pm10))
          } else {
            log.trace(s"Wrong tail: $b9")
          }
        } else {
          log.trace(s"Checksum, expected: $expectedChecksum, actual: $b8")
        }
      }
    }
    readNext(in)
  }

}
