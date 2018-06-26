import java.io.InputStream

trait Measurement

trait MeasurementHandler {

  def handle(measurement: Measurement)
}

trait MeasurementSource[A <: Measurement] {
  def stream(in: InputStream): Stream[A]
}

object MeasurementSource {
  def apply[A <: Measurement, B <: MeasurementSource[A]](sourceType: String): Sds011Reader = {
    new Sds011Reader
  }
}