package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.ESExpr;
import dev.argon.esexpr.ESExprTag;
import dev.argon.esexpr.ESExprTagSet;
import dev.argon.esexpr.codecs.FloatCodec;
import dev.argon.esexpr.codecs.StringCodec;
import dev.argon.esexpr.generator.gen.GenericInlineValue;
import dev.argon.esexpr.generator.gen.InlineValueEnum;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InlineValueTests extends TestBase {

	@Test
	public void testInlineValue() throws Throwable {
		assertEquals(ESExprTagSet.of(ESExprTag.INT, new ESExprTag.Constructor("b")), InlineValueEnum.codec().tags());
		assertCodecMatch(
			InlineValueEnum.codec(),
			new ESExpr.Int(BigInteger.ZERO),
			new InlineValueEnum.A(0)
		);
		assertCodecMatch(
			InlineValueEnum.codec(),
			new ESExpr.Constructor(
				"b",
				List.of(new ESExpr.Float32(0.0f)),
				Map.of()
			),
			new InlineValueEnum.B(0.0f)
		);
	}

	@Test
	public void testGenericInlineValue() throws Throwable {
		assertCodecMatch(
			GenericInlineValue.codec(FloatCodec.INSTANCE),
			new ESExpr.Constructor(
				"wrapped-a",
				List.of(new ESExpr.Float32(4.5f)),
				Map.of()
			),
			new GenericInlineValue.WrappedA<>(4.5f)
		);
		assertCodecMatch(
			GenericInlineValue.codec(FloatCodec.INSTANCE),
			new ESExpr.Str("abc"),
			new GenericInlineValue.WrappedString<>("abc")
		);
	}

}
