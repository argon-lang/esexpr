package esexpr

import ESExprCodec.DecodeError

import cats.*
import cats.implicits.given

trait OptionalValueCodec[A] {
  def encodeOptional(value: A): Option[ESExpr]
  def decodeOptional(expr: Option[ESExpr]): Either[DecodeError, A]
}

object OptionalValueCodec {
  given [A: ESExprCodec]: OptionalValueCodec[Option[A]] with
      def encodeOptional(value: Option[A]): Option[ESExpr] =
        value.map(summon[ESExprCodec[A]].encode)

      def decodeOptional(expr: Option[ESExpr]): Either[DecodeError, Option[A]] =
        expr.traverse(summon[ESExprCodec[A]].decode)
  end given
}
