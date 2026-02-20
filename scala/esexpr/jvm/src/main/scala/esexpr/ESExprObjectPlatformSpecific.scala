package esexpr

import dev.argon.esexpr.ESExpr as JESExpr
import org.eclipse.collections.api.factory.primitive.{ByteLists, IntLists, LongLists, ShortLists}
import com.google.common.collect.{ImmutableList as JImmutableList, ImmutableMap as JImmutableMap}

import scala.jdk.CollectionConverters.*
import zio.Chunk

trait ESExprObjectPlatformSpecific {

  private[esexpr] def compareFloat16(a: Float16, b: Float16): Boolean =
    Float16.float16ToRawShortBits(a) == Float16.float16ToRawShortBits(b)
    
  private[esexpr] def compareFloat16ToBits(a: Float16, b: Short): Boolean =
    Float16.float16ToRawShortBits(a) == b

  private[esexpr] def compareFloat(a: Float, b: Float): Boolean =
    java.lang.Float.floatToRawIntBits(a) == java.lang.Float.floatToRawIntBits(b)

  private[esexpr] def compareFloatToBits(a: Float, b: Int): Boolean =
    java.lang.Float.floatToRawIntBits(a) == b

  private[esexpr] def compareDouble(a: Double, b: Double): Boolean =
    java.lang.Double.doubleToRawLongBits(a) == java.lang.Double.doubleToRawLongBits(b)

  private[esexpr] def compareDoubleToBits(a: Double, b: Long): Boolean =
    java.lang.Double.doubleToRawLongBits(a) == b

  def fromJava(expr: JESExpr): ESExpr =
    expr match {
      case expr: JESExpr.Constructor =>
        ESExpr.Constructor(
          expr.constructor,
          expr.args.asScala.map(fromJava).toSeq,
          expr.kwargs.asScala.view.mapValues(fromJava).toMap
        )

      case expr: JESExpr.Bool => ESExpr.Bool(expr.b)
      case expr: JESExpr.Int => ESExpr.Int(expr.n)
      case expr: JESExpr.Str => ESExpr.Str(expr.s)
      case expr: JESExpr.Float16 => ESExpr.Float16(Float16.shortBitsToFloat16(expr.f))
      case expr: JESExpr.Float32 => ESExpr.Float32(expr.f)
      case expr: JESExpr.Float64 => ESExpr.Float64(expr.d)
      case expr: JESExpr.Null => ESExpr.Null(expr.level().nn)
      case expr: JESExpr.Array8 => ESExpr.Array8(Chunk.fromArray(expr.b.toArray()))
      case expr: JESExpr.Array16 => ESExpr.Array16(Chunk.fromArray(expr.b.toArray()))
      case expr: JESExpr.Array32 => ESExpr.Array32(Chunk.fromArray(expr.b.toArray()))
      case expr: JESExpr.Array64 => ESExpr.Array64(Chunk.fromArray(expr.b.toArray()))
      case expr: JESExpr.Array128 => ESExpr.Array128(Chunk.fromArray(expr.b.toArray()))
      case _ => throw new MatchError(expr)
    }

  def toJava(expr: ESExpr): JESExpr =
    expr match {
      case ESExpr.Constructor(constructor, args, kwargs) =>
        JESExpr.Constructor(
          constructor,
          JImmutableList.copyOf(args.map(toJava).asJava),
          JImmutableMap.copyOf(kwargs.view.mapValues(toJava).toMap.asJava)
        )

      case ESExpr.Bool(b) => JESExpr.Bool(b)
      case ESExpr.Int(n) => JESExpr.Int(n.bigInteger)
      case ESExpr.Str(s) => JESExpr.Str(s)
      case ESExpr.Float16(f) => JESExpr.Float16(Float16.float16ToRawShortBits(f))
      case ESExpr.Float16NaN(bits) => JESExpr.Float16(bits)
      case ESExpr.Float32(f) => JESExpr.Float32(f)
      case ESExpr.Float32NaN(bits) => JESExpr.Float32(java.lang.Float.intBitsToFloat(bits))
      case ESExpr.Float64(d) => JESExpr.Float64(d)
      case ESExpr.Float64NaN(bits) => JESExpr.Float64(java.lang.Double.longBitsToDouble(bits))
      case ESExpr.Null(level) => JESExpr.Null(level.bigInteger)
      case ESExpr.Array8(b) => JESExpr.Array8(ByteLists.immutable.of(b.toArray*))
      case ESExpr.Array16(b) => JESExpr.Array16(ShortLists.immutable.of(b.toArray*))
      case ESExpr.Array32(b) => JESExpr.Array32(IntLists.immutable.of(b.toArray*))
      case ESExpr.Array64(b) => JESExpr.Array64(LongLists.immutable.of(b.toArray*))
      case ESExpr.Array128(b) => JESExpr.Array128(LongLists.immutable.of(b.toArray*))
    }
    
}
