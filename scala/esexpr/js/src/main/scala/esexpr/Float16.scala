package esexpr

import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Float32Array, Int16Array, TypedArray}

object Float16Type {
  opaque type Float16 = Double

  object Float16 {
    val PositiveInfinity: Float16 = Double.PositiveInfinity
    val NegativeInfinity: Float16 = Double.NegativeInfinity
    val MaxValue: Float16 = 65504.0
    val MinValue: Float16 = -65504.0
    val NaN: Float16 = Double.NaN

    @Deprecated
    def shortBitsToFloat16(bits: Short): Float16 =
      val buff = Int16Array(1)
      buff(0) = bits
      Float16Array(buff.buffer, 0, buff.length)(0)
    end shortBitsToFloat16

    @Deprecated
    def float16ToRawShortBits(f: Float16): Short =
      val buff = Float16Array(1)
      buff(0) = f
      Int16Array(buff.buffer, 0, buff.length)(0)
    end float16ToRawShortBits

    extension (a: Float16)
      def +(b: Float16): Float16 = JSMathExtra.f16round(a + b)
      def -(b: Float16): Float16 = JSMathExtra.f16round(a - b)
      def *(b: Float16): Float16 = JSMathExtra.f16round(a * b)
      def /(b: Float16): Float16 = JSMathExtra.f16round(a / b)

      def compare(b: Float16): Int =
        java.lang.Double.compare(a, b)

      def <(b: Float16): Boolean =
        a < b

      def <=(b: Float16): Boolean =
        a <= b

      def >(b: Float16): Boolean =
        a > b

      def >=(b: Float16): Boolean =
        a >= b

      def toFloat: Float = (a: Double).toFloat
      def toDouble: Double = a

      def isNaN: Boolean = java.lang.Double.isNaN(a)
      def isPosInfinity: Boolean = a == java.lang.Double.POSITIVE_INFINITY
      def isNegInfinity: Boolean = a == java.lang.Double.NEGATIVE_INFINITY
    end extension
  }

  @js.native
  @JSGlobal("Math")
  private object JSMathExtra extends js.Object {
    def f16round(d: Double): Double = js.native 
  }

  @js.native
  @JSGlobal("Float16Array")
  private class Float16Array(length: Int) extends TypedArray[Float16, Float16Array] {
    def this(typedArray: Float32Array) = this(0)
    def this(array: js.Iterable[Float16]) = this(0)
    def this(buffer: ArrayBuffer, byteOffset: Int = 0, length: Int = ???) = this(0)
  }

  extension (a: Float)
    def toFloat16: Float16 = JSMathExtra.f16round(a)

  extension (a: Double)
    def toFloat16: Float16 = JSMathExtra.f16round(a)
}

export Float16Type.*
