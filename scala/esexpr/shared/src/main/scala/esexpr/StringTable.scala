package esexpr

final case class StringTable(
  @vararg
  values: Seq[String],
) derives ESExprCodec

object StringTable extends StringTableObjectPlatformSpecific
