package dev.argon.esexpr;

import static org.junit.jupiter.api.Assertions.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.junit.jupiter.api.Test;

class BinaryEdgeCaseTest {
	@Test
	void writerReusesInitialPoolAndWritesMissingStringsDirectly() throws Exception {
		var symbols = java.util.stream.IntStream.range(0, 17).mapToObj(i -> "symbol" + i).toList();
		var output = new ByteArrayOutputStream();
		var writer = new ESExprBinaryWriter(symbols, output);
		writer.write(new ESExpr.Str("symbol0"));
		writer.write(new ESExpr.Str("symbol16"));
		writer.write(new ESExpr.Str("λ"));
		assertArrayEquals(new byte[]{(byte)0x80, (byte)0x90, 1, 0x62, (byte)0xce, (byte)0xbb}, output.toByteArray());
		assertEquals(
			List.of(new ESExpr.Str("symbol0"), new ESExpr.Str("symbol16"), new ESExpr.Str("λ")),
			new ESExprBinaryReader(symbols, new ByteArrayInputStream(output.toByteArray())).readAll().toList()
		);
	}

	@Test
	void writerReusesAppendedSymbolsAfterDefiningThemLiterally() throws Exception {
		for(boolean withKeyword : List.of(false, true)) {
			var output = new ByteArrayOutputStream();
			var writer = new ESExprBinaryWriter(output);
			var expr = new ESExpr.Constructor(
				"x",
				ImmutableList.of(new ESExpr.Str("x")),
				withKeyword ? ImmutableMap.of("y", new ESExpr.Str("y")) : ImmutableMap.of()
			);
			writer.write(expr);
			writer.write(new ESExpr.Str("x"));
			byte[] expected = withKeyword
				? new byte[]{(byte)0xeb, (byte)0xe6, 0x61, 'x', 0x61, 'y', (byte)0xe0, 0, (byte)0x80, (byte)0xc1,
						(byte)0x81, (byte)0xe0, (byte)0x80}
				: new byte[]{(byte)0xeb, 0x61, 'x', 0, (byte)0x80, (byte)0xe0, (byte)0x80};
			assertArrayEquals(expected, output.toByteArray());
			assertEquals(
				List.of(expr, new ESExpr.Str("x")),
				new ESExprBinaryReader(new ByteArrayInputStream(output.toByteArray())).readAll().toList()
			);
		}
	}

	private static ESExprBinaryReader reader(int... bytes) {
		byte[] data = new byte[bytes.length];
		for(int i = 0; i < bytes.length; i++)
			data[i] = (byte)bytes[i];
		return new ESExprBinaryReader(new ByteArrayInputStream(data));
	}

	@Test
	void truncatedValuesThrowEof() {
		// Continuation integers, strings, arrays, floats, and incomplete constructors.
		for(int[] bytes : List.of(
			new int[]{},
			new int[]{0x30},
			new int[]{0x30, 0x80},
			new int[]{0x61},
			new int[]{0xa1},
			new int[]{0xec, 0},
			new int[]{0xe4, 0, 0, 0},
			new int[]{0xe5, 0},
			new int[]{0xed, 1, 0},
			new int[]{0xee, 1, 0},
			new int[]{0xef, 1, 0},
			new int[]{0xf0, 1, 0},
			new int[]{0xea},
			new int[]{0xe7},
			new int[]{0xeb}
		)) {
			assertThrows(EOFException.class, () -> reader(bytes).read());
		}
		assertDoesNotThrow(() -> assertNull(reader().tryRead()));
	}

	@Test
	void invalidTokensAndStringTableUpdates() throws Exception {
		assertThrows(SyntaxException.class, () -> reader(0xff).read());
		assertThrows(SyntaxException.class, () -> reader(0xe0).read());
		var keyword = new ESExprBinaryReader(List.of("field"), new ByteArrayInputStream(new byte[]{(byte)0xc0}));
		assertThrows(SyntaxException.class, keyword::read);
		var badTable = assertThrows(SyntaxException.class, () -> reader(0xeb, 0xe1).read());
		assertInstanceOf(DecodeException.class, badTable.getCause());
		assertEquals("Could not decode string table.", badTable.getMessage());
		// An append directive can occur inside a constructor, before a pooled string.
		assertEquals(
			new ESExpr.Constructor("list", ImmutableList.of(new ESExpr.Str("x")), ImmutableMap.of()),
			reader(0xe7, 0xeb, 0x61, 'x', 0x80, 0xe0).read()
		);
		assertNull(reader(0xeb, 0x61, 'x').tryRead());
		var missingKeywordValue = new ESExprBinaryReader(
			List.of("field"),
			new ByteArrayInputStream(new byte[]{(byte)0xe7, (byte)0xc0})
		);
		assertThrows(EOFException.class, missingKeywordValue::read);
	}

	@Test
	void readAllWrapsParseAndIoFailures() {
		assertInstanceOf(
			SyntaxException.class,
			assertThrows(RuntimeException.class, () -> reader(0xff).readAll().toList()).getCause()
		);
		assertInstanceOf(
			EOFException.class,
			assertThrows(RuntimeException.class, () -> reader(0x61).readAll().toList()).getCause()
		);
		var failure = new IOException("read failed");
		var broken = new ESExprBinaryReader(new InputStream() {
			@Override
			public int read() throws IOException {
				throw failure;
			}

			@Override
			public int read(byte[] bytes, int offset, int length) throws IOException {
				throw failure;
			}
		});
		assertSame(failure, assertThrows(RuntimeException.class, () -> broken.readAll().toList()).getCause());
	}

	@Test
	void initialSymbolsAndReservedConstructors() throws Exception {
		var output = new ByteArrayOutputStream();
		var symbols = List.of("custom", "field");
		var writer = new ESExprBinaryWriter(symbols, output);
		var expr = new ESExpr.Constructor(
			"custom",
			ImmutableList.of(),
			ImmutableMap.of("field", new ESExpr.Bool(true))
		);
		writer.write(expr);
		assertArrayEquals(new byte[]{0, (byte)0xc1, (byte)0xe1, (byte)0xe0}, output.toByteArray());
		assertEquals(expr, new ESExprBinaryReader(symbols, new ByteArrayInputStream(output.toByteArray())).read());
		output.reset();
		writer = new ESExprBinaryWriter(output);
		var values = ImmutableList.<ESExpr>of(
			new ESExpr.Constructor("list", ImmutableList.of(), ImmutableMap.of()),
			new ESExpr.Constructor("map", ImmutableList.of(), ImmutableMap.of()),
			new ESExpr.Constructor("set", ImmutableList.of(), ImmutableMap.of()),
			new ESExpr.Constructor("string-table", ImmutableList.of(new ESExpr.Str("a")), ImmutableMap.of())
		);
		for(var value : values)
			writer.write(value);
		assertEquals(values, new ESExprBinaryReader(new ByteArrayInputStream(output.toByteArray())).readAll().toList());
	}

	@Test
	void oddArray128IsRejected() {
		var writer = new ESExprBinaryWriter(new ByteArrayOutputStream());
		assertThrows(
			IllegalArgumentException.class,
			() -> writer.write(new ESExpr.Array128(LongLists.immutable.of(1)))
		);
	}

	@Test
	void syntaxExceptionConstructorsRetainDetails() {
		var cause = new IOException("cause");
		assertEquals("message", new SyntaxException("message").getMessage());
		assertSame(cause, new SyntaxException(cause).getCause());
		var error = new SyntaxException("message", cause);
		assertEquals("message", error.getMessage());
		assertSame(cause, error.getCause());
	}
}
