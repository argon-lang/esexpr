package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.ESExpr;
import dev.argon.esexpr.ESExprTag;
import dev.argon.esexpr.generator.gen.InlineValueEnum;
import dev.argon.esexpr.generator.gen.InlineValueEnum_Codec;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InlineValueTests extends TestBase {

	@Test
	public void testInlineValue() throws Throwable {
		assertEquals(Set.of(new ESExprTag.Int(), new ESExprTag.Constructor("b")), InlineValueEnum_Codec.INSTANCE.tags());
		assertCodecMatch(
			InlineValueEnum_Codec.INSTANCE,
			new ESExpr.Int(BigInteger.ZERO),
			new InlineValueEnum.A(0)
		);
		assertCodecMatch(
			InlineValueEnum_Codec.INSTANCE,
			new ESExpr.Constructor(
				"b",
				List.of(new ESExpr.Float32(0.0f)),
				Map.of()
			),
			new InlineValueEnum.B(0.0f)
		);
	}

}
