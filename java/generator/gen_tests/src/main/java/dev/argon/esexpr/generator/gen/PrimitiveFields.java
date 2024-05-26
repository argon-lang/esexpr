package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record PrimitiveFields(
	boolean a,
	byte b,
	@Unsigned byte b2,
	short c,
	@Unsigned short c2,
	int d,
	@Unsigned int d2,
	long e,
	@Unsigned long e2,
	float f,
	double g
) {}

