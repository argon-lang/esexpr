package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record MyGenericRecord<T>(
	T value
) {}

