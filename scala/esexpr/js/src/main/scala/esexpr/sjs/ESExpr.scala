package esexpr.sjs

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

import esexpr.ESExpr as SESExpr

type ESExpr = ESExpr.Constructor
            | Boolean
            | js.BigInt
            | String
            | Uint8Array
            | ESExpr.Float32
            | Double
            | Null
            | ESExpr.NestedNull

object ESExpr {
    trait Constructor extends js.Object {
        val `type`: "constructor"
        val name: String
        val args: js.Array[ESExpr]
        val kwargs: js.Map[String, ESExpr]
    }

    trait Float32 extends js.Object {
        val `type`: "float32"
        val value: Float
    }
    
    trait NestedNull extends js.Object {
      val `type`: "null"
      val level: js.BigInt
    }
}


