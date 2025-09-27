package esexpr

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.typedarray.{BigInt64Array, BigUint64Array, Int16Array, Int32Array, Int8Array, Uint16Array, Uint32Array, Uint8Array, byteArray2Int8Array, int16Array2ShortArray, int32Array2IntArray, int8Array2ByteArray, intArray2Int32Array, shortArray2Int16Array}
import esexpr.sjs.ESExpr as JSESExpr
import zio.{Chunk, ChunkBuilder}

trait ESExprObjectPlatformSpecific {

  private[esexpr] def compareFloat16(a: Float16, b: Float16): Boolean =
    js.Object.is(a, b)

  private[esexpr] def compareFloat16ToBits(a: Float16, b: Short): Boolean =
    a.isNaN && b == 0x7E00

  private[esexpr] def compareFloat(a: Float, b: Float): Boolean =
    js.Object.is(a, b)

  private[esexpr] def compareFloatToBits(a: Float, b: Int): Boolean =
    a.isNaN && b == 0x7FC00000

  private[esexpr] def compareDouble(a: Double, b: Double): Boolean =
    js.Object.is(a, b)

  private[esexpr] def compareDoubleToBits(a: Double, b: Long): Boolean =
    a.isNaN && b == 0x7FF8000000000000L

  def fromJS(expr: JSESExpr): ESExpr =
    js.typeOf(expr) match {
      case "boolean" => ESExpr.Bool(expr.asInstanceOf[Boolean])
      case "bigint" => ESExpr.Int(BigInt(expr.asInstanceOf[js.BigInt].toString))
      case "string" => ESExpr.Str(expr.asInstanceOf[String])
      case "number" => ESExpr.Float64(expr.asInstanceOf[Double])
      case "object" if expr.asInstanceOf[AnyRef | Null] eq null => ESExpr.Null(0)
      case "object" =>
        expr match {
          case expr: Uint8Array =>
            val signedArray = Int8Array(expr.buffer, expr.byteOffset, expr.length)
            ESExpr.Array8(Chunk.fromArray(int8Array2ByteArray(signedArray)))

          case expr: Uint16Array =>
            val signedArray = Int16Array(expr.buffer, expr.byteOffset, expr.length)
            ESExpr.Array16(Chunk.fromArray(int16Array2ShortArray(signedArray)))

          case expr: Uint32Array =>
            val signedArray = Int32Array(expr.buffer, expr.byteOffset, expr.length)
            ESExpr.Array32(Chunk.fromArray(int32Array2IntArray(signedArray)))

          case expr: BigUint64Array =>
            val signedArray = BigInt64Array(expr.buffer, expr.byteOffset, expr.length)
            ESExpr.Array64(Chunk.fromArray(signedArray.map(i => i.toString.toLong).toArray))

          case _ =>
            expr.asInstanceOf[js.Dictionary[String]]("type") match {
              case "constructor" =>
                val constructor = expr.asInstanceOf[JSESExpr.Constructor]
                ESExpr.Constructor(
                  constructor.name,
                  constructor.args.view.map(fromJS).toSeq,
                  constructor.kwargs.view.mapValues(fromJS).toMap,
                )

              case "float16" =>
                val f16 = expr.asInstanceOf[JSESExpr.Float16]
                ESExpr.Float16(f16.value)

              case "float16-nan" =>
                val f16 = expr.asInstanceOf[JSESExpr.Float16NaN]
                ESExpr.Float16NaN(f16.bits.toShort)

              case "float32" =>
                val f32 = expr.asInstanceOf[JSESExpr.Float32]
                ESExpr.Float32(f32.value)

              case "float32-nan" =>
                val f32 = expr.asInstanceOf[JSESExpr.Float32NaN]
                ESExpr.Float32NaN(f32.bits.toInt)

              case "float64-nan" =>
                val f64 = expr.asInstanceOf[JSESExpr.Float64NaN]
                ESExpr.Float64NaN(BigInt(f64.bits.toString).toLong)

              case "array128" =>
                val array128 = expr.asInstanceOf[JSESExpr.Array128]
                val cb = ChunkBuilder.make[Long](array128.value.length / 8)
                for i <- 0 until (array128.value.length / 8) do
                  var l = 0
                  for j <- 0 until 8 do
                    l |= array128.value(i * 8 + j)
                  cb.addOne(l)
                end for
                ESExpr.Array128(cb.result())

              case "null" =>
                val nestedNull = expr.asInstanceOf[JSESExpr.NestedNull]
                ESExpr.Null(BigInt(nestedNull.level.toString))

              case _ => throw new MatchError(expr)
            }
        }

      case _ => throw new MatchError(expr)
    }

