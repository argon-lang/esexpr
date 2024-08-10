package esexpr

@constructor("dict")
final case class Dictionary[+A](@dict dict: Map[String, A]) derives CanEqual, ESExprCodec
