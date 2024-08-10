package esexpr

import dev.argon.esexpr.ESExpr as JESExpr
import scala.jdk.CollectionConverters.*

trait ESExprObjectPlatformSpecific {
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
      case expr: JESExpr.Binary => ESExpr.Binary(IArray(expr.b.nn*))
      case expr: JESExpr.Float32 => ESExpr.Float32(expr.f)
      case expr: JESExpr.Float64 => ESExpr.Float64(expr.d)
      case expr: JESExpr.Null => ESExpr.Null
      case _ => throw new MatchError(expr)
    }

  def toJava(expr: ESExpr): JESExpr =
    expr match {
      case ESExpr.Constructor(constructor, args, kwargs) =>
        JESExpr.Constructor(
          constructor,
          args.map(toJava).asJava,
          kwargs.view.mapValues(toJava).toMap.asJava
        )

      case ESExpr.Bool(b) => JESExpr.Bool(b)
      case ESExpr.Int(n) => JESExpr.Int(n.bigInteger)
      case ESExpr.Str(s) => JESExpr.Str(s)
      case ESExpr.Binary(b) => JESExpr.Binary(IArray.genericWrapArray(b).toArray)
      case ESExpr.Float32(f) => JESExpr.Float32(f)
      case ESExpr.Float64(d) => JESExpr.Float64(d)
      case ESExpr.Null => JESExpr.Null()
    }
    
}
