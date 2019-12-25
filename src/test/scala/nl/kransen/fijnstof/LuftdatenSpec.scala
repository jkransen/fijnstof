package nl.kransen.fijnstof

import nl.kransen.fijnstof.Sds011.SdsMeasurement
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class LuftdatenSpec extends FlatSpec {

  "Valid SDS011 measurement" should "map to correct Luftdaten json" in {

    val validPmMeasurement = SdsMeasurement(id = 134, pm25 = 123, pm10 = 234)

    val expectedJson = s"""
                          |{
                          |    "sensordatavalues": [
                          |        {"value_type": "P1", "value": "23.4"},
                          |        {"value_type": "P2", "value": "12.3"}
                          |    ],
                          |    "software_version": "fijnstof 1.2"
                          |}
       """.stripMargin.replaceAll("\\s", "").replace("fijnstof", "fijnstof ")

    val actualJson = Luftdaten.toJson(validPmMeasurement)

    expectedJson should equal(actualJson)
  }

}
