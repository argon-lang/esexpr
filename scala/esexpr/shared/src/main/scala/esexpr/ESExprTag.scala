package esexpr

import scala.compiletime.error
import scala.quoted.{Expr, FromExpr, Quotes, ToExpr}

sealed trait ESExprTag derives CanEqual

object ESExprTag {
  sealed trait ScalarTag extends ESExprTag

  final case class Constructor(name: String) extends ESExprTag
  case object Bool extends ScalarTag
  case object Str extends ScalarTag
  case object Int extends ScalarTag
  case object Float16 extends ScalarTag
  case object Float32 extends ScalarTag
  case object Float64 extends ScalarTag
  case object Null extends ScalarTag
  case object Array8 extends ScalarTag
  case object Array16 extends ScalarTag
  case object Array32 extends ScalarTag
  case object Array64 extends ScalarTag
  case object Array128 extends ScalarTag
  
  def fromExpr(expr: ESExpr): ESExprTag =
    expr match {
      case ESExpr.Constructor(constructor, _, _) => Constructor(constructor)
      case ESExpr.Bool(_) => Bool
      case ESExpr.Int(_) => Int
      case ESExpr.Str(_) => Str
      case ESExpr.Float16(_) | ESExpr.Float16NaN(_) => Float16
      case ESExpr.Float32(_) | ESExpr.Float32NaN(_) => Float32
      case ESExpr.Float64(_) | ESExpr.Float64NaN(_) => Float64
      case ESExpr.Null(_) => Null
      case ESExpr.Array8(_) => Array8
      case ESExpr.Array16(_) => Array16
      case ESExpr.Array32(_) => Array32
      case ESExpr.Array64(_) => Array64
      case ESExpr.Array128(_) => Array128
    }

  inline def tagEqualsInline(inline a: ESExprTag, inline b: ESExprTag): Boolean =
    ${ tagEqualsInlineMacro('a, 'b) }

  given FromExpr[ESExprTag]:
    override def unapply(x: Expr[ESExprTag])(using Quotes): Option[ESExprTag] =
      x match {
        case '{ Constructor($name) } => name.value.map(ESExprTag.Constructor.apply)
        case '{ Bool } => Some(ESExprTag.Bool)
        case '{ Int } => Some(ESExprTag.Int)
        case '{ Str } => Some(ESExprTag.Str)
        case '{ Float32 } => Some(ESExprTag.Float32)
        case '{ Float64 } => Some(ESExprTag.Float64)
        case '{ Array8 } => Some(ESExprTag.Array8)
        case '{ Array16 } => Some(ESExprTag.Array16)
        case '{ Array32 } => Some(ESExprTag.Array32)
        case '{ Array64 } => Some(ESExprTag.Array64)
        case '{ Array128 } => Some(ESExprTag.Array128)
        case '{ Null } => Some(ESExprTag.Null)
        case _ => None
      }
  end given

  given ToExpr[ESExprTag]:
    override def apply(x: ESExprTag)(using Quotes): Expr[ESExprTag] =
      x match {
        case Constructor(name) => '{ Constructor(${ Expr(name) }) }
        case Bool => '{ Bool }
        case Int => '{ Int }
        case Str => '{ Str }
        case Float16 => '{ Float16 }
        case Float32 => '{ Float32 }
        case Float64 => '{ Float64 }
        case Null => '{ Null }
        case Array8 => '{ Array8 }
        case Array16 => '{ Array16 }
        case Array32 => '{ Array32 }
        case Array64 => '{ Array64 }
        case Array128 => '{ Array128 }
      }
  end given


  private def tagEqualsInlineMacro(a: Expr[ESExprTag], b: Expr[ESExprTag])(using Quotes): Expr[Boolean] =

    (a.value, b.value) match
      case (Some(x), Some(y)) => Expr(x == y)
      case (None, _) =>
        val aStr = Expr(a.show)
        '{ error("Unable to determine ESExprTag value: " + $aStr) }
      case (_, None) =>
        val bStr = Expr(b.show)
        '{ error("Unable to determine ESExprTag value: " + $bStr) }
    end match
}
