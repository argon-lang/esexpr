package esexpr

import ESExprCodec.DecodeError

import cats.*
import cats.implicits.given

trait OptionalValueCodec[A] {
  type ElementType
  def elementTags: ESExprTagSet
  def isEncodedEqual(x: A, y: A): Boolean
  def encodeOptional(value: A): Option[ESExpr]
  def decodeOptional(expr: Option[ESExpr]): Either[DecodeError, A]
}

object OptionalValueCodec {
  final class OptionOptionalValueCodec[A: ESExprCodec] extends OptionalValueCodec[Option[A]] {
    override type ElementType = A

    override def elementTags: ESExprTagSet = summon[ESExprCodec[A]].tags

    override def isEncodedEqual(x: Option[A], y: Option[A]): Boolean =
      (x, y) match {
        case (Some(x), Some(y)) => summon[ESExprCodec[A]].isEncodedEqual(x, y)
        case (None, None) => true
        case _ => false
      }

    def encodeOptional(value: Option[A]): Option[ESExpr] =
      value.map(summon[ESExprCodec[A]].encode)

    def decodeOptional(expr: Option[ESExpr]): Either[DecodeError, Option[A]] =
      expr.traverse(summon[ESExprCodec[A]].decode)
  }

  inline given [A: ESExprCodec] => OptionOptionalValueCodec[A] =
    OptionOptionalValueCodec[A]
}
