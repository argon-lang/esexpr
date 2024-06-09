package esexpr

final case class WrappedCodec[TCodec[_], T](codec: TCodec[T])
