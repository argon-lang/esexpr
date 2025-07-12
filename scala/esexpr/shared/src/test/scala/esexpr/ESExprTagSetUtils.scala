package esexpr

import scala.compiletime.error
import scala.quoted.{Expr, Quotes}

object ESExprTagSetUtils {

  inline def not(inline b: Boolean): Boolean =
    inline if b then
    false
    else
      true

  inline def assert(inline condition: Boolean): Unit =
    inline if not(condition) then
      error("Assertion failed: ")

  inline def showValue(inline x: Any): String =
    ${ showValueMacro('x) }

  def showValueMacro(x: Expr[Any])(using Quotes): Expr[String] =
    Expr(x.show)
}
