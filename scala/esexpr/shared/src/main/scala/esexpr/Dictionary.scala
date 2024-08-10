package esexpr

@constructor("dict")
final case class Dictionary[+A](@dict dict: Map[String, A]) derives CanEqual, ESExprCodec

object Dictionary {

  given [A: ESExprCodec]: DictCodec[Dictionary[A]] with {
    override def encodeDict(value: Dictionary[A]): Map[String, ESExpr] =
      summon[DictCodec[Map[String, A]]].encodeDict(value.dict)

    override def decodeDict(exprs: Map[String, ESExpr]): Either[(String, ESExprCodec.DecodeError), Dictionary[A]] =
      summon[DictCodec[Map[String, A]]].decodeDict(exprs)
        .map(Dictionary.apply)
  }

}
