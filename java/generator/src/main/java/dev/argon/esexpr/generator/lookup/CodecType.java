package dev.argon.esexpr.generator.lookup;

public enum CodecType {
	VALUE("dev.argon.esexpr.ESExprCodec"),
	OPTIONAL_VALUE("dev.argon.esexpr.OptionalValueCodec"),
	VARARG("dev.argon.esexpr.VarargCodec"),
	DICT("dev.argon.esexpr.DictCodec"),
	;

	CodecType(String codecClass) {
		this.codecClass = codecClass;
	}

	private final String codecClass;

	public String codecClass() {
		return codecClass;
	}
}
