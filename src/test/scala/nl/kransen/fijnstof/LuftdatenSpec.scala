package nl.kransen.fijnstof

import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class LuftdatenSpec extends FlatSpec {

  "Valid sds011 measurement" should "map to correct luftdaten json" in {

    val validPm25Measurement = Pm25Measurement(id = 134, pm25 = 123)
    val validPm10Measurement = Pm10Measurement(id = 134, pm10 = 234)

    val expectedJson = s"""
                          |{
                          |    "sensordatavalues": [
                          |        {"value_type": "P1", "value": "23.4"},
                          |        {"value_type": "P2", "value": "12.3"}
                          |    ],
                          |    "software_version": "fijnstof 1.0"
                          |}
       """.stripMargin.replaceAll("\\s", "").replace("fijnstof", "fijnstof ")

    val actualJson = Luftdaten.toJson(validPm25Measurement, validPm10Measurement)

    expectedJson should equal(actualJson)
  }

}
