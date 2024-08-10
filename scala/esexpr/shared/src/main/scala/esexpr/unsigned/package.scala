package esexpr.unsigned

import java.lang.{Byte as JByte, Short as JShort, Integer as JInt, Long as JLong}

opaque type UByte = Byte
object UByte {
  val MinValue: UByte = 0
  val MaxValue: UByte = -1

  extension (a: UByte)
    def + (b: UByte): UByte = ((a : Byte) + b).toByte
    def - (b: UByte): UByte = ((a : Byte) - b).toByte
    def * (b: UByte): UByte = ((a : Byte) * b).toByte
    def / (b: UByte): UByte = (JByte.toUnsignedInt(a) / JByte.toUnsignedInt(b)).toByte

    def compareTo(b: UByte): Int =
      JByte.compareUnsigned(a, b)

    def < (b: UByte): Boolean =
      JByte.toUnsignedInt(a) < JByte.toUnsignedInt(b)
      
    def <= (b: UByte): Boolean =
      JByte.toUnsignedInt(a) <= JByte.toUnsignedInt(b)
      
    def > (b: UByte): Boolean =
      JByte.toUnsignedInt(a) > JByte.toUnsignedInt(b)
      
    def >= (b: UByte): Boolean =
      JByte.toUnsignedInt(a) >= JByte.toUnsignedInt(b)

    def toByte: Byte = a
    def toShort: Short = JByte.toUnsignedInt(a).toShort
    def toUShort: UShort = toShort
    def toInt: Int = JByte.toUnsignedInt(a)
    def toUInt: UInt = toInt
    def toLong: Long = JByte.toUnsignedLong(a)
    def toULong: ULong = toLong
  end extension 

  given Conversion[UByte, Short] = _.toShort
  given Conversion[UByte, Int] = _.toInt
  given Conversion[UByte, Long] = _.toLong
  given Conversion[UByte, UShort] = _.toUShort
  given Conversion[UByte, UInt] = _.toUInt
  given Conversion[UByte, ULong] = _.toULong
}

extension (a: Byte)
  def toUByte: UByte = a
  def toUShort: UShort = a.toShort
  def toUInt: UInt = a.toInt
  def toULong: ULong = a.toLong
end extension


opaque type UShort = Short
object UShort {
  val MinValue: UShort = 0
  val MaxValue: UShort = -1

  extension (a: UShort)
    def + (b: UShort): UShort = ((a : Short) + b).toShort
    def - (b: UShort): UShort = ((a : Short) - b).toShort
    def * (b: UShort): UShort = ((a : Short) * b).toShort
    def / (b: UShort): UShort = (JShort.toUnsignedInt(a) / JShort.toUnsignedInt(b)).toShort

    def compareTo(b: UShort): Int =
      JShort.compareUnsigned(a, b)

    def < (b: UShort): Boolean =
      JShort.toUnsignedInt(a) < JShort.toUnsignedInt(b)
      
    def <= (b: UShort): Boolean =
      JShort.toUnsignedInt(a) <= JShort.toUnsignedInt(b)
      
    def > (b: UShort): Boolean =
      JShort.toUnsignedInt(a) > JShort.toUnsignedInt(b)
      
    def >= (b: UShort): Boolean =
      JShort.toUnsignedInt(a) >= JShort.toUnsignedInt(b)

    def toByte: Byte = a.toByte
    def toUByte: UByte = toByte
    def toShort: Short = a
    def toInt: Int = JShort.toUnsignedInt(a)
    def toUInt: UInt = toInt
    def toLong: Long = JShort.toUnsignedLong(a)
    def toULong: ULong = toLong
  end extension 

  given Conversion[UShort, Int] = _.toInt
  given Conversion[UShort, Long] = _.toLong
  given Conversion[UShort, UInt] = _.toUInt
  given Conversion[UShort, ULong] = _.toULong
}

extension (a: Short)
  def toUByte: UByte = a.toByte
  def toUShort: UShort = a
  def toUInt: UInt = a.toInt
  def toULong: ULong = a.toLong
end extension


opaque type UInt = Int
object UInt {
  val MinValue: UInt = 0
  val MaxValue: UInt = -1

  extension (a: UInt)
    def + (b: UInt): UInt = ((a : Int) + b)
    def - (b: UInt): UInt = ((a : Int) - b)
    def * (b: UInt): UInt = ((a : Int) * b)
    def / (b: UInt): UInt = JInt.divideUnsigned(a, b)

    def compareTo(b: UInt): Int =
      JInt.compareUnsigned(a, b)

    def < (b: UInt): Boolean =
      JInt.compareUnsigned(a, b) < 0
      
    def <= (b: UInt): Boolean =
      JInt.compareUnsigned(a, b) <= 0
      
    def > (b: UInt): Boolean =
      JInt.compareUnsigned(a, b) > 0
      
    def >= (b: UInt): Boolean =
      JInt.compareUnsigned(a, b) >= 0

    def toByte: Byte = a.toByte
    def toUByte: UByte = toByte
    def toShort: Short = a.toShort
    def toUShort: UShort = toShort
    def toInt: Int = a
    def toLong: Long = JInt.toUnsignedLong(a)
    def toULong: ULong = toLong
  end extension 

  given Conversion[UInt, BigInt] = _.toLong
  given Conversion[UInt, Long] = _.toLong
  given Conversion[UInt, ULong] = _.toULong
}

extension (a: Int)
  def toUByte: UByte = a.toByte
  def toUShort: UShort = a.toShort
  def toUInt: UInt = a
  def toULong: ULong = a.toLong
end extension

opaque type ULong = Long
object ULong {
  val MinValue: ULong = 0
  val MaxValue: ULong = -1

  extension (a: ULong)
    def + (b: ULong): ULong = ((a : Long) + b)
    def - (b: ULong): ULong = ((a : Long) - b)
    def * (b: ULong): ULong = ((a : Long) * b)
    def / (b: ULong): ULong = JLong.divideUnsigned(a, b)

    def compareTo(b: ULong): Long =
      JLong.compareUnsigned(a, b)

    def < (b: ULong): Boolean =
      JLong.compareUnsigned(a, b) < 0
      
    def <= (b: ULong): Boolean =
      JLong.compareUnsigned(a, b) <= 0
      
    def > (b: ULong): Boolean =
      JLong.compareUnsigned(a, b) > 0
      
    def >= (b: ULong): Boolean =
      JLong.compareUnsigned(a, b) >= 0

    def toByte: Byte = a.toByte
    def toUByte: UByte = toByte
    def toShort: Short = a.toShort
    def toUShort: UShort = toShort
    def toInt: Int = a.toInt
    def toUInt: UInt = toInt
    def toLong: Long = a
  end extension 

  given Conversion[UInt, BigInt] = (((1 : BigInt) << JLong.SIZE) - 1) & _
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
