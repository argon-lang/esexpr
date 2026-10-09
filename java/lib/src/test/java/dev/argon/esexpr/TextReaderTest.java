package dev.argon.esexpr;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TextReaderTest {
	private ESExprTextReader reader(String s) {
		return new ESExprTextReader(new StringReader(s));
	}

	@Test
	void sharedFixtures() throws Exception {
		try(var files = Files.list(Path.of("../../tests"))) {
			for(var path : files.filter(p -> p.toString().endsWith(".esx")).toList()) {
				var binary = path.resolveSibling(path.getFileName().toString().replace(".esx", ".esxb"));
				try(var text = Files.newBufferedReader(path); var bytes = Files.newInputStream(binary)) {
					assertEquals(
						new ESExprBinaryReader(bytes).readAll().toList(),
						new ESExprTextReader(text).readAll().toList(),
						path.toString()
					);
				}
			}
		}
	}

	@Test
	void boundariesAndErrors() throws Exception {
		var r = reader(" // comment\n #true #false // final comment");
		assertEquals(new ESExpr.Bool(true), r.read());
		assertEquals(new ESExpr.Bool(false), r.read());
		assertNull(r.tryRead());
		assertThrows(EOFException.class, r::read);
		for(String s : new String[]{"(list", "\"unterminated", "\"\\u{110000}\"", "\"\\u{D800}\"", "\"\\q\"", "0x1.0",
				"1.0e", "#truex", "#u8[256]", "#u16[-1]", "#\"0\"", "#float32:nan:8388608", "(a key:)"}) {
			assertThrows(SyntaxException.class, () -> {
				var r2 = reader(s);
				while (r2.tryRead() != null) {
				}
			}, s);
		}
	}

	@Test
	void numbersAndPayloads() throws Exception {
		assertEquals(
			new ESExpr.Int(new BigInteger("340282366920938463463374607431768211456")),
			reader("+0x100000000000000000000000000000000").read()
		);
		for(String suffix : new String[]{"", "d", "D", "f", "F", "f16", "F16"}) {
			assertEquals(reader("-1.5e+1" + suffix).read(), reader("-0x1.ep3" + suffix).read());
		}
		assertEquals(new ESExpr.Float16((short)0xFC01), reader("#float16:-nan:1").read());
		assertEquals(new ESExpr.Float32(Float.intBitsToFloat(0xFF800001)), reader("#float32:-nan:1").read());
		assertEquals(
			new ESExpr.Float64(Double.longBitsToDouble(0xFFF0000000000001L)),
			reader("#float64:-nan:1").read()
		);
		var array = (ESExpr.Array128)reader("#u128[340282366920938463463374607431768211455]").read();
		assertEquals(-1L, array.b().get(0));
		assertEquals(-1L, array.b().get(1));
	}

	@Test
	void constructorAndEscapes() throws Exception {
		var e = (ESExpr.Constructor)reader("('a\\u{1F600}' key: 1 \"\\n\\t\\f\\r\\\\\\\"\\'\" key: 2)").read();
		assertEquals("a😀", e.constructor());
		assertEquals(new ESExpr.Int(BigInteger.TWO), e.kwargs().get("key"));
		assertEquals(new ESExpr.Str("\n\t\f\r\\\"'"), e.args().getFirst());
	}

	@Test
	void propagatesIoErrors() {
		var r = new ESExprTextReader(new Reader() {
			@Override
			public int read(char[] buffer, int start, int length) throws IOException {
				throw new IOException("failure");
			}

			@Override
			public void close() {}
		});
		assertThrows(IOException.class, () -> {
			r.tryRead();
		});
	}
}
