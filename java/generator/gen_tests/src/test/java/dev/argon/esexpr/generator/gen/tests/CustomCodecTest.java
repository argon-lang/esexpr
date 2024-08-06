package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.ESExpr;
import dev.argon.esexpr.generator.gen.CustomCodecRecord;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public class CustomCodecTest extends TestBase {

	@Test
	public void manyArgsTest() throws Throwable {
		assertCodecMatch(
			CustomCodecRecord.codec(),
			new ESExpr.Constructor(
				"custom-codec-record",
				List.of(
					new ESExpr.Int(BigInteger.TEN),
					new ESExpr.Int(BigInteger.TWO),
					new ESExpr.Constructor(
						"list",
						List.of(
							new ESExpr.Str("C"),
							new ESExpr.Str("B"),
							new ESExpr.Str("A")
						),
						Map.of()
					)
				),
				Map.of()
			),
			new CustomCodecRecord(
				5,
				List.of("b", "b"),
				List.of("A", "B", "C")
			)
		);
	}

}
