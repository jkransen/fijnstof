import java.io.{ByteArrayInputStream, InputStream}

import org.scalatest.FlatSpec
import javax.xml.bind.DatatypeConverter
import org.scalatest.Matchers._

class Sds011Spec extends FlatSpec {

  "Valid payload with correct checksum" should "send precisely one measurement" in {

    val validPayload = "aa c0 01 02 03 04 05 06 15 ab"

    val reading25 = 2 * 256 + 1
    val reading10 = 4 * 256 + 3
    val id = 6 * 256 + 5

    val expectedMeasurement = Sds011Measurement(id, reading25, reading10)

    val actualMeasurement = new Sds011Reader().stream(validPayload).head

    actualMeasurement should equal(expectedMeasurement)
  }

  "Checksum error" should "skip 1 measurement" in {

    val invalidReading = "aa c0 01 02 03 04 05 06 14 ab"
    val validReading = "aa c0 01 00 01 00 01 00 03 ab"

    val expectedMeasurement = Sds011Measurement(1, 1, 1)

    val actualMeasurement = new Sds011Reader().stream(invalidReading + validReading).head

    actualMeasurement should equal(expectedMeasurement)
  }

  "Bad footer" should "skip 1 measurement" in {

    val invalidReading = "aa c0 01 02 03 04 05 06 14 ac"
    val validReading = "aa c0 01 00 01 00 01 00 03 ab"

    val expectedMeasurement = Sds011Measurement(1, 1, 1)

    val actualMeasurement = new Sds011Reader().stream(invalidReading + validReading).head

    actualMeasurement should equal(expectedMeasurement)
  }

  "Measurement matching header" should "recover" in {

    val validPayload = "aa c0 01 02 03 aa c0 01 02 03 04 05 06 15 ab aa c0 01 02 03 04 05 06 15 ab"
    //                  ^- measurement ^- actual start of payload    ^- recover from here

    val reading25 = 2 * 256 + 1
    val reading10 = 4 * 256 + 3
    val id = 6 * 256 + 5

    val expectedMeasurement = Sds011Measurement(id, reading25, reading10)

    val actualMeasurement = new Sds011Reader().stream(validPayload).head

    actualMeasurement should equal(expectedMeasurement)
  }

  "SDS011 Measurement " should "allow measurements smaller than 1" in {
    val reading25 = 9
    val reading10 = 8
    val id = 7

    val actualMeasurement = Sds011Measurement(id, reading25, reading10)
    actualMeasurement.pm25str should equal("0.9")
    actualMeasurement.pm10str should equal("0.8")
  }

  "SDS011 Measurement " should "allow measurements greater than 1" in {
    val reading25 = 1024
    val reading10 = 512
    val id = 7

    val actualMeasurement = Sds011Measurement(id, reading25, reading10)
    actualMeasurement.pm25str should equal("102.4")
    actualMeasurement.pm10str should equal("51.2")
  }

  "SDS011 Measurements " should "fold to correct average value" in {
    val ms = Sds011Measurement(1, 25, 125) :: Sds011Measurement(1, 31, 131) :: Sds011Measurement(1, 35, 135) :: Nil
    val avg = Sds011Measurement.average(ms)
    avg.pm25 should equal(30)
    avg.pm10 should equal(130)
    avg.id should equal(1)
  }

  implicit def hexToInputStream(str: String): InputStream = new ByteArrayInputStream(DatatypeConverter.parseHexBinary(str.replaceAll("\\s", "")))
}
