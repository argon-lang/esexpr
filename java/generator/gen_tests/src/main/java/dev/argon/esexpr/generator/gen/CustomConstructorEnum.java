package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public sealed interface CustomConstructorEnum {

	@Constructor("my-ctor-name")
	record CustomNameCase(int a) implements CustomConstructorEnum {}

	record NormalNameCase(float b) implements CustomConstructorEnum {}

}

