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

    val expectedReport = Report(id, reading25, reading10)

    println("expecting")

    val actualReport = Sds011Reader.stream(validPayload).head

    println(s"actual: $actualReport")

    expectedReport should equal(actualReport)
  }

  implicit def hexToInputStream(str: String): InputStream = new ByteArrayInputStream(DatatypeConverter.parseHexBinary(str.replaceAll("\\s", "")))
}
