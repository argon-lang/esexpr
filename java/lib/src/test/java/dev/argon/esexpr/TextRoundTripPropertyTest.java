package dev.argon.esexpr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.List;
import net.jqwik.api.*;
import org.eclipse.collections.api.factory.primitive.*;

class TextRoundTripPropertyTest {
	@Property
	void expressionRoundTrip(@ForAll("expressions") ESExpr expression) throws Exception {
		assertRoundTrip(List.of(expression), null);
	}

	@Property
	void streamRoundTrip(@ForAll("expressionStreams") List<ESExpr> expressions) throws Exception {
		assertRoundTrip(expressions, null);
	}

	@Property
	void indentedStreamRoundTrip(
		@ForAll("expressionStreams") List<ESExpr> expressions,
		@ForAll("indentations") String indentation
	) throws Exception {
		assertRoundTrip(expressions, indentation);
	}

	private void assertRoundTrip(List<ESExpr> expressions, @org.jspecify.annotations.Nullable String indentation)
		throws Exception {
		var output = new StringWriter();
		var writer = new ESExprTextWriter(output, indentation);
		for(var expression : expressions)
			writer.write(expression);
		var reader = new ESExprTextReader(new StringReader(output.toString()));
		assertEquals(expressions, reader.readAll().toList());
	}

	@Provide
	Arbitrary<List<ESExpr>> expressionStreams() {
		return expressions().list().ofMaxSize(12);
	}

	@Provide
	Arbitrary<String> indentations() {
		return Arbitraries.of("", " ", "  ", "    ", "\t", "\t\t");
	}

	@Provide
	Arbitrary<ESExpr> expressions() {
		// Bound depth and width to keep generated trees and shrunk failures small.
		return Arbitraries.recursive(
			this::scalars,
			children -> Arbitraries.oneOf(
				scalars(),
				Combinators
					.combine(names(), children.list().ofMaxSize(4), Arbitraries.maps(names(), children).ofMaxSize(4))
					.as(
						(name, args, kwargs) -> new ESExpr.Constructor(
							name, ImmutableList.copyOf(args), ImmutableMap.copyOf(kwargs)
						)
					)
			),
			3
		);
	}

	private Arbitrary<String> names() {
		// Include simple names, quoted identifiers, escapes and reserved constructors.
		return Arbitraries.oneOf(
			Arbitraries
				.of("", "list", "map", "set", "string-table", "field", "λ", "a-b", "a\'b", "a\\b", "\n", "\u0000"),
			strings()
		);
	}

	private Arbitrary<String> strings() {
		// Generate Unicode scalar values, including supplementary characters.
		return Arbitraries.integers().between(0, 0x10ffff).filter(cp -> cp < 0xd800 || cp > 0xdfff).list().ofMaxSize(40)
			.map(codePoints -> {
				var result = new StringBuilder();
				codePoints.forEach(result::appendCodePoint);
				return result.toString();
			});
	}

	private Arbitrary<ESExpr> scalars() {
		var limit = BigInteger.ONE.shiftLeft(256);
		return Arbitraries.oneOf(
			Arbitraries.of(false, true).map(ESExpr.Bool::new),
			Arbitraries.bigIntegers().between(limit.negate(), limit).map(ESExpr.Int::new),
			names().map(ESExpr.Str::new),
			Arbitraries.shorts().map(ESExpr.Float16::new),
			Arbitraries.integers().map(bits -> new ESExpr.Float32(Float.intBitsToFloat(bits))),
			Arbitraries.longs().map(bits -> new ESExpr.Float64(Double.longBitsToDouble(bits))),
			Arbitraries.bytes().array(byte[].class).ofMaxSize(40)
				.map(v -> new ESExpr.Array8(ByteLists.immutable.of(v))),
			Arbitraries.shorts().array(short[].class).ofMaxSize(40)
				.map(v -> new ESExpr.Array16(ShortLists.immutable.of(v))),
			Arbitraries.integers().array(int[].class).ofMaxSize(40)
				.map(v -> new ESExpr.Array32(IntLists.immutable.of(v))),
			Arbitraries.longs().array(long[].class).ofMaxSize(40)
				.map(v -> new ESExpr.Array64(LongLists.immutable.of(v))),
			Arbitraries.integers().between(0, 20)
				.flatMap(size -> Arbitraries.longs().array(long[].class).ofSize(size * 2))
				.map(v -> new ESExpr.Array128(LongLists.immutable.of(v))),
			Arbitraries.bigIntegers().between(BigInteger.ZERO, limit).map(ESExpr.Null::new)
		);
	}
}
