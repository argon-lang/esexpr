package dev.argon.esexpr.generator.gen;

import java.math.BigInteger;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record KnownReferenceFields(
	String a,
	BigInteger b,
	@Unsigned BigInteger b2,
	byte[] c
) {}

