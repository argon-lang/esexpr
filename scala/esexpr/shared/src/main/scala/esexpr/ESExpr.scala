package esexpr

import zio.Chunk
import scala.compiletime.asMatchable

enum ESExpr derives CanEqual {
  case Constructor(constructor: String, args: Seq[ESExpr], kwargs: Map[String, ESExpr])
  case Bool(b: Boolean)
  case Int(n: BigInt)
  case Str(s: String)
  case Float16(f: esexpr.Float16)
  case Float16NaN(bits: Short)
  case Float32(f: Float)
  case Float32NaN(bits: scala.Int)
  case Float64(d: Double)
  case Float64NaN(bits: Long)
  case Array8(b: Chunk[Byte])
  case Array16(b: Chunk[Short])
  case Array32(b: Chunk[scala.Int])
  case Array64(b: Chunk[Long])
  case Array128(b: Chunk[Long])
  case Null(level: BigInt)

  def tag: ESExprTag = ESExprTag.fromExpr(this)

  override def equals(obj: Any): Boolean =
    obj.asMatchable match {
      case other: ESExpr => (this, other) match {
        case (Constructor(c1, args1, kwargs1), Constructor(c2, args2, kwargs2)) =>
          c1 == c2 && args1 == args2 && kwargs1 == kwargs2
        case (Bool(b1), Bool(b2)) => b1 == b2
        case (Int(n1), Int(n2)) => n1 == n2
        case (Str(s1), Str(s2)) => s1 == s2
        case (Float16(f1), Float16(f2)) => esexpr.Float16.float16ToRawShortBits(f1) == esexpr.Float16.float16ToRawShortBits(f2)
        case (Float16NaN(bits1), Float16NaN(bits2)) => bits1 == bits2
        case (Float32(f1), Float32(f2)) => ESExpr.compareFloat(f1, f2)
        case (Float32NaN(bits1), Float32NaN(bits2)) => bits1 == bits2
        case (Float64(d1), Float64(d2)) => ESExpr.compareDouble(d1, d2)
        case (Float64NaN(bits1), Float64NaN(bits2)) => bits1 == bits2
        case (Array8(b1), Array8(b2)) => b1 == b2
        case (Array16(b1), Array16(b2)) => b1 == b2
        case (Array32(b1), Array32(b2)) => b1 == b2
        case (Array64(b1), Array64(b2)) => b1 == b2
        case (Array128(b1), Array128(b2)) => b1 == b2
        case (Null(l1), Null(l2)) => l1 == l2
        case _ => false
      }
      case _ => false
    }
}

object ESExpr extends ESExprObjectPlatformSpecific {
  given ESExprCodec[ESExpr]:
    override lazy val tags: ESExprTagSet = ESExprTagSet.All

    override def isEncodedEqual(x: ESExpr, y: ESExpr): Boolean =
      x == y

    override def encode(value: ESExpr): ESExpr = value
    override def decode(expr: ESExpr): Either[ESExprCodec.DecodeError, ESExpr] = Right(expr)
  end given

  inline given ESExprTagSetProvider[ESExpr]:
    override inline def tags: ESExprTagSet = ESExprTagSet.All
  end given
}
