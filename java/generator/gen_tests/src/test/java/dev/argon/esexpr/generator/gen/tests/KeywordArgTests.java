package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.ESExpr;
import dev.argon.esexpr.ESExprTag;
import dev.argon.esexpr.DecodeException;
import dev.argon.esexpr.generator.gen.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


public class KeywordArgTests extends TestBase {
	@Test
	public void optionalFieldsPresent() throws Throwable {
		assertCodecMatch(
			KeywordArguments_Codec.INSTANCE,
			new ESExpr.Constructor(
				"keyword-arguments",
				List.of(),
				Map.of(
					"a", new ESExpr.Bool(false),
					"b2", new ESExpr.Bool(false),
					"c2", new ESExpr.Bool(false),
					"d", new ESExpr.Bool(false),
					"e", new ESExpr.Bool(false),
					"f", new ESExpr.Bool(false)
				)
			),
			new KeywordArguments(false, false, Optional.of(false), Optional.of(false), false, Optional.of(false))
		);
	}

	@Test
	public void optionalFieldsEmpty() throws Throwable {
		assertCodecMatch(
			KeywordArguments_Codec.INSTANCE,
			new ESExpr.Constructor(
				"keyword-arguments",
				List.of(),
				Map.of(
					"a", new ESExpr.Bool(false),
					"b2", new ESExpr.Bool(false),
					"f", new ESExpr.Null()
				)
			),
			new KeywordArguments(false, false, Optional.empty(), Optional.empty(), true, Optional.empty())
		);
	}

	@Test
	public void missingRequiredOptional() throws Throwable {
		assertThrows(
			DecodeException.class,
			() -> KeywordArguments_Codec.INSTANCE.decode(
				new ESExpr.Constructor(
					"keyword-arguments",
					List.of(),
					Map.of(
						"a", new ESExpr.Bool(false),
						"b2", new ESExpr.Bool(false)
					)
				)	
			)
		);
	}
}
