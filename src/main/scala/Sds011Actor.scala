import java.io.InputStream

import Sds011Actor.Tick
import akka.actor.{Actor, ActorRef, Props}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import Sds011Actor._

case class Pm25Measurement(id: Int, pm25: Int) {
  val pm25str = s"${pm25 / 10}.${pm25 % 10}"

  override def toString: String = s"Sds011 id=$id pm2.5=$pm25str"
}

case class Pm10Measurement(id: Int, pm10: Int) {
  val pm10str = s"${pm10 / 10}.${pm10 % 10}"

  override def toString: String = s"Sds011 id=$id pm10=$pm10str"
}

object Sds011Actor {

  case object Tick

  def props(in: InputStream, interval: Int, listeners: Seq[ActorRef])(implicit ec: ExecutionContext): Props = Props(new Sds011Actor(in, interval, listeners))

  def average(ms: List[(Pm25Measurement, Pm10Measurement)]): (Pm25Measurement, Pm10Measurement) = {
    val (pm25agg, pm10agg) = ms.foldRight((0,0))((nxt, agg) => (nxt._1.pm25 + agg._1, nxt._2.pm10 + agg._2))
    (Pm25Measurement(ms.head._1.id, pm25agg / ms.size), Pm10Measurement(ms.head._2.id, pm10agg / ms.size))
  }
}

class Sds011Actor(in: InputStream, interval: Int, listeners: Seq[ActorRef])(implicit ec: ExecutionContext) extends Actor {

  private val log = LoggerFactory.getLogger("Sds011Actor")

  log.info(s"interval: $interval")

  override def receive: Receive = receiveWithCache(List())

  def receiveWithCache(cache: List[(Pm25Measurement, Pm10Measurement)]): Receive = {
    case (pm25: Pm25Measurement, pm10: Pm10Measurement) =>
      context.become(receiveWithCache((pm25, pm10) :: cache))
    case Tick =>
      if (cache.nonEmpty) {
        log.debug("Tick")
        listeners.foreach(_ ! average(cache))
        context.become(receiveWithCache(List()))
      } else {
        log.debug("Tick, no data")
      }
      context.system.scheduler.scheduleOnce(interval seconds, self, Tick)
  }

  override def preStart(): Unit = {
    context.system.dispatcher.execute(() => new Sds011Reader().keepReading(in, self ! (_, _)))
    context.system.scheduler.scheduleOnce(0 seconds, self, Tick)
  }
}

class Sds011Reader {

  private val log = LoggerFactory.getLogger("Sds011Reader")

  type MeasurementHandler = (Pm25Measurement, Pm10Measurement) => Unit

  @tailrec
  final def keepReading(in: InputStream, handle: MeasurementHandler): Nothing = {
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
            handle(Pm25Measurement(id, pm25), Pm10Measurement(id, pm10))
          } else {
            log.trace(s"Wrong tail: $b9")
          }
        } else {
          log.trace(s"Checksum, expected: $expectedChecksum, actual: $b8")
        }
      }
    }
    keepReading(in, handle)
  }

}