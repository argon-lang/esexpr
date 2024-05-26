package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public sealed interface InlineValueEnum {
	@InlineValue
	record A(int a) implements InlineValueEnum {}
	record B(float b) implements InlineValueEnum {}
}

