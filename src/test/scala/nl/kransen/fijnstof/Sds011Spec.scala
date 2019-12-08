package nl.kransen.fijnstof

import java.io.{ByteArrayInputStream, InputStream}

import javax.xml.bind.DatatypeConverter
import nl.kransen.fijnstof.SdsStateMachine.SdsMeasurement
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global

class Sds011Spec extends WordSpecLike with Matchers with BeforeAndAfterAll {

  "An Sds011 actor" must {

    "send precisely one measurement" in {
      val validPayload = "aa c0 01 02 03 04 05 06 15 ab"

      val reading25 = 2 * 256 + 1
      val reading10 = 4 * 256 + 3
      val id = 6 * 256 + 5

//      val expectedMeasurement = (Pm25Measurement(id, reading25), Pm10Measurement(id, reading10))
    }

    "skip 1 measurement on Checksum error" in {

      val invalidReading = "aa c0 01 02 03 04 05 06 14 ab"
      val validReading = "aa c0 01 00 01 00 01 00 03 ab"

      val expectedMeasurement = SdsMeasurement(1, 1, 1)
    }

    "skip 1 measurement on Bad footer" in {

      val invalidReading = "aa c0 01 02 03 04 05 06 14 ac"
      val validReading = "aa c0 01 00 01 00 01 00 03 ab"

      val expectedMeasurement = SdsMeasurement(1, 1, 1)
    }

    "recover when Measurement matches header" in {

      val validPayload = "aa c0 01 02 03 aa c0 01 02 03 04 05 06 15 ab aa c0 01 02 03 04 05 06 15 ab"
      //                  ^- measurement ^- actual start of payload    ^- recover from here

      val reading25 = 2 * 256 + 1
      val reading10 = 4 * 256 + 3
      val id = 6 * 256 + 5

      val expectedMeasurement = SdsMeasurement(id, reading25, reading10)
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

//    "fold to correct average value" in {
//      val ms = (Pm25Measurement(1, 25), Pm10Measurement(1, 125)) :: (Pm25Measurement(1, 31), Pm10Measurement(1, 131)) :: (Pm25Measurement(1, 35), Pm10Measurement(1, 135)) :: Nil
//      val avg = Sds011Protocol.average(ms)
//      avg._1.pm25 should equal(30)
//      avg._2.pm10 should equal(130)
//      avg._1.id should equal(1)
//      avg._2.id should equal(1)
//    }
  }

  implicit def hexToInputStream(str: String): InputStream = new ByteArrayInputStream(DatatypeConverter.parseHexBinary(str.replaceAll("\\s", "")))
}

