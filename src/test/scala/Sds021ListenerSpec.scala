import java.io.{ByteArrayInputStream, InputStream}

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory
import javax.xml.bind.DatatypeConverter

class Sds021ListenerSpec extends FlatSpec with MockFactory {

  private val mockRecipient = mockFunction[Report, Unit]

  "Valid payload with correct checksum" should "send precisely one Report" in {

    val validPayload = "aa c0 01 02 03 04 05 06 15 ab"

    val reading25 = 2 * 256 + 1
    val reading10 = 4 * 256 + 3
    val id = 5 * 256 + 6

    val expectedReport = Report(id,reading25,reading10)

    mockRecipient expects expectedReport once

    Sds021Listener.listen(validPayload, mockRecipient)

    // TODO end test
  }

  implicit def hexToInputStream(str: String): InputStream = new ByteArrayInputStream(DatatypeConverter.parseHexBinary(str.replaceAll("\\s", "")))
}
