package nl.kransen.fijnstof

import java.io.{ByteArrayInputStream, InputStream}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import javax.xml.bind.DatatypeConverter
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global

class Sds011ActorSpec extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "An Sds011 actor" must {

    "send precisely one measurement" in {
      val validPayload = "aa c0 01 02 03 04 05 06 15 ab"

      val probe1 = TestProbe()
//      val actor = system.actorOf(Sds011Actor.props(validPayload, 1, Seq(testActor)))

      val reading25 = 2 * 256 + 1
      val reading10 = 4 * 256 + 3
      val id = 6 * 256 + 5

      val expectedMeasurement = (Pm25Measurement(id, reading25), Pm10Measurement(id, reading10))

      expectMsg(expectedMeasurement)
      expectNoMessage()
    }

    "skip 1 measurement on Checksum error" in {

      val invalidReading = "aa c0 01 02 03 04 05 06 14 ab"
      val validReading = "aa c0 01 00 01 00 01 00 03 ab"

//      val actor = system.actorOf(Sds011Actor.props(invalidReading + validReading, 1, Seq(testActor)))

      val expectedMeasurement = (Pm25Measurement(1, 1), Pm10Measurement(1, 1))

      expectMsg(expectedMeasurement)
      expectNoMessage()
    }

    "skip 1 measurement on Bad footer" in {

      val invalidReading = "aa c0 01 02 03 04 05 06 14 ac"
      val validReading = "aa c0 01 00 01 00 01 00 03 ab"

//      val actor = system.actorOf(Sds011Actor.props(invalidReading + validReading, 1, Seq(testActor)))

      val expectedMeasurement = (Pm25Measurement(1, 1), Pm10Measurement(1, 1))

      expectMsg(expectedMeasurement)
      expectNoMessage()
    }

    "recover when Measurement matches header" in {

      val validPayload = "aa c0 01 02 03 aa c0 01 02 03 04 05 06 15 ab aa c0 01 02 03 04 05 06 15 ab"
      //                  ^- measurement ^- actual start of payload    ^- recover from here

//      val actor = system.actorOf(Sds011Actor.props(validPayload, 1, Seq(testActor)))

      val reading25 = 2 * 256 + 1
      val reading10 = 4 * 256 + 3
      val id = 6 * 256 + 5

      val expectedMeasurement = (Pm25Measurement(id, reading25), Pm10Measurement(id, reading10))

      expectMsg(expectedMeasurement)
      expectNoMessage()
    }
  }

  "SDS011 Measurement " must {

    "allow measurements smaller than 1" in {
      val reading25 = 9
      val reading10 = 8
      val id = 7

      val actualPm25Measurement = Pm25Measurement(id, reading25)
      actualPm25Measurement.pm25str should equal("0.9")
      val actualPm10Measurement = Pm10Measurement(id, reading10)
      actualPm10Measurement.pm10str should equal("0.8")
    }

    "allow measurements greater than 1" in {
      val reading25 = 1024
      val reading10 = 512
      val id = 7

      val actualPm25Measurement = Pm25Measurement(id, reading25)
      actualPm25Measurement.pm25str should equal("102.4")
      val actualPm10Measurement = Pm10Measurement(id, reading10)
      actualPm10Measurement.pm10str should equal("51.2")
    }

    "fold to correct average value" in {
        val ms = (Pm25Measurement(1, 25), Pm10Measurement(1, 125)) :: (Pm25Measurement(1, 31), Pm10Measurement(1, 131)) :: (Pm25Measurement(1, 35), Pm10Measurement(1, 135)) :: Nil
        val avg = Sds011Protocol.average(ms)
        avg._1.pm25 should equal(30)
        avg._2.pm10 should equal(130)
        avg._1.id should equal(1)
        avg._2.id should equal(1)
      }

  }

  implicit def hexToInputStream(str: String): InputStream = new ByteArrayInputStream(DatatypeConverter.parseHexBinary(str.replaceAll("\\s", "")))
}

