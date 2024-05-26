package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public sealed interface MyEnum {
	record MyCaseA(int a) implements MyEnum {}
	record MyCaseB(float b) implements MyEnum {}
}

