package dev.argon.esexpr.generator.gen;

import java.math.BigInteger;

import dev.argon.esexpr.*;
import org.eclipse.collections.api.list.primitive.ImmutableByteList;

@ESExprCodecGen
public record KnownReferenceFields(
	String a,
	BigInteger b,
	UnsignedBigInteger b2,
	ImmutableByteList c
) {

	@TypeClassInstance
	public static ESExprCodec<KnownReferenceFields> codec() {
		return KnownReferenceFields_CodecImpl.INSTANCE;
	}

}

