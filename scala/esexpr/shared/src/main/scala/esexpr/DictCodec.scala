package esexpr

import ESExprCodec.DecodeError

import cats.*
import cats.implicits.given

trait DictCodec[A] {
  type ElementType
  def isEncodedEqual(x: A, y: A): Boolean
  def encodeDict(value: A): Map[String, ESExpr]
  def decodeDict(exprs: Map[String, ESExpr]): Either[(String, DecodeError), A]
}

object DictCodec {
  final class MapDictCodec[A: ESExprCodec] extends DictCodec[Map[String, A]] {
    override type ElementType = A

    override def isEncodedEqual(x: Map[String, A], y: Map[String, A]): Boolean =
      x.size == y.size && x.forall { (k, v1) =>
        y.get(k).fold(false)(v2 => summon[ESExprCodec[A]].isEncodedEqual(v1, v2))
      }

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
  }

  given [A: ESExprCodec] => MapDictCodec[A] = MapDictCodec[A]
}
