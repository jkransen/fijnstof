import java.io.InputStream

trait Measurement

trait MeasurementHandler {

  def handle(measurement: Measurement)
}

trait MeasurementSource[A <: Measurement] {
  def stream(in: InputStream): Stream[A]
}
