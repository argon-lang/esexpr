package esexpr.sjs

import scala.scalajs.js
import scala.scalajs.js.typedarray.{BigUint64Array, Uint16Array, Uint32Array, Uint8Array}
import esexpr.ESExpr as SESExpr

type ESExpr = ESExpr.Constructor
            | Boolean
            | js.BigInt
            | String
            | ESExpr.Float16
            | ESExpr.Float16NaN
            | ESExpr.Float32
            | ESExpr.Float32NaN
            | Double
            | ESExpr.Float64NaN
            | Uint8Array
            | Uint16Array
            | Uint32Array
            | BigUint64Array
            | ESExpr.Array128
            | Null
            | ESExpr.NestedNull

object ESExpr {
  trait Constructor extends js.Object {
    val `type`: "constructor"
    val name: String
    val args: js.Array[ESExpr]
    val kwargs: js.Map[String, ESExpr]
  }

  trait Float16 extends js.Object {
    val `type`: "float16"
    val value: esexpr.Float16
  }

  trait Float16NaN extends js.Object {
    val `type`: "float16-nan"
    val bits: Int
  }

  trait Float32 extends js.Object {
    val `type`: "float32"
    val value: Float
  }
  
  trait Float32NaN extends js.Object {
    val `type`: "float32-nan"
    val bits: Double
  }
  
  trait Float64NaN extends js.Object {
    val `type`: "float64-nan"
    val bits: js.BigInt
  }
  
  trait Array128 extends js.Object {
    val `type`: "array128"
    val value: Uint8Array
  }
  
  trait NestedNull extends js.Object {
    val `type`: "null"
    val level: js.BigInt
  }
}


