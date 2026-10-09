package dev.argon.esexpr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class TextWriterTest {
	private String format(ESExpr expr) throws IOException {
		var output = new StringWriter();
		new ESExprTextWriter(output).write(expr);
		return output.toString();
	}

	private void roundTrip(ESExpr expr) throws Exception {
		var r = new ESExprTextReader(new StringReader(format(expr)));
		assertEquals(expr, r.read());
		assertNull(r.tryRead());
	}

	@Test
	void fixtures() throws Exception {
		try(var files = Files.list(Path.of("../../tests"))) {
			for(var path : files.filter(p -> p.toString().endsWith(".esxb")).toList()) {
				try(var input = Files.newInputStream(path)) {
					var values = new ESExprBinaryReader(input).readAll().toList();
					var output = new StringWriter();
					var writer = new ESExprTextWriter(output);
					for(var value : values)
						writer.write(value);
					assertEquals(
						values,
						new ESExprTextReader(new StringReader(output.toString())).readAll().toList(),
						path.toString()
					);
				}
			}
		}
	}

	@Test
	void floatBits() throws Exception {
		// Every half-precision pattern includes subnormals, signed zeros and all NaN
		// payloads.
		for(int bits = 0; bits < 65536; ++bits)
			roundTrip(new ESExpr.Float16((short)bits));
		var random = new Random(42);
		for(int i = 0; i < 1000; ++i) {
			roundTrip(new ESExpr.Float32(Float.intBitsToFloat(random.nextInt())));
			roundTrip(new ESExpr.Float64(Double.longBitsToDouble(random.nextLong())));
		}
		for(long payload : new long[]{1, 0xFFFFFFFFFFFFFL}) {
			roundTrip(new ESExpr.Float64(Double.longBitsToDouble(0xFFF0000000000000L | payload)));
		}
	}

	@Test
	void escapingAndFormatting() throws Exception {
		var expr = new ESExpr.Constructor(
			"a'b", ImmutableList.of(new ESExpr.Str("\u0000\n\r\t\f\\\"😀")),
			ImmutableMap.of("z", new ESExpr.Bool(false), "A", new ESExpr.Bool(true))
		);
		assertEquals("('a\\'b' \"\\u{0}\\n\\r\\t\\f\\\\\\\"😀\" 'A': #true z: #false)", format(expr));
		roundTrip(expr);
		var output = new StringWriter();
		var writer = new ESExprTextWriter(output, "  ");
		var nested = new ESExpr.Constructor(
			"outer",
			ImmutableList
				.of(new ESExpr.Constructor("inner", ImmutableList.of(new ESExpr.Bool(true)), ImmutableMap.of())),
			ImmutableMap.of()
		);
		writer.write(nested);
		assertEquals("(outer\n  (inner\n    #true\n  )\n)", output.toString());
		assertEquals(nested, new ESExprTextReader(new StringReader(output.toString())).read());
		assertThrows(IllegalArgumentException.class, () -> new ESExprTextWriter(output, "x"));
	}

	@Test
	void unsigned128AndInvalidValues() throws Exception {
		var array = new ESExpr.Array128(LongLists.immutable.of(-1L, -1L, 1L, 2L));
		assertEquals("#u128[340282366920938463463374607431768211455 36893488147419103233]", format(array));
		roundTrip(array);
		assertThrows(IllegalArgumentException.class, () -> format(new ESExpr.Array128(LongLists.immutable.of(1L))));
		assertThrows(IllegalArgumentException.class, () -> format(new ESExpr.Null(BigInteger.ONE.negate())));
	}

	@Test
	void ioFailure() {
		var writer = new ESExprTextWriter(new Writer() {
			@Override
			public void write(char[] chars, int start, int length) throws IOException {
				throw new IOException("failure");
			}

			@Override
			public void flush() {}

			@Override
			public void close() {}
		});
		assertThrows(IOException.class, () -> writer.write(new ESExpr.Bool(true)));
	}
}
