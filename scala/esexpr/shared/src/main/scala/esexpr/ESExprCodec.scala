package esexpr

import cats.*
import cats.data.{NonEmptySeq, NonEmptyList, NonEmptyVector}
import cats.implicits.given
import esexpr.ESExprCodec.DecodeError
import esexpr.unsigned.*

import scala.deriving.Mirror
import scala.quoted.*
import scala.compiletime.{asMatchable, constValue, erasedValue, summonInline}
import scala.deriving.Mirror.ProductOf

import zio.Chunk

trait ESExprCodec[T] {
  lazy val tags: ESExprTagSet
  def isEncodedEqual(x: T, y: T): Boolean
  def encode(value: T): ESExpr
  def decode(expr: ESExpr): Either[DecodeError, T]
}

object ESExprCodec {

  enum ErrorPath {
    case Current
    case Constructor(constructor: String)
    case Positional(constructor: String, pos: Int, next: ErrorPath)
    case Keyword(constructor: String, name: String, next: ErrorPath)
  }

  final case class DecodeError(message: String, path: ErrorPath) extends ESExprDecodeException(message + " " + path.toString)


  given ESExprCodec[String]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Str, ESExprTagSet.Empty)

    override def isEncodedEqual(x: String, y: String): Boolean = x == y

    override def encode(value: String): ESExpr =
      ESExpr.Str(value)

    override def decode(expr: ESExpr): Either[DecodeError, String] =
      expr match {
        case ESExpr.Str(s) => Right(s)
        case _ => Left(DecodeError("Expected a string", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Boolean]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Bool, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Boolean, y: Boolean): Boolean = x == y

    override def encode(value: Boolean): ESExpr =
      ESExpr.Bool(value)

    override def decode(expr: ESExpr): Either[DecodeError, Boolean] =
      expr match {
        case ESExpr.Bool(b) => Right(b)
        case _ => Left(DecodeError("Expected a bool", ErrorPath.Current))
      }
  end given

  given ESExprCodec[BigInt]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)

    override def isEncodedEqual(x: BigInt, y: BigInt): Boolean = x == y

    override def encode(value: BigInt): ESExpr =
      ESExpr.Int(value)

    override def decode(expr: ESExpr): Either[DecodeError, BigInt] =
      expr match {
        case ESExpr.Int(n) => Right(n)
        case _ => Left(DecodeError("Expected an int", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Byte]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Byte, y: Byte): Boolean = x == y

    override def encode(value: Byte): ESExpr =
      ESExpr.Int(BigInt(value))

    override def decode(expr: ESExpr): Either[DecodeError, Byte] =
      expr match {
        case ESExpr.Int(n) if n >= Byte.MinValue && n <= Byte.MaxValue => Right(n.toByte)
        case _ => Left(DecodeError("Expected an int within the range of an 8-bit signed integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[UByte]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)

    override def isEncodedEqual(x: UByte, y: UByte): Boolean = x == y

    override def encode(value: UByte): ESExpr =
      ESExpr.Int(value.toBigInt)

    override def decode(expr: ESExpr): Either[DecodeError, UByte] =
      expr match {
        case ESExpr.Int(n) if n >= UByte.MinValue.toBigInt && n <= UByte.MaxValue.toBigInt => Right(n.toUByte)
        case _ => Left(DecodeError("Expected an int within the range of an 8-bit unsigned integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Short]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Short, y: Short): Boolean = x == y

    override def encode(value: Short): ESExpr =
      ESExpr.Int(BigInt(value))

    override def decode(expr: ESExpr): Either[DecodeError, Short] =
      expr match {
        case ESExpr.Int(n) if n >= Short.MinValue && n <= Short.MaxValue => Right(n.toShort)
        case _ => Left(DecodeError("Expected an int within the range of a 16-bit signed integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[UShort]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)

    override def isEncodedEqual(x: UShort, y: UShort): Boolean = x == y

    override def encode(value: UShort): ESExpr =
      ESExpr.Int(value.toBigInt)

    override def decode(expr: ESExpr): Either[DecodeError, UShort] =
      expr match {
        case ESExpr.Int(n) if n >= UShort.MinValue.toBigInt && n <= UShort.MaxValue.toBigInt => Right(n.toUShort)
        case _ => Left(DecodeError("Expected an int within the range of an 16-bit unsigned integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Int]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Int, y: Int): Boolean = x == y

    override def encode(value: Int): ESExpr =
      ESExpr.Int(value)

    override def decode(expr: ESExpr): Either[DecodeError, Int] =
      expr match {
        case ESExpr.Int(n) if n >= Int.MinValue && n <= Int.MaxValue => Right(n.toInt)
        case _ => Left(DecodeError("Expected an int within the range of a 32-bit signed integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[UInt]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)

    override def isEncodedEqual(x: UInt, y: UInt): Boolean = x == y

    override def encode(value: UInt): ESExpr =
      ESExpr.Int(value.toBigInt)

    override def decode(expr: ESExpr): Either[DecodeError, UInt] =
      expr match {
        case ESExpr.Int(n) if n >= UInt.MinValue.toBigInt && n <= UInt.MaxValue.toBigInt => Right(n.toUInt)
        case _ => Left(DecodeError("Expected an int within the range of an 16-bit unsigned integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Long]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Long, y: Long): Boolean = x == y

    override def encode(value: Long): ESExpr =
      ESExpr.Int(value)

    override def decode(expr: ESExpr): Either[DecodeError, Long] =
      expr match {
        case ESExpr.Int(n) if n >= Long.MinValue && n <= Long.MaxValue => Right(n.toLong)
        case _ => Left(DecodeError("Expected an int within the range of a 64-bit signed integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[ULong]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Int, ESExprTagSet.Empty)

    override def isEncodedEqual(x: ULong, y: ULong): Boolean = x == y

    override def encode(value: ULong): ESExpr =
      ESExpr.Int(value.toBigInt)

    override def decode(expr: ESExpr): Either[DecodeError, ULong] =
      expr match {
        case ESExpr.Int(n) if n >= ULong.MinValue.toBigInt && n <= ULong.MaxValue.toBigInt => Right(n.toULong)
        case _ => Left(DecodeError("Expected an int within the range of a 64-bit unsigned integer", ErrorPath.Current))
      }
  end given

  given ESExprCodec[Float16]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float16, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Float16, y: Float16): Boolean =
      ESExpr.compareFloat16(x, y)

    override def encode(value: Float16): ESExpr =
      ESExpr.Float16(value)

    override def decode(expr: ESExpr): Either[DecodeError, Float16] =
      expr match {
        case ESExpr.Float16(f) => Right(f)
        case ESExpr.Float16NaN(bits) => Right(Float16.shortBitsToFloat16(bits))
        case _ => Left(DecodeError("Expected a float16", ErrorPath.Current))
      }
  end given

  given float16NaNCodec: ESExprCodec[Either[ESExpr.Float16NaN, Float16]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float16, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Either[ESExpr.Float16NaN, Float16], y: Either[ESExpr.Float16NaN, Float16]): Boolean =
      (x, y) match {
        case (Right(x), Right(y)) => ESExpr.compareFloat16(x, y)
        case (Left(x), Left(y)) => x.bits == y.bits
        case (Right(x), Left(y)) => ESExpr.compareFloat16ToBits(x, y.bits)
        case (Left(x), Right(y)) => ESExpr.compareFloat16ToBits(y, x.bits)
      }

    override def encode(value: Either[ESExpr.Float16NaN, Float16]): ESExpr =
      value match {
        case Right(value) => ESExpr.Float16(value)
        case Left(value) => value
      }

    override def decode(expr: ESExpr): Either[DecodeError, Either[ESExpr.Float16NaN, Float16]] =
      expr match {
        case ESExpr.Float16(f) => Right(Right(f))
        case expr: ESExpr.Float16NaN => Right(Left(expr))
        case _ => Left(DecodeError("Expected a float16", ErrorPath.Current))
      }
  end float16NaNCodec


  given ESExprCodec[Float]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float32, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Float, y: Float): Boolean =
      ESExpr.compareFloat(x, y)

    override def encode(value: Float): ESExpr =
      ESExpr.Float32(value)

    override def decode(expr: ESExpr): Either[DecodeError, Float] =
      expr match {
        case ESExpr.Float32(f) => Right(f)
        case ESExpr.Float32NaN(bits) => Right(java.lang.Float.intBitsToFloat(bits))
        case _ => Left(DecodeError("Expected a float32", ErrorPath.Current))
      }
  end given

  given floatNaNCodec: ESExprCodec[Either[ESExpr.Float32NaN, Float]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float32, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Either[ESExpr.Float32NaN, Float], y: Either[ESExpr.Float32NaN, Float]): Boolean =
      (x, y) match {
        case (Right(x), Right(y)) => ESExpr.compareFloat(x, y)
        case (Left(x), Left(y)) => x.bits == y.bits
        case (Right(x), Left(y)) => ESExpr.compareFloatToBits(x, y.bits)
        case (Left(x), Right(y)) => ESExpr.compareFloatToBits(y, x.bits)
      }

    override def encode(value: Either[ESExpr.Float32NaN, Float]): ESExpr =
      value match {
        case Right(value) => ESExpr.Float32(value)
        case Left(value) => value
      }

    override def decode(expr: ESExpr): Either[DecodeError, Either[ESExpr.Float32NaN, Float]] =
      expr match {
        case ESExpr.Float32(f) => Right(Right(f))
        case expr: ESExpr.Float32NaN => Right(Left(expr))
        case _ => Left(DecodeError("Expected a float32", ErrorPath.Current))
      }
  end floatNaNCodec

  given ESExprCodec[Double]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float64, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Double, y: Double): Boolean =
      ESExpr.compareDouble(x, y)

    override def encode(value: Double): ESExpr =
      ESExpr.Float64(value)

    override def decode(expr: ESExpr): Either[DecodeError, Double] =
      expr match {
        case ESExpr.Float64(f) => Right(f)
        case ESExpr.Float64NaN(bits) => Right(java.lang.Double.longBitsToDouble(bits))
        case _ => Left(DecodeError("Expected a float64", ErrorPath.Current))
      }
  end given

  given doubleNaNCodec: ESExprCodec[Either[ESExpr.Float64NaN, Double]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Float64, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Either[ESExpr.Float64NaN, Double], y: Either[ESExpr.Float64NaN, Double]): Boolean =
      (x, y) match {
        case (Right(x), Right(y)) => ESExpr.compareDouble(x, y)
        case (Left(x), Left(y)) => x.bits == y.bits
        case (Right(x), Left(y)) => ESExpr.compareDoubleToBits(x, y.bits)
        case (Left(x), Right(y)) => ESExpr.compareDoubleToBits(y, x.bits)
      }

    override def encode(value: Either[ESExpr.Float64NaN, Double]): ESExpr =
      value match {
        case Right(value) => ESExpr.Float64(value)
        case Left(value) => value
      }

    override def decode(expr: ESExpr): Either[DecodeError, Either[ESExpr.Float64NaN, Double]] =
      expr match {
        case ESExpr.Float64(f) => Right(Right(f))
        case expr: ESExpr.Float64NaN => Right(Left(expr))
        case _ => Left(DecodeError("Expected a float64", ErrorPath.Current))
      }
  end doubleNaNCodec

  given iarray8Codec: ESExprCodec[IArray[Byte]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array8, ESExprTagSet.Empty)

    override def isEncodedEqual(x: IArray[Byte], y: IArray[Byte]): Boolean =
      java.util.Arrays.equals(x.asInstanceOf[Array[Byte]], y.asInstanceOf[Array[Byte]])

    override def encode(value: IArray[Byte]): ESExpr =
      ESExpr.Array8(Chunk.fromArray(IArray.genericWrapArray(value).toArray))

    override def decode(expr: ESExpr): Either[DecodeError, IArray[Byte]] =
      expr match {
        case ESExpr.Array8(b) => Right(IArray.unsafeFromArray(b.toArray))
        case _ => Left(DecodeError("Expected an array8 value", ErrorPath.Current))
      }
  end iarray8Codec

  given chunk8Codec: ESExprCodec[Chunk[Byte]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array8, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Chunk[Byte], y: Chunk[Byte]): Boolean = x == y

    override def encode(value: Chunk[Byte]): ESExpr =
      ESExpr.Array8(value)

    override def decode(expr: ESExpr): Either[DecodeError, Chunk[Byte]] =
      expr match {
        case ESExpr.Array8(b) => Right(b)
        case _ => Left(DecodeError("Expected a array8 value", ErrorPath.Current))
      }
  end chunk8Codec

  given iarray16Codec: ESExprCodec[IArray[Short]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array16, ESExprTagSet.Empty)

    override def isEncodedEqual(x: IArray[Short], y: IArray[Short]): Boolean =
      java.util.Arrays.equals(x.asInstanceOf[Array[Short]], y.asInstanceOf[Array[Short]])

    override def encode(value: IArray[Short]): ESExpr =
      ESExpr.Array16(Chunk.fromArray(IArray.genericWrapArray(value).toArray))

    override def decode(expr: ESExpr): Either[DecodeError, IArray[Short]] =
      expr match {
        case ESExpr.Array16(b) => Right(IArray.unsafeFromArray(b.toArray))
        case _ => Left(DecodeError("Expected an array16 value", ErrorPath.Current))
      }
  end iarray16Codec

  given chunk16Codec: ESExprCodec[Chunk[Short]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array16, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Chunk[Short], y: Chunk[Short]): Boolean = x == y

    override def encode(value: Chunk[Short]): ESExpr =
      ESExpr.Array16(value)

    override def decode(expr: ESExpr): Either[DecodeError, Chunk[Short]] =
      expr match {
        case ESExpr.Array16(b) => Right(b)
        case _ => Left(DecodeError("Expected a array16 value", ErrorPath.Current))
      }
  end chunk16Codec

  given iarray32Codec: ESExprCodec[IArray[Int]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array32, ESExprTagSet.Empty)

    override def isEncodedEqual(x: IArray[Int], y: IArray[Int]): Boolean =
      java.util.Arrays.equals(x.asInstanceOf[Array[Int]], y.asInstanceOf[Array[Int]])

    override def encode(value: IArray[Int]): ESExpr =
      ESExpr.Array32(Chunk.fromArray(IArray.genericWrapArray(value).toArray))

    override def decode(expr: ESExpr): Either[DecodeError, IArray[Int]] =
      expr match {
        case ESExpr.Array32(b) => Right(IArray.unsafeFromArray(b.toArray))
        case _ => Left(DecodeError("Expected an array32 value", ErrorPath.Current))
      }
  end iarray32Codec

  given chunk32Codec: ESExprCodec[Chunk[Int]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array32, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Chunk[Int], y: Chunk[Int]): Boolean = x == y

    override def encode(value: Chunk[Int]): ESExpr =
      ESExpr.Array32(value)

    override def decode(expr: ESExpr): Either[DecodeError, Chunk[Int]] =
      expr match {
        case ESExpr.Array32(b) => Right(b)
        case _ => Left(DecodeError("Expected a array32 value", ErrorPath.Current))
      }
  end chunk32Codec

  given iarray64Codec: ESExprCodec[IArray[Long]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array8, ESExprTagSet.Empty)

    override def isEncodedEqual(x: IArray[Long], y: IArray[Long]): Boolean =
      java.util.Arrays.equals(x.asInstanceOf[Array[Long]], y.asInstanceOf[Array[Long]])

    override def encode(value: IArray[Long]): ESExpr =
      ESExpr.Array64(Chunk.fromArray(IArray.genericWrapArray(value).toArray))

    override def decode(expr: ESExpr): Either[DecodeError, IArray[Long]] =
      expr match {
        case ESExpr.Array64(b) => Right(IArray.unsafeFromArray(b.toArray))
        case _ => Left(DecodeError("Expected an array64 value", ErrorPath.Current))
      }
  end iarray64Codec

  given chunk64Codec: ESExprCodec[Chunk[Long]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Array8, ESExprTagSet.Empty)

    override def isEncodedEqual(x: Chunk[Long], y: Chunk[Long]): Boolean = x == y

    override def encode(value: Chunk[Long]): ESExpr =
      ESExpr.Array64(value)

    override def decode(expr: ESExpr): Either[DecodeError, Chunk[Long]] =
      expr match {
        case ESExpr.Array64(b) => Right(b)
        case _ => Left(DecodeError("Expected a array64 value", ErrorPath.Current))
      }
  end chunk64Codec

  given [A: ESExprCodec] => ESExprCodec[Seq[A]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Constructor("list"), ESExprTagSet.Empty)

    override def isEncodedEqual(x: Seq[A], y: Seq[A]): Boolean =
      x.size == y.size && x.zip(y).forall(summon[ESExprCodec[A]].isEncodedEqual.tupled)

    override def encode(value: Seq[A]): ESExpr =
      ESExpr.Constructor(
        "list",
        value.map(summon[ESExprCodec[A]].encode),
        Map(),
      )

    override def decode(expr: ESExpr): Either[DecodeError, Seq[A]] =
      expr match {
        case expr: ESExpr.Constructor =>
          for
            _ <- if expr.constructor == "list" then Right(()) else Left(DecodeError(s"Invalid constructor name for list: ${expr.constructor}", ErrorPath.Current))
            _ <- if expr.kwargs.isEmpty then Right(()) else Left(DecodeError(s"Unexpected keyword arguments for list: ${expr.constructor}", ErrorPath.Current))
            values <- expr.args.zipWithIndex
              .traverse((arg, i) => summon[ESExprCodec[A]].decode(arg).left.map(error => DecodeError(error.message, ErrorPath.Positional("list", i, error.path))))
          yield values

        case _ => Left(DecodeError("Expected constructor for list", ErrorPath.Current))
      }
  end given

  given [A: ESExprCodec] => ESExprCodec[NonEmptySeq[A]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Constructor("list"), ESExprTagSet.Empty)

    override def isEncodedEqual(x: NonEmptySeq[A], y: NonEmptySeq[A]): Boolean =
      x.size == y.size && x.zip(y).forall(summon[ESExprCodec[A]].isEncodedEqual.tupled)

    override def encode(value: NonEmptySeq[A]): ESExpr =
      summon[ESExprCodec[Seq[A]]].encode(value.toList)

    override def decode(expr: ESExpr): Either[DecodeError, NonEmptySeq[A]] =
      summon[ESExprCodec[Seq[A]]].decode(expr).flatMap { values =>
        NonEmptySeq.fromSeq(values).toRight(DecodeError("List was expected to be non-empty", ErrorPath.Current))
      }
  end given

  given [A: ESExprCodec] => ESExprCodec[NonEmptyList[A]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Constructor("list"), ESExprTagSet.Empty)

    override def isEncodedEqual(x: NonEmptyList[A], y: NonEmptyList[A]): Boolean =
      x.size == y.size && x.zip(y).forall(summon[ESExprCodec[A]].isEncodedEqual.tupled)

    override def encode(value: NonEmptyList[A]): ESExpr =
      summon[ESExprCodec[Seq[A]]].encode(value.toList)

    override def decode(expr: ESExpr): Either[DecodeError, NonEmptyList[A]] =
      summon[ESExprCodec[Seq[A]]].decode(expr).flatMap { values =>
        NonEmptyList.fromList(values.toList).toRight(DecodeError("List was expected to be non-empty", ErrorPath.Current))
      }
  end given

  given [A: ESExprCodec] => ESExprCodec[NonEmptyVector[A]]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Constructor("list"), ESExprTagSet.Empty)

    override def isEncodedEqual(x: NonEmptyVector[A], y: NonEmptyVector[A]): Boolean =
      x.size == y.size && x.toVector.zip(y.toVector).forall(summon[ESExprCodec[A]].isEncodedEqual.tupled)

    override def encode(value: NonEmptyVector[A]): ESExpr =
      summon[ESExprCodec[Seq[A]]].encode(value.toList)

    override def decode(expr: ESExpr): Either[DecodeError, NonEmptyVector[A]] =
      summon[ESExprCodec[Seq[A]]].decode(expr).flatMap { values =>
        NonEmptyVector.fromVector(values.toVector).toRight(DecodeError("List was expected to be non-empty", ErrorPath.Current))
      }
  end given

  given [A: ESExprCodec] => ESExprCodec[Option[A]]:
    override lazy val tags: ESExprTagSet = summon[ESExprCodec[A]].tags.add(ESExprTag.Null)

    override def isEncodedEqual(x: Option[A], y: Option[A]): Boolean =
      (x, y) match {
        case (Some(x), Some(y)) => summon[ESExprCodec[A]].isEncodedEqual(x, y)
        case (None, None) => true
        case _ => false
      }

    override def encode(value: Option[A]): ESExpr =
      value.fold(ESExpr.Null(0))(a =>
        summon[ESExprCodec[A]].encode(a) match {
          case ESExpr.Null(level) => ESExpr.Null(level + 1)
          case res => res
        }
      )

    override def decode(expr: ESExpr): Either[DecodeError, Option[A]] =
      expr match {
        case ESExpr.Null(0) => Right(None)
        case ESExpr.Null(level) => summon[ESExprCodec[A]].decode(ESExpr.Null(level - 1)).map(Some.apply) 
        case _ => summon[ESExprCodec[A]].decode(expr).map(Some.apply)
      }
  end given

  export CodecDerivation.derived

  object CodecDerivation:
    import MacroUtils.*
    inline def derived[T](using m: Mirror.Of[T]): ESExprCodec[T] =
      inline m match {
        case m: Mirror.SumOf[T] => derivedSum[T](using m)
        case m: Mirror.ProductOf[T] => derivedProduct[T](using m)
      }


    inline def derivedSum[T](using m: Mirror.SumOf[T]): ESExprCodec[T] =
      inline if typeHasAnn[T, simple] then
        val caseNames = simpleEnumCaseNames[m.MirroredElemTypes](Set())
        val caseValues = simpleEnumCaseValues[T, m.MirroredElemTypes]
        simpleEnumCodec(caseNames.toArray, caseNames.zip(caseValues).toMap)
      else
        lazy val codecMap = buildSumCodecs[T, m.MirroredElemTypes](ESExprTagSet.Empty)
        derivedSumCreateCodec(codecMap)
      end if

    inline def simpleEnumCaseNames[Cases <: Tuple](inline prevNames: Set[String]): List[String] =
      inline erasedValue[Cases] match
        case _: (head *: tail) =>
          if setContains(prevNames, getConstructorInline[head]) then
            scala.compiletime.error("Overlapping constructors for simple enum cases: " + setShow(prevNames) + " with " + getConstructorInline[head])

          val name = getConstructorInline[head]

          name :: simpleEnumCaseNames[tail](setAdd(prevNames, getConstructorInline[head]))

        case _: EmptyTuple =>
          Nil
      end match


    inline def simpleEnumCaseValues[T, Cases <: Tuple](using m: Mirror.SumOf[T]): List[T] =
      inline erasedValue[Cases] match
        case _: (head *: tail) =>
          val value = summonInline[Mirror.ProductOf[head] { type MirroredElemTypes = EmptyTuple }].fromProductTyped(EmptyTuple)
          summonInline[head <:< T](value) :: simpleEnumCaseValues[T, tail]

        case _: EmptyTuple =>
          Nil
      end match


    inline def buildSumCodecs[T, SubTypes <: Tuple](inline prevCaseTags: ESExprTagSet): List[ESExprCodec[? <: T]] =
      inline erasedValue[SubTypes] match
        case _: (htype *: ttypes) =>
          if !ESExprTagSet.isDisjoint(prevCaseTags, ESExprTagSetProvider.tagsFor[htype]) then
            scala.compiletime.error("Overlapping tags for enum cases: " + ESExprTagSet.show(prevCaseTags) + " with " + ESExprTagSet.show(ESExprTagSetProvider.tagsFor[htype]))

          val hcodec =  derived[htype](using summonInline[Mirror.Of[htype]])
          val tailCodecs = buildSumCodecs[T, ttypes](ESExprTagSet.union(prevCaseTags, ESExprTagSetProvider.tagsFor[htype]))
          val hcodec2 = summonInline[ESExprCodec[htype] <:< ESExprCodec[? <: T]](hcodec)

          hcodec2 :: tailCodecs
        case _: EmptyTuple => Nil
      end match


    inline def derivedProduct[T](using m: Mirror.ProductOf[T]): ESExprCodec[T] =
      inline if typeHasAnn[T, inlineValue] then

        inline erasedValue[m.MirroredElemTypes] match {
          case _: (elem *: EmptyTuple) =>
            lazy val elemCodec = summonInline[ESExprCodec[elem]]

            type InnerMirror = Mirror.ProductOf[T & Product] { type MirroredElemTypes = elem *: EmptyTuple }
            val codec = inlineValueCodec[T & Product, elem](elemCodec)(using summonInline[m.type <:< InnerMirror](m))
            summonInline[ESExprCodec[T & Product] =:= ESExprCodec[T]](codec)
        }

      else
        val derivedTuple = derivedProductTuple[T, m.MirroredLabel, m.MirroredElemLabels, m.MirroredElemTypes](
          prevOptionalPositionalTags = ESExprTagSet.Empty,
          keywords = Set[String](),
          hasDict = false,
        )
        val constructor =
          inline if typeHasAnn[T, constructor] then
            typeGetAnn[T, constructor].name
          else
            toSExprName(constValue[m.MirroredLabel])



        var codec: ESExprCodec[T & Product] = DerivedProductCodec[T & Product, m.MirroredElemTypes](constructor, derivedTuple)(
          using summonInline[Mirror.ProductOf[T] {type MirroredElemTypes = m.MirroredElemTypes} =:= Mirror.ProductOf[T & Product] {type MirroredElemTypes = m.MirroredElemTypes}](m)
        )

        summonInline[ESExprCodec[T & Product] =:= ESExprCodec[T]](codec)
      end if
      
    final class DerivedProductCodec[T <: Product, Types <: Tuple](constructor: String, derivedTuple: ESExprCodecProduct[Types])(using m: Mirror.ProductOf[T] { type MirroredElemTypes = Types }) extends ESExprCodec[T] {
      override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Constructor(constructor), ESExprTagSet.Empty)

      override def isEncodedEqual(x: T, y: T): Boolean =
        derivedTuple.isEncodedEqual(
          Tuple.fromProductTyped[T](x)(using m),
          Tuple.fromProductTyped[T](y)(using m),
        )

      override def encode(value: T): ESExpr =
        val (args, kwargs) = derivedTuple.encode(
          Tuple.fromProductTyped[T](value)(using m)
        )

        ESExpr.Constructor(constructor, args, kwargs)
      end encode

      override def decode(expr: ESExpr): Either[DecodeError, T] =
        expr match {
          case expr: ESExpr.Constructor =>
            for
              _ <- if expr.constructor == constructor then Right(()) else Left(DecodeError(s"Unexpected constructor name: ${expr.constructor}", ErrorPath.Current))
              (res, state) <- derivedTuple.decode(ProductDecodeState(0, expr.args, expr.kwargs))
                .left.map(_.toDecodeError(constructor))

              _ <-
                if state.args.nonEmpty || state.kwargs.nonEmpty then
                  Left(DecodeError("Extra arguments were provided", ErrorPath.Current))
                else
                  Right(())

            yield m.fromTuple(res)

          case _ =>
            Left(DecodeError("Expected a constructed value", ErrorPath.Current))
        }
    }

    trait ESExprCodecProduct[T] {
      def isEncodedEqual(x: T, y: T): Boolean
      def encode(value: T): (Seq[ESExpr], Map[String, ESExpr])
      def decode(state: ProductDecodeState): Either[ProductDecodeError, (T, ProductDecodeState)]
    }

    final case class ProductDecodeState(positionalIndex: Int, args: Seq[ESExpr], kwargs: Map[String, ESExpr])

    enum ProductErrorPath derives CanEqual {
      case Current
      case Positional(pos: Int, next: ErrorPath)
      case Keyword(name: String, next: ErrorPath)
    }

    final case class ProductDecodeError(message: String, path: ProductErrorPath) {
      def toDecodeError(constructor: String): DecodeError =
        DecodeError(message, path match {
          case ProductErrorPath.Current => ErrorPath.Constructor(constructor)
          case ProductErrorPath.Positional(pos, next) => ErrorPath.Positional(constructor, pos, next)
          case ProductErrorPath.Keyword(name, next) => ErrorPath.Keyword(constructor, name, next)
        })
    }

    inline def derivedProductTuple[T, TypeLabel <: String, Labels <: Tuple, Types <: Tuple](
      inline prevOptionalPositionalTags: ESExprTagSet,
      inline keywords: Set[String],
      inline hasDict: Boolean,
    ): ESExprCodecProduct[Types] =
      inline (erasedValue[Labels], erasedValue[Types]) match
        case _: ((hlabel *: tlabels), (htype *: ttype)) =>
          val (fieldCodec, tailCodec) =
            inline if caseFieldHasAnn[T, keyword](constValue[hlabel & String]) then
              val keyName = getKeywordName[T, hlabel]

              inline if hasDict then
                scala.compiletime.error("Keyword arguments cannot be used with dict arguments")

              inline if setContains(keywords, getKeywordName[T, hlabel]) then
                scala.compiletime.error(s"Duplicate keyword argument \"${getKeywordName[T, hlabel]}\"")


              val fieldCodec =
                inline if caseFieldHasAnn[T, optional](constValue[hlabel & String]) then
                  lazy val optionalValueCodec = summonInline[OptionalValueCodec[htype]]
                  optionalKeywordProductCodec(keyName, optionalValueCodec)
                else
                  lazy val valueCodec = summonInline[ESExprCodec[htype]]

                  if caseFieldHasDefaultValue[T, htype](constValue[hlabel & String]) then
                    defaultKeywordProductCodec(keyName, valueCodec, caseFieldDefaultValue[T, htype](constValue[hlabel & String]))
                  else if caseFieldHasAnn[T, defaultValue[?]](constValue[hlabel & String]) then
                    defaultKeywordProductCodec(keyName, valueCodec, caseFieldGetAnn[T, defaultValue[htype]](constValue[hlabel & String]).value)
                  else
                    requiredKeywordProductCodec(keyName, valueCodec)
                  end if
                end if

              val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttype](
                prevOptionalPositionalTags = prevOptionalPositionalTags,
                keywords = setAdd(keywords, getKeywordName[T, hlabel]),
                hasDict = hasDict,
              )

              (fieldCodec, tailCodec)

            else if caseFieldHasAnn[T, vararg](constValue[hlabel & String]) then
              val varargCodec = summonInline[VarargCodec[htype]]
              validatePositionalTags(prevOptionalPositionalTags, ESExprTagSetProvider.tagsForVararg[htype], constValue[hlabel & String])

              val fieldCodec = varargProductCodec(varargCodec)
              val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttype](
                prevOptionalPositionalTags = ESExprTagSet.union(prevOptionalPositionalTags, ESExprTagSetProvider.tagsForVararg[htype]),
                keywords = keywords,
                hasDict = hasDict,
              )

              (fieldCodec, tailCodec)

            else if caseFieldHasAnn[T, dict](constValue[hlabel & String]) then
              inline if hasDict then
                scala.compiletime.error("Only a single dict argument is allowed")

              inline if setNonEmpty(keywords) then
                scala.compiletime.error("Keyword arguments cannot be used with dict arguments")

              lazy val dictCodec = summonInline[DictCodec[htype]]
              val fieldCodec = dictProductCodec(dictCodec)
              val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttype](
                prevOptionalPositionalTags = prevOptionalPositionalTags,
                keywords = keywords,
                hasDict = true,
              )

              (fieldCodec, tailCodec)
            else
              inline if caseFieldHasAnn[T, optional](constValue[hlabel & String]) then
                val optionalValueCodec = summonInline[OptionalValueCodec[htype]]
                validatePositionalTags(prevOptionalPositionalTags, ESExprTagSetProvider.tagsForOptional[htype], constValue[hlabel & String])

                val fieldCodec = optionalPositionalProductCodec(optionalValueCodec)
                val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttype](
                  prevOptionalPositionalTags = ESExprTagSet.union(prevOptionalPositionalTags, ESExprTagSetProvider.tagsForOptional[htype]),
                  keywords = keywords,
                  hasDict = hasDict,
                )

                (fieldCodec, tailCodec)

              else if caseFieldHasDefaultValue[T, htype](constValue[hlabel & String]) then
                val valueCodec = summonInline[ESExprCodec[htype]]
                validatePositionalTags(prevOptionalPositionalTags, ESExprTagSetProvider.tagsFor[htype], constValue[hlabel & String])

                val fieldCodec = defaultPositionalProductCodec(valueCodec, caseFieldDefaultValue[T, htype](constValue[hlabel & String]))
                val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttype](
                  prevOptionalPositionalTags = ESExprTagSet.union(prevOptionalPositionalTags, ESExprTagSetProvider.tagsFor[htype]),
                  keywords = keywords,
                  hasDict = hasDict,
                )

                (fieldCodec, tailCodec)

              else if caseFieldHasAnn[T, defaultValue[?]](constValue[hlabel & String]) then
                val valueCodec = summonInline[ESExprCodec[htype]]
                validatePositionalTags(prevOptionalPositionalTags, ESExprTagSetProvider.tagsFor[htype], constValue[hlabel & String])

                val fieldCodec = defaultPositionalProductCodec(valueCodec, caseFieldGetAnn[T, defaultValue[htype]](constValue[hlabel & String]).value)
                val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttype](
                  prevOptionalPositionalTags = ESExprTagSet.union(prevOptionalPositionalTags, ESExprTagSetProvider.tagsFor[htype]),
                  keywords = keywords,
                  hasDict = hasDict,
                )

                (fieldCodec, tailCodec)
              else
                val valueCodec = summonInline[ESExprCodec[htype]]
                val fieldCodec = requiredPositionalProductCodec(valueCodec)

                val tailCodec = derivedProductTuple[T, TypeLabel, tlabels, ttype](
                  prevOptionalPositionalTags = ESExprTagSet.Empty,
                  keywords = keywords,
                  hasDict = hasDict,
                )

                (fieldCodec, tailCodec)
              end if
            end if

          val codec = new ProductConsCodec[htype, ttype](fieldCodec, tailCodec)

          summonInline[ESExprCodecProduct[htype *: ttype] <:< ESExprCodecProduct[Types]](codec)

        case _: (EmptyTuple, EmptyTuple) =>
          val emptyProductCodec = ProductEmptyCodec()

          summonInline[ESExprCodecProduct[EmptyTuple] =:= ESExprCodecProduct[Types]](emptyProductCodec)
      end match


    private inline def validatePositionalTags(inline prevOptionalPositionalTags: ESExprTagSet, inline tags: ESExprTagSet, inline fieldName: String): Unit =
      inline if ESExprTagSet.isAll(prevOptionalPositionalTags) then
          scala.compiletime.error("Field '" + fieldName + "' cannot follow optional positional arguments with all tags")

      inline if !ESExprTagSet.isDisjoint(prevOptionalPositionalTags, tags) then
        scala.compiletime.error("Field '" + fieldName + "' must have distinct tags from immediately preceding optional positional arguments")
    end validatePositionalTags

    private inline def getKeywordName[T, HLabel]: String =
      inline extractKeywordName(caseFieldGetAnn[T, keyword](constValue[HLabel & String])) match {
        case "" => toSExprNameInline(constValue[HLabel & String])
        case name => name
      }

    private inline def extractKeywordName(inline kwValue: keyword): String =
      ${ extractKeywordNameMacro('kwValue) }

    private def extractKeywordNameMacro(kwValue: Expr[keyword])(using q: Quotes): Expr[String] =
      import q.reflect.*

      kwValue match {
        case '{ new esexpr.keyword($name) } =>
          name.asTerm.underlyingArgument match {
            case Select(_, defaultName) if defaultName.endsWith("$default$1") => Expr("")
            case _ => name
          }

        case kwExpr => report.errorAndAbort("Invalid keyword annotation: " + kwExpr.show)
      }

    end extractKeywordNameMacro

    final class ProductConsCodec[HType, TType <: Tuple](fieldCodec: ESExprCodecProduct[HType], tailCodec: ESExprCodecProduct[TType]) extends ESExprCodecProduct[HType *: TType] {
      override def isEncodedEqual(x: HType *: TType, y: HType *: TType): Boolean =
        val (xHead *: xTail) = x
        val (yHead *: yTail) = y

        fieldCodec.isEncodedEqual(xHead, yHead) &&
          tailCodec.isEncodedEqual(xTail, yTail)
      end isEncodedEqual

      override def encode(value: HType *: TType): (Seq[ESExpr], Map[String, ESExpr]) =
        val (head *: tail) = value

        val (args1, kwargs1) = fieldCodec.encode(head)
        val (args2, kwargs2) = tailCodec.encode(tail)
        (args1 ++ args2, kwargs1 ++ kwargs2)
      end encode

      override def decode(state: ProductDecodeState): Either[ProductDecodeError, (HType *: TType, ProductDecodeState)] =
        for
          (h, state) <- fieldCodec.decode(state)
          (t, state) <- tailCodec.decode(state)
        yield (h *: t, state)
    }

    final class ProductEmptyCodec extends ESExprCodecProduct[EmptyTuple] {
      override def isEncodedEqual(x: EmptyTuple, y: EmptyTuple): Boolean =
        true

      override def encode(value: EmptyTuple): (Seq[ESExpr], Map[String, ESExpr]) =
        (Seq(), Map())

      override def decode(state: ProductDecodeState): Either[ProductDecodeError, (EmptyTuple, ProductDecodeState)] =
        Right((EmptyTuple, state))
    }

    def varargProductCodec[A](varargCodec: VarargCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def isEncodedEqual(x: A, y: A): Boolean =
          varargCodec.isEncodedEqual(x, y)

        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          (varargCodec.encodeVararg(value), Map())

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          val argExprs = state.args.takeWhile(arg => varargCodec.elementTags.contains(arg.tag))

          varargCodec.decodeVararg(argExprs) match {
            case Left((i, DecodeError(message, path))) => Left(ProductDecodeError(message, ProductErrorPath.Positional(state.positionalIndex + i, path)))
            case Right(a) => Right((a, state.copy(args = state.args.drop(argExprs.size), positionalIndex = state.positionalIndex + argExprs.size)))
          }
        end decode
      }

    def dictProductCodec[A](dictCodec: DictCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def isEncodedEqual(x: A, y: A): Boolean =
          dictCodec.isEncodedEqual(x, y)

        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          (Seq(), dictCodec.encodeDict(value))

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          dictCodec.decodeDict(state.kwargs) match {
            case Left((kw, DecodeError(message, path))) => Left(ProductDecodeError(message, ProductErrorPath.Keyword(kw, path)))
            case Right(a) => Right((a, state.copy(kwargs = Map())))
          }
      }

    def optionalKeywordProductCodec[A](keyword: String, optionalValueCodec: OptionalValueCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def isEncodedEqual(x: A, y: A): Boolean =
          optionalValueCodec.isEncodedEqual(x, y)

        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          optionalValueCodec.encodeOptional(value) match {
            case Some(expr) => (Seq(), Map(keyword -> expr))
            case _: None.type => (Seq(), Map())
          }

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          optionalValueCodec.decodeOptional(state.kwargs.get(keyword)) match {
            case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Keyword(keyword, path)))
            case Right(a) => Right((a, state.copy(kwargs = state.kwargs.removed(keyword))))
          }
      }

    def defaultKeywordProductCodec[A](keyword: String, codec: ESExprCodec[A], defaultValue: => A): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def isEncodedEqual(x: A, y: A): Boolean =
          codec.isEncodedEqual(x, y)

        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          if codec.isEncodedEqual(value, defaultValue) then
            (Seq(), Map())
          else
            (Seq(), Map(keyword -> codec.encode(value)))
          end if

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          state.kwargs.get(keyword) match {
            case Some(value) =>
              codec.decode(value) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Keyword(keyword, path)))
                case Right(a) => Right((a, state.copy(kwargs = state.kwargs.removed(keyword))))
              }

            case None => Right((defaultValue, state))
          }
      }

    def requiredKeywordProductCodec[A](keyword: String, codec: ESExprCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def isEncodedEqual(x: A, y: A): Boolean =
          codec.isEncodedEqual(x, y)

        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          (Seq(), Map(keyword -> codec.encode(value)))

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          state.kwargs
            .get(keyword)
            .toRight { ProductDecodeError(s"Required key $keyword was not provided", ProductErrorPath.Current) }
            .flatMap { value =>
              codec.decode(value) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Keyword(keyword, path)))
                case Right(a) => Right((a, state.copy(kwargs = state.kwargs.removed(keyword))))
              }
            }
      }

    def optionalPositionalProductCodec[A](optionalValueCodec: OptionalValueCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def isEncodedEqual(x: A, y: A): Boolean =
          optionalValueCodec.isEncodedEqual(x, y)

        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          optionalValueCodec.encodeOptional(value) match {
            case Some(expr) => (Seq(expr), Map())
            case _: None.type => (Seq(), Map())
          }

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          state.args match {
            case h +: t if optionalValueCodec.elementTags.contains(h.tag) =>
              optionalValueCodec.decodeOptional(Some(h)) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Positional(state.positionalIndex, path)))
                case Right(a) => Right((a, state.copy(args = t, positionalIndex = state.positionalIndex + 1)))
              }

            case _ =>
              optionalValueCodec.decodeOptional(None) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Positional(state.positionalIndex, path)))
                case Right(a) => Right((a, state))
              }
          }
      }

    def defaultPositionalProductCodec[A](codec: ESExprCodec[A], defaultValue: => A): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def isEncodedEqual(x: A, y: A): Boolean =
          codec.isEncodedEqual(x, y)

        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          if codec.isEncodedEqual(value, defaultValue) then
            (Seq(), Map())
          else
            (Seq(codec.encode(value)), Map())
          end if

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          state.args match {
            case h +: t if codec.tags.contains(h.tag) =>
              codec.decode(h) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Positional(state.positionalIndex, path)))
                case Right(a) => Right((a, state.copy(args = t, positionalIndex = state.positionalIndex + 1)))
              }

            case _ => Right((defaultValue, state))
          }
      }

    def requiredPositionalProductCodec[A](codec: ESExprCodec[A]): ESExprCodecProduct[A] =
      new ESExprCodecProduct[A] {
        override def isEncodedEqual(x: A, y: A): Boolean =
          codec.isEncodedEqual(x, y)

        override def encode(value: A): (Seq[ESExpr], Map[String, ESExpr]) =
          (Seq(codec.encode(value)), Map())

        override def decode(state: ProductDecodeState): Either[ProductDecodeError, (A, ProductDecodeState)] =
          state.args match {
            case h +: t =>
              codec.decode(h) match {
                case Left(DecodeError(message, path)) => Left(ProductDecodeError(message, ProductErrorPath.Positional(state.positionalIndex, path)))
                case Right(a) => Right((a, state.copy(args = t, positionalIndex = state.positionalIndex + 1)))
              }

            case _ => Left(ProductDecodeError("Not enough arguments were provided", ProductErrorPath.Current))
          }
      }

    
    def simpleEnumCodec[T](caseNames: Array[String], caseValues: Map[String, T])(using m: Mirror.SumOf[T]): ESExprCodec[T] =
      new ESExprCodec[T] {
        override lazy val tags: ESExprTagSet = ESExprTagSet.Cons(ESExprTag.Str, ESExprTagSet.Empty)

        override def isEncodedEqual(x: T, y: T): Boolean =
          m.ordinal(x) == m.ordinal(y)

        override def encode(value: T): ESExpr =
          ESExpr.Str(caseNames(m.ordinal(value)))

        override def decode(expr: ESExpr): Either[DecodeError, T] =
          expr match {
            case ESExpr.Str(s) =>
              caseValues.get(s).toRight { DecodeError(s"Invalid simple enum value: $s", ErrorPath.Current) }

            case _ =>
              Left(DecodeError("Expected a string for enum value", ErrorPath.Current))
          }
      }

    inline def derivedSumCreateCodec[T](codecMap: => Seq[ESExprCodec[? <: T]])(using m: Mirror.SumOf[T]): ESExprCodec[T] =
      ${ derivedSumMacro[T, m.MirroredElemTypes]('codecMap) }

    def derivedSumMacro[T: Type, SubTypes <: Tuple: Type](codecMap: Expr[Seq[ESExprCodec[? <: T]]])(using q: Quotes): Expr[ESExprCodec[T]] =
      try '{
        new ESExprCodec[T] {
          private lazy val codecs: Seq[ESExprCodec[? <: T]] = ${codecMap}

          override lazy val tags: ESExprTagSet =
            codecs.foldLeft[ESExprTagSet](ESExprTagSet.Empty)(_ | _.tags)

          override def isEncodedEqual(x: T, y: T): Boolean =
            ${
              val yValue = 'y
              MacroUtils.patternMatch[T, SubTypes, Boolean]('x)([U] => (xValue: Expr[U], uType: Type[U]) => {
                given Type[U] = uType
                '{
                  $yValue.asMatchable match {
                    case y2: U => ESExprCodec.derived[U](using summonInline[Mirror.Of[U]]).isEncodedEqual($xValue, y2)
                    case _ => false
                  }
                }
              })
            }

          override def encode(value: T): ESExpr =
            ${
              MacroUtils.patternMatch[T, SubTypes, ESExpr]('value)([U] => (uValue: Expr[U], uType: Type[U]) => {
                given Type[U] = uType
                '{
                  ESExprCodec.derived[U](using summonInline[Mirror.Of[U]]).encode($uValue)
                }
              })
            }

          override def decode(expr: ESExpr): Either[DecodeError, T] =
            val tag = ESExprTag.fromExpr(expr)

            codecs
              .find(codec => codec.tags.contains(tag))
              .toRight(DecodeError(s"Unexpected tag: $tag (valid tags: ${tags})", ErrorPath.Current))
              .flatMap { codec => codec.decode(expr) }
          end decode
        }
      }
      catch {
        case e: Throwable =>
          e.printStackTrace()
          throw e
      }
    

    def inlineValueCodec[T <: Product, Elem](elemCodec: ESExprCodec[Elem])(using m: Mirror.ProductOf[T] { type MirroredElemTypes = Elem *: EmptyTuple }): ESExprCodec[T] =
      new ESExprCodec[T] {
        override lazy val tags: ESExprTagSet = elemCodec.tags

        override def isEncodedEqual(x: T, y: T): Boolean =
          val (x0 *: EmptyTuple) = Tuple.fromProductTyped(x)
          val (y0 *: EmptyTuple) = Tuple.fromProductTyped(y)
          elemCodec.isEncodedEqual(x0, y0)
        end isEncodedEqual

        override def encode(value: T): ESExpr =
          val (elemValue *: EmptyTuple) = Tuple.fromProductTyped(value)
          elemCodec.encode(elemValue)
        end encode

        override def decode(expr: ESExpr): Either[DecodeError, T] =
          elemCodec.decode(expr).map(res =>
            m.fromTuple(res *: EmptyTuple)
          )
      }


    def toSExprName(name: String): String =
      name
        .split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])_(?=[0-9])").nn
        .map(_.nn.toLowerCase)
        .mkString("-")

    inline def toSExprNameInline(name: String): String =
      ${ toSExprNameMacro('name) }

    private def toSExprNameMacro(name: Expr[String])(using q: Quotes): Expr[String] =
      import q.reflect.*
      Expr(toSExprName(name.valueOrAbort))
    end toSExprNameMacro

    private inline def getConstructorInline[T]: String =
      ${ getConstructorMacro[T] }

    private def getConstructorMacro[T: Type](using q: Quotes): Expr[String] =
      import q.reflect.*
      Expr(getConstructor(
        q.reflect.TypeRepr.of[T] match {
          case tRef: TermRef => tRef.termSymbol
          case tRep => tRep.typeSymbol
        }
      ))
    end getConstructorMacro

    private[esexpr] def getConstructor(using q: Quotes)(t: q.reflect.Symbol): String =
      import q.reflect.*
      t.getAnnotation(TypeRepr.of[constructor].typeSymbol)
        .map(_.asExprOf[constructor] match {
          case '{ new esexpr.constructor($name) } => name.value.getOrElse {
            report.errorAndAbort("Could not get constructor name value")
          }
          case ctorAnn => report.errorAndAbort("Invalid constructor annotation: " + ctorAnn.show)
        })
        .getOrElse {
          toSExprName(t.name)
        }
    end getConstructor

    private inline def setContains(inline s: Set[String], inline value: String): Boolean =
      ${ setContainsMacro('s, 'value) }

    private def setContainsMacro(s: Expr[Set[String]], value: Expr[String])(using q: Quotes): Expr[Boolean] =
      import q.reflect.*
      Expr(s.valueOrAbort.contains(value.valueOrAbort))
    end setContainsMacro

    private inline def setShow(inline s: Set[String]): String =
      ${ setShowMacro('s) }

    private def setShowMacro(s: Expr[Set[String]])(using q: Quotes): Expr[String] =
      import q.reflect.*
      Expr(s.valueOrAbort.toString())
    end setShowMacro

    private inline def setNonEmpty(inline s: Set[String]): Boolean =
      ${ setNonEmptyMacro('s) }

    private def setNonEmptyMacro[A: Type](s: Expr[Set[String]])(using q: Quotes): Expr[Boolean] =
      import q.reflect.*
      Expr(s.valueOrAbort.nonEmpty)
    end setNonEmptyMacro

    private inline def setAdd(inline s: Set[String], inline value: String): Set[String] =
      ${ setAddMacro('s, 'value) }

    private def setAddMacro(s: Expr[Set[String]], value: Expr[String])(using q: Quotes): Expr[Set[String]] =
      import q.reflect.*
      Expr(s.valueOrAbort + value.valueOrAbort)
    end setAddMacro

  end CodecDerivation

}