  def toJS(expr: ESExpr): JSESExpr =
    expr match {
      case expr: ESExpr.Constructor =>
        new JSESExpr.Constructor {
          override val `type`: "constructor" = "constructor"
          override val name: String = expr.constructor
          override val args: js.Array[JSESExpr] = expr.args.map(toJS).toJSArray
          override val kwargs: js.Map[String, JSESExpr] = js.Map(expr.kwargs.view.mapValues(toJS).toSeq*)
        }

      case ESExpr.Bool(b) => b
      case ESExpr.Int(n) => js.BigInt(n.toString)
      case ESExpr.Str(s) => s

      case ESExpr.Float16(f) =>
        new JSESExpr.Float16 {
          override val `type`: "float16" = "float16"
          override val value: Float16 = f
        }

      case ESExpr.Float16NaN(fbits) =>
        new JSESExpr.Float16NaN {
          override val `type`: "float16-nan" = "float16-nan"
          override val bits: Int = java.lang.Short.toUnsignedInt(fbits)
        }
        
      case ESExpr.Float32(f) =>
        new JSESExpr.Float32 {
          override val `type`: "float32" = "float32"
          override val value: Float = f
        }

      case ESExpr.Float32NaN(fbits) =>
        new JSESExpr.Float32NaN {
          override val `type`: "float32-nan" = "float32-nan"
          override val bits: Double = java.lang.Integer.toUnsignedLong(fbits).toDouble
        }

      case ESExpr.Float64(d) => d

      case ESExpr.Float64NaN(fbits) =>
        new JSESExpr.Float64NaN {
          override val `type`: "float64-nan" = "float64-nan"
          override val bits: js.BigInt = js.BigInt.asUintN(64, js.BigInt(fbits.toString))
        }

      case ESExpr.Array8(b) =>
        val signedArray = byteArray2Int8Array(b.toArray)
        Uint8Array(signedArray.buffer, signedArray.byteOffset, signedArray.length)

      case ESExpr.Array16(b) =>
        val signedArray = shortArray2Int16Array(b.toArray)
        Uint16Array(signedArray.buffer, signedArray.byteOffset, signedArray.length)

      case ESExpr.Array32(b) =>
        val signedArray = intArray2Int32Array(b.toArray)
        Uint32Array(signedArray.buffer, signedArray.byteOffset, signedArray.length)

      case ESExpr.Array64(b) =>
        val array = BigUint64Array(b.length)
        for i <- b.indices do
          array(i) = js.BigInt(b.long(i).toString)
        array

      case ESExpr.Array128(b) =>
        val array = Uint8Array(b.length * 8)
        for i <- b.indices do
          var l = b.long(i)
          for j <- 0 until 8 do
            array(i * 8 + j) = (l & 0xFFL).toByte
            l = l >>> 8
          end for
        end for

        new JSESExpr.Array128 {
          override val `type`: "array128" = "array128"
          override val value: Uint8Array = array
        }

      case ESExpr.Null(0) => null
      case ESExpr.Null(levelValue) =>
        new JSESExpr.NestedNull {
          override val `type`: "null" = "null"
          override val level: js.BigInt = js.BigInt(levelValue.toString)
        }
    }

}
