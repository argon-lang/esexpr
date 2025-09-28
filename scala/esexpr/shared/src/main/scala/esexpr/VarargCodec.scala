package esexpr

import ESExprCodec.DecodeError

import cats.*
import cats.implicits.given

trait VarargCodec[A] {
  type ElementType
  def isEncodedEqual(x: A, y: A): Boolean
  def elementTags: ESExprTagSet
  def encodeVararg(value: A): Seq[ESExpr]
  def decodeVararg(exprs: Seq[ESExpr]): Either[(Int, DecodeError), A]
}

object VarargCodec {
  final class SeqVarargCodec[A: ESExprCodec] extends VarargCodec[Seq[A]] {
    override type ElementType = A

    override def elementTags: ESExprTagSet =
      summon[ESExprCodec[A]].tags

    override def isEncodedEqual(x: Seq[A], y: Seq[A]): Boolean =
      x.size == y.size && x.zip(y).forall(summon[ESExprCodec[A]].isEncodedEqual.tupled)

    def encodeVararg(value: Seq[A]): Seq[ESExpr] =
      value.map(summon[ESExprCodec[A]].encode)

    def decodeVararg(exprs: Seq[ESExpr]): Either[(Int, DecodeError), Seq[A]] =
      exprs.zipWithIndex.traverse { (a, i) =>
        summon[ESExprCodec[A]].decode(a)
          .left.map { e => (i, e) }
      }
  }

  inline given [A: ESExprCodec] => SeqVarargCodec[A] = SeqVarargCodec[A]
}
