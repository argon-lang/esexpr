package esexpr

import ESExprCodec.DecodeError

import cats.*
import cats.implicits.given

trait DictCodec[A] {
  def encodeDict(value: A): Map[String, ESExpr]
  def decodeDict(exprs: Map[String, ESExpr]): Either[(String, DecodeError), A]
}

object DictCodec {
  given [A: ESExprCodec]: DictCodec[Map[String, A]] with
    def encodeDict(value: Map[String, A]): Map[String, ESExpr] =
      value.view.mapValues(summon[ESExprCodec[A]].encode).toMap

    def decodeDict(exprs: Map[String, ESExpr]): Either[(String, DecodeError), Map[String, A]] =
      exprs.toSeq
        .traverse { (k, v) =>
          summon[ESExprCodec[A]].decode(v)
            .map { a => k -> a }
            .left.map { e => (k, e) }
        }
        .map { _.toMap }
  end given
}
