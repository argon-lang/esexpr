package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
@Constructor("my-ctor-name")
public record CustomConstructorRecord(
	boolean a
) {
	@TypeClassInstance
	public static ESExprCodec<CustomConstructorRecord> codec() {
		return CustomConstructorRecord_CodecImpl.INSTANCE;
	}
}

