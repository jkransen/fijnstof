package nl.kransen.fijnstof

import cats.effect.{ContextShift, IO}
import nl.kransen.fijnstof.Sds011.SdsMeasurement
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class Sds011Spec extends WordSpecLike with Matchers with BeforeAndAfterAll {

  implicit val cs: ContextShift[IO] = Main.cs

  "Sds011" must {

    "send precisely one measurement" in {
      val validPayload = "aa c0 01 02 03 04 05 06 15 ab"

      val reading25 = 2 * 256 + 1
      val reading10 = 4 * 256 + 3
      val id = 6 * 256 + 5

      val expectedMeasurement = SdsMeasurement(id, reading25, reading10)
      val actualMeasurement = Sds011(new LiteralSerialPort(validPayload), 1).take(1).compile.toVector.unsafeRunSync().head
      actualMeasurement shouldEqual expectedMeasurement
    }

    "skip 1 measurement on Checksum error" in {
      val invalidReading = "aa c0 01 02 03 04 05 06 14 ab "
      val validReading = "aa c0 01 00 01 00 01 00 03 ab"

      val expectedMeasurement = SdsMeasurement(1, 1, 1)
      val actualMeasurement = Sds011(new LiteralSerialPort(invalidReading + validReading), 1).take(1).compile.toVector.unsafeRunSync().head
      actualMeasurement shouldEqual expectedMeasurement
    }

    "skip 1 measurement on Bad footer" in {
      val invalidReading = "aa c0 01 02 03 04 05 06 14 ac"
      val validReading = "aa c0 01 00 01 00 01 00 03 ab"

      val expectedMeasurement = SdsMeasurement(1, 1, 1)
      val actualMeasurement = Sds011(new LiteralSerialPort(invalidReading + validReading), 1).take(1).compile.toVector.unsafeRunSync().head
      actualMeasurement shouldEqual expectedMeasurement
    }

    "recover when Measurement matches header" in {
      val validPayload = "aa c0 01 02 03 aa c0 01 02 03 04 05 06 15 ab aa c0 01 02 03 04 05 06 15 ab"
      //                  ^- measurement ^- actual start of payload    ^- recover from here

      val reading25 = 2 * 256 + 1
      val reading10 = 4 * 256 + 3
      val id = 6 * 256 + 5

      val expectedMeasurement = SdsMeasurement(id, reading25, reading10)
      val actualMeasurement = Sds011(new LiteralSerialPort(validPayload), 1).take(1).compile.toVector.unsafeRunSync().head
      actualMeasurement shouldEqual expectedMeasurement
    }
  }

  "SDS011 Measurement " must {

    "allow measurements smaller than 1" in {
      val reading25 = 9
      val reading10 = 8
      val id = 7

      val actualPm25Measurement = SdsMeasurement(id, reading25, reading10)
      actualPm25Measurement.pm25str should equal("0.9")
      actualPm25Measurement.pm10str should equal("0.8")
    }

    "allow measurements greater than 1" in {
      val reading25 = 1024
      val reading10 = 512
      val id = 7

      val actualPm25Measurement = SdsMeasurement(id, reading25, reading10)
      actualPm25Measurement.pm25str should equal("102.4")
      actualPm25Measurement.pm10str should equal("51.2")
    }

    "fold to correct average value" in {
      val ms = SdsMeasurement(1, 25, 125) :: SdsMeasurement(1, 31, 131) :: SdsMeasurement(1, 35, 135) :: Nil
      val avg = Sds011.average(ms)
      avg.pm25 should equal(30)
      avg.pm10 should equal(130)
      avg.id should equal(1)
    }
  }
}

