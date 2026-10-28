package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.DecodeException;
import dev.argon.esexpr.ESExpr;
import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.generator.gen.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;

public class GeneratedOutputTests extends TestBase {
	@Test
	public void boundedGenericRecords() throws DecodeException {
		var expr = new ESExpr.Int(BigInteger.valueOf(42));
		assertCodecMatch(
			BoundedRecord.codec(ESExprCodec.signedIntegerCodec()),
			new ESExpr.Constructor("bounded-record", ImmutableList.of(expr), ImmutableMap.of()),
			new BoundedRecord<>(42)
		);
		assertCodecMatch(
			RecursiveBoundRecord.codec(ESExprCodec.signedIntegerCodec()),
			new ESExpr.Constructor("recursive-bound-record", ImmutableList.of(expr), ImmutableMap.of()),
			new RecursiveBoundRecord<>(42)
		);
		assertCodecMatch(
			WildcardBoundRecord.codec(ESExprCodec.signedIntegerCodec()),
			new ESExpr.Constructor("wildcard-bound-record", ImmutableList.of(expr), ImmutableMap.of()),
			new WildcardBoundRecord<>(42)
		);

		assertCodecMatch(
			DependentBoundRecord.codec(ESExprCodec.signedIntegerCodec(), ESExprCodec.signedIntegerCodec()),
			new ESExpr.Constructor("dependent-bound-record", ImmutableList.of(expr, expr), ImmutableMap.of()),
			new DependentBoundRecord<>(42, 42)
		);
	}

	@Test
	public void genericDefaultsAndOptionalKeywords() throws DecodeException {
		var codec = GenericDefaults.codec(ESExprCodec.stringCodec());
		var nested = new ESExpr.Constructor(
			"list",
			ImmutableList.of(new ESExpr.Constructor("list", ImmutableList.of(new ESExpr.Str("a")), ImmutableMap.of())),
			ImmutableMap.of()
		);
		assertCodecMatch(
			codec,
			new ESExpr.Constructor("generic-defaults", ImmutableList.of(nested), ImmutableMap.of()),
			new GenericDefaults<>(ImmutableList.of(ImmutableList.of("a")), ImmutableList.of(), Optional.empty())
		);
		var defaults = new ESExpr.Constructor("list", ImmutableList.of(new ESExpr.Str("b")), ImmutableMap.of());
		assertCodecMatch(
			codec,
			new ESExpr.Constructor(
				"generic-defaults",
				ImmutableList.of(nested),
				ImmutableMap.of("defaulted", defaults, "optional", new ESExpr.Str("c"))
			),
			new GenericDefaults<>(ImmutableList.of(ImmutableList.of("a")), ImmutableList.of("b"), Optional.of("c"))
		);
	}

	@Test
	public void genericInlineAndNestedCollectionCases() throws DecodeException {
		var codec = GenericInline.codec(ESExprCodec.stringCodec());
		assertCodecMatch(codec, new ESExpr.Str("text"), new GenericInline.Text<>("text"));
		var values = new ESExpr.Constructor("list", ImmutableList.of(new ESExpr.Str("a")), ImmutableMap.of());
		assertCodecMatch(
			codec,
			new ESExpr.Constructor("values", ImmutableList.of(values), ImmutableMap.of()),
			new GenericInline.Values<>(ImmutableList.of("a"))
		);
	}

	@Test
	public void enumMaskLiteralBoundaries() throws DecodeException {
		assertCodecMatch(
			IntMaxMask.codec(),
			new ESExpr.Int(BigInteger.valueOf(Integer.MAX_VALUE)),
			new IntMaxMask(IntMaxMask.Bits.VALUE)
		);
		assertCodecMatch(
			AboveIntMask.codec(),
			new ESExpr.Int(BigInteger.ONE.shiftLeft(31)),
			new AboveIntMask(AboveIntMask.Bits.VALUE)
		);
		assertCodecMatch(
			LongMaxMask.codec(),
			new ESExpr.Int(BigInteger.valueOf(Long.MAX_VALUE)),
			new LongMaxMask(LongMaxMask.Bits.VALUE)
		);
		assertCodecMatch(
			AboveLongMask.codec(),
			new ESExpr.Int(BigInteger.ONE.shiftLeft(63)),
			new AboveLongMask(AboveLongMask.Bits.VALUE)
		);
		assertCodecMatch(
			AboveLongMask.codec(),
			new ESExpr.Int(BigInteger.ZERO),
			new AboveLongMask(AboveLongMask.Bits.ZERO)
		);
	}
}
