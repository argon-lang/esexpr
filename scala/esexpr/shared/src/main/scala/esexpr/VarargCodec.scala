package esexpr

import ESExprCodec.DecodeError

import cats.*
import cats.implicits.given

trait VarargCodec[A] {
  def encodeVararg(value: A): Seq[ESExpr]
  def decodeVararg(exprs: Seq[ESExpr]): Either[(Int, DecodeError), A]
}

object VarargCodec {
  given [A: ESExprCodec]: VarargCodec[Seq[A]] with
    def encodeVararg(value: Seq[A]): Seq[ESExpr] =
      value.map(summon[ESExprCodec[A]].encode)

    def decodeVararg(exprs: Seq[ESExpr]): Either[(Int, DecodeError), Seq[A]] =
      exprs.zipWithIndex.traverse { (a, i) =>
        summon[ESExprCodec[A]].decode(a)
          .left.map { e => (i, e) }
      }
  end given
}
