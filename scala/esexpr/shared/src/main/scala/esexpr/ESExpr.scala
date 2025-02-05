package esexpr

import zio.Chunk

enum ESExpr derives CanEqual {
  case Constructor(constructor: String, args: Seq[ESExpr], kwargs: Map[String, ESExpr])
  case Bool(b: Boolean)
  case Int(n: BigInt)
  case Str(s: String)
  case Binary(b: Chunk[Byte])
  case Float32(f: Float)
  case Float64(d: Double)
  case Null(level: BigInt)
}

object ESExpr extends ESExprObjectPlatformSpecific
