package esexpr

sealed trait ESExprTag derives CanEqual

object ESExprTag {
  final case class Constructor(name: String) extends ESExprTag
  case object Bool extends ESExprTag
  case object Str extends ESExprTag
  case object Int extends ESExprTag
  case object Binary extends ESExprTag
  case object Float32 extends ESExprTag
  case object Float64 extends ESExprTag
  case object Null extends ESExprTag
  
  def fromExpr(expr: ESExpr): ESExprTag =
    expr match {
      case ESExpr.Constructor(constructor, _, _) => Constructor(constructor)
      case ESExpr.Bool(_) => Bool
      case ESExpr.Int(_) => Int
      case ESExpr.Str(_) => Str
      case ESExpr.Binary(_) => Binary
      case ESExpr.Float32(_) => Float32
      case ESExpr.Float64(_) => Float64
      case ESExpr.Null(_) => Null
    }
}
