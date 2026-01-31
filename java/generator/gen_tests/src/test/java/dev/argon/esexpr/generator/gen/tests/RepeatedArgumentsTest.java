package dev.argon.esexpr.generator.gen.tests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.argon.esexpr.KeywordMapping;
import dev.argon.esexpr.ESExpr;
import dev.argon.esexpr.generator.gen.RepeatedArguments;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class RepeatedArgumentsTest extends TestBase {

	@Test
	public void manyArgsTest() throws Throwable {
		assertCodecMatch(
			RepeatedArguments.codec(),
			new ESExpr.Constructor(
				"repeated-arguments",
				ImmutableList.of(new ESExpr.Str("A"), new ESExpr.Str("B"), new ESExpr.Str("Z")),
				ImmutableMap.of(
					"A", new ESExpr.Str("1"),
					"B", new ESExpr.Str("2"),
					"Z", new ESExpr.Str("3")
				)
			),
			new RepeatedArguments(
				ImmutableList.of("A", "B", "Z"),
				new KeywordMapping<>(ImmutableMap.of(
					"A", "1",
					"B", "2",
					"Z", "3"
				))
			)
		);
	}

}
