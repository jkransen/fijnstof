import java.io.{ByteArrayInputStream, InputStream}

import org.scalatest.FlatSpec
import javax.xml.bind.DatatypeConverter
import org.scalatest.Matchers._

class Sds011ListenerSpec extends FlatSpec {

  "Valid payload with correct checksum" should "send precisely one Report" in {

    val validPayload = "aa c0 01 02 03 04 05 06 15 ab"

    val reading25 = 2 * 256 + 1
    val reading10 = 4 * 256 + 3
    val id = 5 * 256 + 6

    val expectedReport = Sds011Measurement(id, reading25, reading10)

    val actualReport = Sds011Reader.stream(validPayload).head

    expectedReport should equal(actualReport)
  }

  "Checksum error" should "skip 1 Report" in {

    val invalidReading = "aa c0 01 02 03 04 05 06 14 ab"
    val validReading = "aa c0 01 00 01 00 00 01 03 ab"

    val expectedReport = Sds011Measurement(1, 1, 1)

    val actualReport = Sds011Reader.stream(invalidReading + validReading).head

    expectedReport should equal(actualReport)
  }

  "Bad footer" should "skip 1 Report" in {

    val invalidReading = "aa c0 01 02 03 04 05 06 14 ac"
    val validReading = "aa c0 01 00 01 00 00 01 03 ab"

    val expectedReport = Sds011Measurement(1, 1, 1)

    val actualReport = Sds011Reader.stream(invalidReading + validReading).head

    expectedReport should equal(actualReport)
  }

  "Measurement matching header" should "recover" in {

    val validPayload = "aa c0 01 02 03 aa c0 01 02 03 04 05 06 15 ab aa c0 01 02 03 04 05 06 15 ab"
    //                  ^- reading     ^- actual start of payload    ^- recover from here

    val reading25 = 2 * 256 + 1
    val reading10 = 4 * 256 + 3
    val id = 5 * 256 + 6

    val expectedReport = Sds011Measurement(id, reading25, reading10)

    val actualReport = Sds011Reader.stream(validPayload).head

    expectedReport should equal(actualReport)
  }

  implicit def hexToInputStream(str: String): InputStream = new ByteArrayInputStream(DatatypeConverter.parseHexBinary(str.replaceAll("\\s", "")))
}
