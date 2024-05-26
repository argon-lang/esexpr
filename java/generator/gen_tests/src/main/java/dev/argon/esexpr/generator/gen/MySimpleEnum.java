package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public enum MySimpleEnum {
	TEST_NAME123,
	TEST_NAME_123,
	OTHER_NAME_HERE,

	@Constructor("my-custom-name")
	CUSTOM_NAME,
}

