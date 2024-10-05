package esexpr.unsigned

import java.lang.{Byte as JByte, Short as JShort, Integer as JInt, Long as JLong}

object UnsignedTypes {

  opaque type UByte = Byte

  object UByte {
    given CanEqual[UByte, UByte] = summon[CanEqual[Byte, Byte]]

    val MinValue: UByte = 0
    val MaxValue: UByte = -1

    extension (a: UByte)
      def +(b: UByte): UByte = (JByte.toUnsignedInt(a) + JByte.toUnsignedInt(b)).toByte
      def -(b: UByte): UByte = (JByte.toUnsignedInt(a) - JByte.toUnsignedInt(b)).toByte
      def *(b: UByte): UByte = (JByte.toUnsignedInt(a) * JByte.toUnsignedInt(b)).toByte
      def /(b: UByte): UByte = (JByte.toUnsignedInt(a) / JByte.toUnsignedInt(b)).toByte

      def compare(b: UByte): Int =
        JByte.toUnsignedInt(a).compare(JByte.toUnsignedInt(b))

      def <(b: UByte): Boolean =
        JByte.toUnsignedInt(a) < JByte.toUnsignedInt(b)

      def <=(b: UByte): Boolean =
        JByte.toUnsignedInt(a) <= JByte.toUnsignedInt(b)

      def >(b: UByte): Boolean =
        JByte.toUnsignedInt(a) > JByte.toUnsignedInt(b)

      def >=(b: UByte): Boolean =
        JByte.toUnsignedInt(a) >= JByte.toUnsignedInt(b)

      def toByte: Byte = a
      def toShort: Short = JByte.toUnsignedInt(a).toShort
      def toUShort: UShort = JByte.toUnsignedInt(a).toShort
      def toInt: Int = JByte.toUnsignedInt(a)
      def toUInt: UInt = JByte.toUnsignedInt(a)
      def toLong: Long = JByte.toUnsignedLong(a)
      def toULong: ULong = JByte.toUnsignedLong(a)
      def toBigInt: BigInt = JByte.toUnsignedLong(a)
    end extension
  }

  extension (a: Byte)
    def toUByte: UByte = a
    def toUShort: UShort = a.toShort
    def toUInt: UInt = a.toInt
    def toULong: ULong = a.toLong
  end extension

  opaque type UShort = Short

  object UShort {
    given CanEqual[UShort, UShort] = summon[CanEqual[Short, Short]]

    val MinValue: UShort = 0
    val MaxValue: UShort = -1

    extension (a: UShort)
      def +(b: UShort): UShort = (JShort.toUnsignedInt(a) + JShort.toUnsignedInt(b)).toShort
      def -(b: UShort): UShort = (JShort.toUnsignedInt(a) - JShort.toUnsignedInt(b)).toShort
      def *(b: UShort): UShort = (JShort.toUnsignedInt(a) * JShort.toUnsignedInt(b)).toShort
      def /(b: UShort): UShort = (JShort.toUnsignedInt(a) / JShort.toUnsignedInt(b)).toShort

      def compare(b: UShort): Int =
        JShort.toUnsignedInt(a).compare(JShort.toUnsignedInt(b))

      def <(b: UShort): Boolean =
        JShort.toUnsignedInt(a) < JShort.toUnsignedInt(b)

      def <=(b: UShort): Boolean =
        JShort.toUnsignedInt(a) <= JShort.toUnsignedInt(b)

      def >(b: UShort): Boolean =
        JShort.toUnsignedInt(a) > JShort.toUnsignedInt(b)

      def >=(b: UShort): Boolean =
        JShort.toUnsignedInt(a) >= JShort.toUnsignedInt(b)

      def toByte: Byte = (a : Short).toByte
      def toUByte: UByte = (a : Short).toByte
      def toShort: Short = a
      def toInt: Int = JShort.toUnsignedInt(a)
      def toUInt: UInt = JShort.toUnsignedInt(a)
      def toLong: Long = JShort.toUnsignedLong(a)
      def toULong: ULong = JShort.toUnsignedLong(a)
      def toBigInt: BigInt = JShort.toUnsignedLong(a)
    end extension
  }

