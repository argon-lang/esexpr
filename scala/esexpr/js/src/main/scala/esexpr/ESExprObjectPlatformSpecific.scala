package esexpr

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.typedarray.{Uint8Array, Int8Array, byteArray2Int8Array, int8Array2ByteArray}

import esexpr.sjs.ESExpr as JSESExpr

trait ESExprObjectPlatformSpecific {

  def fromJS(expr: JSESExpr): ESExpr =
    js.typeOf(expr) match {
      case "boolean" => ESExpr.Bool(expr.asInstanceOf[Boolean])
      case "bigint" => ESExpr.Int(BigInt(expr.asInstanceOf[js.BigInt].toString))
      case "string" => ESExpr.Str(expr.asInstanceOf[String])
      case "number" => ESExpr.Float64(expr.asInstanceOf[Double])
      case "object" if expr.asInstanceOf[AnyRef | Null] eq null => ESExpr.Null
      case "object" =>
        expr match {
          case expr: Uint8Array =>
            val signedArray = Int8Array(expr.buffer, expr.byteOffset, expr.length)
            ESExpr.Binary(IArray(int8Array2ByteArray(signedArray)*))

          case _ =>
            expr.asInstanceOf[js.Dictionary[String]]("type") match {
              case "constructor" =>
                val constructor = expr.asInstanceOf[JSESExpr.Constructor]
                ESExpr.Constructor(
                  constructor.name,
                  constructor.args.view.map(fromJS).toSeq,
                  constructor.kwargs.view.mapValues(fromJS).toMap,
                )

              case "float32" =>
                val f32 = expr.asInstanceOf[JSESExpr.Float32]
                ESExpr.Float32(f32.value)

              case _ => throw new MatchError(expr)
            }
        }

      case _ => throw new MatchError(expr)
    }

  def toJS(expr: ESExpr): JSESExpr =
    expr match {
      case expr: ESExpr.Constructor =>
        new JSESExpr.Constructor {
          val `type`: "constructor" = "constructor"
          val name: String = expr.constructor
          val args: js.Array[JSESExpr] = expr.args.map(toJS).toJSArray
          val kwargs: js.Map[String, JSESExpr] = js.Map(expr.kwargs.view.mapValues(toJS).toSeq*)
        }

      case ESExpr.Bool(b) => b
      case ESExpr.Int(n) => js.BigInt(n.toString)
      case ESExpr.Str(s) => s
      case ESExpr.Binary(b) =>
        val signedArray = byteArray2Int8Array(IArray.genericWrapArray(b).toArray)
        Uint8Array(signedArray.buffer, signedArray.byteOffset, signedArray.length)
        
      case ESExpr.Float32(f) =>
        new JSESExpr.Float32 {
          val `type`: "float32" = "float32"
          val value: Float = f
        }

      case ESExpr.Float64(d) => d
      case ESExpr.Null => null
    }

}
