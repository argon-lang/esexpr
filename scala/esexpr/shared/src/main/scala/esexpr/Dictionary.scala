package esexpr

@constructor("dict")
final case class Dictionary[+A](@dict dict: Map[String, A]) derives CanEqual, ESExprCodec

object Dictionary {

  given [A: ESExprCodec] => DictCodec[Dictionary[A]]:
    override type ElementType = A

    override def isEncodedEqual(x: Dictionary[A], y: Dictionary[A]): Boolean =
      x.dict.size == y.dict.size && x.dict.forall { (k, v1) =>
        y.dict.get(k).fold(false)(v2 => summon[ESExprCodec[A]].isEncodedEqual(v1, v2))
      }

    override def encodeDict(value: Dictionary[A]): Map[String, ESExpr] =
      summon[DictCodec[Map[String, A]]].encodeDict(value.dict)

    override def decodeDict(exprs: Map[String, ESExpr]): Either[(String, ESExprCodec.DecodeError), Dictionary[A]] =
      summon[DictCodec[Map[String, A]]].decodeDict(exprs)
        .map(Dictionary.apply)
  end given

}