  extension (a: Short)
    def toUByte: UByte = a.toByte
    def toUShort: UShort = a
    def toUInt: UInt = a.toInt
    def toULong: ULong = a.toLong
  end extension


  opaque type UInt = Int

  object UInt {
    given CanEqual[UInt, UInt] = summon[CanEqual[Int, Int]]

    val MinValue: UInt = 0
    val MaxValue: UInt = -1

    extension (a: UInt)
      def +(b: UInt): UInt = ((a: Int) + b)
      def -(b: UInt): UInt = ((a: Int) - b)
      def *(b: UInt): UInt = ((a: Int) * b)
      def /(b: UInt): UInt = JInt.divideUnsigned(a, b)

      def compare(b: UInt): Int =
        JInt.compareUnsigned(a, b)

      def <(b: UInt): Boolean =
        JInt.compareUnsigned(a, b) < 0

      def <=(b: UInt): Boolean =
        JInt.compareUnsigned(a, b) <= 0

      def >(b: UInt): Boolean =
        JInt.compareUnsigned(a, b) > 0

      def >=(b: UInt): Boolean =
        JInt.compareUnsigned(a, b) >= 0

      def toByte: Byte = (a : Int).toByte
      def toUByte: UByte = (a : Int).toByte
      def toShort: Short = (a : Int).toShort
      def toUShort: UShort = (a : Int).toShort
      def toInt: Int = a
      def toLong: Long = JInt.toUnsignedLong(a)
      def toULong: ULong = JInt.toUnsignedLong(a)
      def toBigInt: BigInt = JInt.toUnsignedLong(a)
    end extension
  }

  extension (a: Int)
    def toUByte: UByte = a.toByte
    def toUShort: UShort = a.toShort
    def toUInt: UInt = a
    def toULong: ULong = a.toLong
  end extension

  opaque type ULong = Long

  object ULong {
    given CanEqual[ULong, ULong] = summon[CanEqual[Long, Long]]

    val MinValue: ULong = 0
    val MaxValue: ULong = -1

    extension (a: ULong)
      def +(b: ULong): ULong = ((a: Long) + b)
      def -(b: ULong): ULong = ((a: Long) - b)
      def *(b: ULong): ULong = ((a: Long) * b)
      def /(b: ULong): ULong = JLong.divideUnsigned(a, b)

      def compare(b: ULong): Int =
        JLong.compareUnsigned(a, b)

      def <(b: ULong): Boolean =
        JLong.compareUnsigned(a, b) < 0

      def <=(b: ULong): Boolean =
        JLong.compareUnsigned(a, b) <= 0

      def >(b: ULong): Boolean =
        JLong.compareUnsigned(a, b) > 0

      def >=(b: ULong): Boolean =
        JLong.compareUnsigned(a, b) >= 0

      def toByte: Byte = (a : Long).toByte
      def toUByte: UByte = (a : Long).toByte
      def toShort: Short = (a : Long).toShort
      def toUShort: UShort = (a : Long).toShort
      def toInt: Int = (a : Long).toInt
      def toUInt: UInt = (a : Long).toInt
      def toLong: Long = a
      def toBigInt: BigInt = (((1: BigInt) << JLong.SIZE) - 1) & a
    end extension
  }

  extension (a: Long)
    def toUByte: UByte = a.toByte
    def toUShort: UShort = a.toShort
    def toUInt: UInt = a.toInt
    def toULong: ULong = a
  end extension


  extension (a: BigInt)
    def toUByte: UByte = a.toByte
    def toUShort: UShort = a.toShort
    def toUInt: UInt = a.toInt
    def toULong: ULong = a.toLong
  end extension


}

export UnsignedTypes.*

