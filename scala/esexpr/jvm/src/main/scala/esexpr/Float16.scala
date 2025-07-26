package esexpr

import java.lang.Float as JFloat

object Float16Type {
  opaque type Float16 = Short

  object Float16 {
    given CanEqual[Float16, Float16] = summon[CanEqual[Short, Short]]

    val PositiveInfinity: Float16 = JFloat.floatToFloat16(Float.PositiveInfinity)
    val NegativeInfinity: Float16 = JFloat.floatToFloat16(Float.NegativeInfinity)
    val MaxValue: Float16 = JFloat.floatToFloat16(65504.0f)
    val MinValue: Float16 = JFloat.floatToFloat16(-65504.0f)
    val NaN: Float16 = JFloat.floatToFloat16(Float.NaN)

    def shortBitsToFloat16(bits: Short): Float16 =
      bits

    def float16ToRawShortBits(f: Float16): Short =
      f

    extension (a: Float16)
      def +(b: Float16): Float16 = (JFloat.float16ToFloat(a) + JFloat.float16ToFloat(b)).toFloat16
      def -(b: Float16): Float16 = (JFloat.float16ToFloat(a) - JFloat.float16ToFloat(b)).toFloat16
      def *(b: Float16): Float16 = (JFloat.float16ToFloat(a) * JFloat.float16ToFloat(b)).toFloat16
      def /(b: Float16): Float16 = (JFloat.float16ToFloat(a) / JFloat.float16ToFloat(b)).toFloat16

      def compare(b: Float16): Int =
        JFloat.float16ToFloat(a).compareTo(JFloat.float16ToFloat(b))

      def <(b: Float16): Boolean =
        JFloat.float16ToFloat(a) < JFloat.float16ToFloat(b)

      def <=(b: Float16): Boolean =
        JFloat.float16ToFloat(a) <= JFloat.float16ToFloat(b)

      def >(b: Float16): Boolean =
        JFloat.float16ToFloat(a) > JFloat.float16ToFloat(b)

      def >=(b: Float16): Boolean =
        JFloat.float16ToFloat(a) >= JFloat.float16ToFloat(b)

      def toFloat: Float = JFloat.float16ToFloat(a)
      def toDouble: Double = toFloat 
      
      def isNaN: Boolean = JFloat.float16ToFloat(a).isNaN
      def isPosInfinity: Boolean = JFloat.float16ToFloat(a).isPosInfinity
      def isNegInfinity: Boolean = JFloat.float16ToFloat(a).isNegInfinity
    end extension
  }
  
  extension (a: Float)
    def toFloat16: Float16 = JFloat.floatToFloat16(a)

  extension (a: Double)
    def toFloat16: Float16 = JFloat.floatToFloat16(a.toFloat)
}

export Float16Type.*
