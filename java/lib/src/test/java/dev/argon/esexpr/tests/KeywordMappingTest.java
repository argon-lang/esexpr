package dev.argon.esexpr.tests;

import dev.argon.esexpr.*;

import static org.junit.jupiter.api.Assertions.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class KeywordMappingTest {
	@Test
	public void roundTripsAndEncodedEquality() throws DecodeException {
		var codec = KeywordMapping.codec(ESExprCodec.floatCodec());
		var dict = KeywordMapping.dictCodec(ESExprCodec.floatCodec());
		var values = List.of(
			new KeywordMapping<Float>(ImmutableMap.of()),
			new KeywordMapping<>(ImmutableMap.of("a", 0.0f)),
			new KeywordMapping<>(ImmutableMap.of("a", -0.0f)),
			new KeywordMapping<>(ImmutableMap.of("b", 0.0f))
		);
		assertEquals(ESExprTagSet.of(new ESExprTag.Constructor("dict")), codec.tags());
		for(int i = 0; i < values.size(); i++) {
			var value = values.get(i);
			var builder = ImmutableMap.<String, ESExpr>builder();
			dict.encodeDict(value, builder);
			var encoded = builder.build();
			assertEquals(new ESExpr.Constructor("dict", ImmutableList.of(), encoded), codec.encode(value));
			assertEquals(value, codec.decode(codec.encode(value)));
			assertEquals(
				value,
				dict.decodeDict(encoded, key -> new ESExprCodec.FailurePath.Current().append("dict", key))
			);
			for(int j = 0; j < values.size(); j++) {
				assertEquals(i == j, codec.isEncodedEqual(value, values.get(j)));
				assertEquals(i == j, dict.isEncodedEqual(value, values.get(j)));
			}
		}
	}

	@Test
	public void rejectsInvalidStructureAndReportsKeywordPath() {
		var codec = KeywordMapping.codec(ESExprCodec.stringCodec());
		var path = new ESExprCodec.FailurePath.Current().append("outer", 1);
		assertEquals(path, assertThrows(DecodeException.class, () -> codec.decode(new ESExpr.Bool(true), path)).path);
		assertEquals(
			path,
			assertThrows(
				DecodeException.class,
				() -> codec.decode(new ESExpr.Constructor("wrong", ImmutableList.of(), ImmutableMap.of()), path)
			).path
		);
		assertEquals(
			path.withConstructor("dict"),
			assertThrows(
				DecodeException.class,
				() -> codec.decode(
					new ESExpr.Constructor("dict", ImmutableList.of(new ESExpr.Str("bad")), ImmutableMap.of()),
					path
				)
			).path
		);
		assertEquals(
			path.append("dict", "field"),
			assertThrows(
				DecodeException.class,
				() -> codec.decode(
					new ESExpr.Constructor("dict", ImmutableList.of(), ImmutableMap.of("field", new ESExpr.Bool(true))),
					path
				)
			).path
		);
	}

	@Test
	public void stringTableValidation() throws DecodeException {
		var codec = StringTable.codec();
		var value = new StringTable(ImmutableList.of("a", "λ"));
		assertEquals(value, codec.decode(codec.encode(value)));
		assertTrue(codec.tags().contains(codec.encode(value).tag()));
		assertTrue(codec.isEncodedEqual(value, value));
		assertFalse(codec.isEncodedEqual(value, new StringTable(ImmutableList.of())));
		assertThrows(DecodeException.class, () -> codec.decode(new ESExpr.Bool(true)));
		assertThrows(
			DecodeException.class,
			() -> codec.decode(new ESExpr.Constructor("wrong", ImmutableList.of(), ImmutableMap.of()))
		);
		assertEquals(
			new ESExprCodec.FailurePath.Constructor("string-table"),
			assertThrows(
				DecodeException.class,
				() -> codec.decode(
					new ESExpr.Constructor(
						"string-table",
						ImmutableList.of(),
						ImmutableMap.of("bad", new ESExpr.Bool(true))
					)
				)
			).path
		);
		assertEquals(
			new ESExprCodec.FailurePath.Current().append("string-table", 0),
			assertThrows(
				DecodeException.class,
				() -> codec.decode(
					new ESExpr.Constructor("string-table", ImmutableList.of(new ESExpr.Bool(true)), ImmutableMap.of())
				)
			).path
		);
	}
}
