package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.KeywordMapping;
import dev.argon.esexpr.ESExpr;
import dev.argon.esexpr.generator.gen.RepeatedArguments;
import dev.argon.esexpr.generator.gen.RepeatedArguments_Codec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class RepeatedArgumentsTest extends TestBase {

	@Test
	public void manyArgsTest() throws Throwable {
		assertCodecMatch(
			RepeatedArguments_Codec.INSTANCE,
			new ESExpr.Constructor(
				"repeated-arguments",
				List.of(new ESExpr.Str("A"), new ESExpr.Str("B"), new ESExpr.Str("Z")),
				Map.of(
					"A", new ESExpr.Str("1"),
					"B", new ESExpr.Str("2"),
					"Z", new ESExpr.Str("3")
				)
			),
			new RepeatedArguments(
				List.of("A", "B", "Z"),
				new KeywordMapping<>(Map.of(
					"A", "1",
					"B", "2",
					"Z", "3"
				))
			)
		);

	}

}
