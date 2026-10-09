package dev.argon.esexpr;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BinaryCompactionTest {
	private static ESExpr constructor(String name) {
		return new ESExpr.Constructor(name, ImmutableList.of(), ImmutableMap.of());
	}

	@Test
	void recordsNestedMixedRolesAndDedicatedConstructors() {
		var builder = new StringTableBuilder();
		assertEquals(List.of(), builder.build());
		for(var name : List.of("list", "map", "set", "string-table"))
			builder.record(constructor(name));
		assertEquals(List.of(), builder.build());
		builder.record(
			new ESExpr.Constructor(
				"outer",
				ImmutableList.of(
					new ESExpr.Constructor(
						"inner",
						ImmutableList.of(new ESExpr.Str("shared"), new ESExpr.Str("once")),
						ImmutableMap.of()
					)
				),
				ImmutableMap.of("shared", new ESExpr.Str("shared"))
			)
		);
		assertEquals(List.of("shared", "inner", "outer"), builder.build());
		assertEquals(List.of("shared", "inner", "outer"), builder.build());
		builder.record(
			new ESExpr.Constructor(
				"list",
				ImmutableList.of(new ESExpr.Str("map"), new ESExpr.Str("map")),
				ImmutableMap.of("set", new ESExpr.Bool(true))
			)
		);
		assertEquals(List.of("shared", "map", "inner", "outer", "set"), builder.build());
	}

	@Test
	void selectsByUtf8CostAndCombinedFrequency() {
		var builder = new StringTableBuilder();
		for(var s : List.of("", "x", "ab", "é", "a long single-use literal", "", "x", "ab", "é", "x"))
			builder.record(new ESExpr.Str(s));
		assertEquals(List.of("x", "ab", "é"), builder.build());
		var breakEven = new StringTableBuilder();
		breakEven.record(new ESExpr.Str("x"));
		breakEven.record(new ESExpr.Str("x"));
		assertEquals(List.of(), breakEven.build());
	}

	@Test
	void tiesUseUnicodeCodePointOrderRegardlessOfRecordingOrder() {
		var first = new StringTableBuilder();
		var second = new StringTableBuilder();
		for(var name : List.of("\uD800\uDC00", "\uE000", "c", "b", "a"))
			first.record(constructor(name));
		for(var name : List.of("a", "b", "c", "\uE000", "\uD800\uDC00"))
			second.record(constructor(name));
		assertEquals(List.of("a", "b", "c", "\uE000", "\uD800\uDC00"), first.build());
		assertEquals(first.build(), second.build());
	}

	@Test
	void selectionAccountsForIndexWidth() {
		for(int prefix : new int[]{15, 16, 2047, 2048}) {
			var builder = new StringTableBuilder();
			for(int i = 0; i < prefix; ++i) {
				for(int j = 0; j < 5; ++j)
					builder.record(constructor("a" + i));
			}
			var value = prefix < 100 ? "zz" : "zzz";
			for(int j = 0; j < 3; ++j)
				builder.record(new ESExpr.Str(value));
			assertEquals(prefix == 15 || prefix == 2047, builder.build().contains(value));
		}
	}

	@Test
	void compactedStreamDecodesWithFreshReaderAndSavesBytes() throws Exception {
		var expr = new ESExpr.Constructor(
			"custom",
			ImmutableList.of(new ESExpr.Str("repeated long value"), new ESExpr.Str("repeated long value")),
			ImmutableMap.of("key", new ESExpr.Str("once"))
		);
		var builder = new StringTableBuilder();
		builder.record(expr);
		var output = new ByteArrayOutputStream();
		var writer = new ESExprBinaryWriter(output);
		writer.addStrings(builder.build());
		writer.write(expr);
		assertEquals(
			List.of(expr),
			new ESExprBinaryReader(new ByteArrayInputStream(output.toByteArray())).readAll().toList()
		);
		var ordinary = new ByteArrayOutputStream();
		new ESExprBinaryWriter(ordinary).write(expr);
		assertTrue(output.size() < ordinary.size());
	}

	@Test
	void additionsPreserveIndicesAndSkipDuplicates() throws Exception {
		var output = new ByteArrayOutputStream();
		var writer = new ESExprBinaryWriter(output);
		writer.addStrings(List.of());
		assertEquals(0, output.size());
		writer.addStrings(List.of("first", "first"));
		writer.write(new ESExpr.Str("first"));
		writer.addStrings(List.of("first", "second", "third", "second"));
		writer.addStrings(List.of("third"));
		for(var value : List.of("first", "second", "third"))
			writer.write(new ESExpr.Str(value));
		assertArrayEquals(
			new byte[]{(byte)0xEB, 0x65, 'f', 'i', 'r', 's', 't', (byte)0x80, (byte)0xEB, (byte)0xE6, 0x66, 's', 'e',
					'c', 'o', 'n', 'd', 0x65, 't', 'h', 'i', 'r', 'd', (byte)0xE0, (byte)0x80, (byte)0x81, (byte)0x82},
			output.toByteArray()
		);
		assertEquals(
			List.of(
				new ESExpr.Str("first"),
				new ESExpr.Str("first"),
				new ESExpr.Str("second"),
				new ESExpr.Str("third")
			),
			new ESExprBinaryReader(new ByteArrayInputStream(output.toByteArray())).readAll().toList()
		);
	}

	@Test
	void additionsAfterAutomaticSymbolsPreserveIndices() throws Exception {
		var output = new ByteArrayOutputStream();
		var writer = new ESExprBinaryWriter(output);
		writer.write(constructor("first"));
		int size = output.size();
		writer.addStrings(List.of("first", "second"));
		writer.write(new ESExpr.Str("first"));
		writer.write(new ESExpr.Str("second"));
		assertArrayEquals(
			new byte[]{(byte)0xEB, 0x66, 's', 'e', 'c', 'o', 'n', 'd', (byte)0x80, (byte)0x81},
			java.util.Arrays.copyOfRange(output.toByteArray(), size, output.size())
		);
		assertEquals(
			List.of(constructor("first"), new ESExpr.Str("first"), new ESExpr.Str("second")),
			new ESExprBinaryReader(new ByteArrayInputStream(output.toByteArray())).readAll().toList()
		);
	}

	@Test
	void dedicatedConstructorsNeedNoAppends() throws Exception {
		var output = new ByteArrayOutputStream();
		var writer = new ESExprBinaryWriter(output);
		for(var name : List.of("list", "map", "set", "string-table"))
			writer.write(constructor(name));
		assertArrayEquals(
			new byte[]{(byte)0xE7, (byte)0xE0, (byte)0xF1, (byte)0xE0, (byte)0xF2, (byte)0xE0, (byte)0xE6, (byte)0xE0},
			output.toByteArray()
		);
	}

	@Test
	void referencesUseActualIndexWidthAndInlineFallback() throws Exception {
		for(int index : new int[]{15, 16, 2047, 2048}) {
			var pool = new java.util.ArrayList<>(IntStream.range(0, index).mapToObj(i -> "entry" + i).toList());
			pool.add("zz");
			pool.add("x");
			var output = new ByteArrayOutputStream();
			var writer = new ESExprBinaryWriter(pool, output);
			writer.write(new ESExpr.Str("zz"));
			byte[] expected = switch(index) {
				case 15 -> new byte[]{(byte)0x8F};
				case 16 -> new byte[]{(byte)0x90, 1};
				case 2047 -> new byte[]{(byte)0x9F, 0x7F};
				default -> new byte[]{(byte)0x90, (byte)0x80, 1};
			};
			assertArrayEquals(expected, output.toByteArray());
			if(index == 2048) {
				output.reset();
				writer.write(new ESExpr.Str("x"));
				assertArrayEquals(new byte[]{0x61, 'x'}, output.toByteArray());
			}
		}
	}

	@Test
	void stringLengthBoundariesRoundTrip() throws Exception {
		for(int length : new int[]{15, 16, 2047, 2048}) {
			var expr = new ESExpr.Str("z".repeat(length));
			var builder = new StringTableBuilder();
			builder.record(expr);
			builder.record(expr);
			assertEquals(List.of(expr.s()), builder.build());
			var output = new ByteArrayOutputStream();
			var writer = new ESExprBinaryWriter(output);
			writer.addStrings(builder.build());
			writer.write(expr);
			assertEquals(expr, new ESExprBinaryReader(new ByteArrayInputStream(output.toByteArray())).read());
		}
	}

	@Test
	void additionErrorsPropagateAtEveryByte() throws Exception {
		for(var strings : List.of(List.<String>of(), List.of("single"), List.of("first", "second", "first"))) {
			var baseline = new ByteArrayOutputStream();
			new ESExprBinaryWriter(baseline).addStrings(strings);
			for(int offset = 0; offset <= baseline.size(); ++offset) {
				int limit = offset;
				var partial = new ByteArrayOutputStream();
				var output = new OutputStream() {
					@Override
					public void write(int b) throws IOException {
						if(partial.size() == limit)
							throw new IOException("injected write failure");
						partial.write(b);
					}
				};
				var writer = new ESExprBinaryWriter(output);
				if(offset == baseline.size())
					writer.addStrings(strings);
				else
					assertThrows(IOException.class, () -> writer.addStrings(strings));
				assertArrayEquals(java.util.Arrays.copyOf(baseline.toByteArray(), offset), partial.toByteArray());
			}
		}
	}
}
