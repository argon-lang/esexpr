package dev.argon.esexpr;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;

import org.eclipse.collections.api.factory.primitive.ByteLists;
import org.eclipse.collections.api.factory.primitive.ShortLists;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.junit.jupiter.api.Test;

class ScalarCodecTest {
	private <T> void check(ESExprCodec<T> codec, T first, T second) throws DecodeException {
		for(T value : List.of(first, second)) {
			var encoded = codec.encode(value);
			assertTrue(codec.tags().contains(encoded.tag()));
			assertTrue(codec.isEncodedEqual(value, codec.decode(encoded)));
		}
		assertFalse(codec.isEncodedEqual(first, second));
		var path = new ESExprCodec.FailurePath.Current().append("outer", 2);
		var error = assertThrows(DecodeException.class, () -> codec.decode(new ESExpr.Null(BigInteger.ZERO), path));
		assertEquals(path, error.path);
		assertTrue(error.getMessage().contains(path.toString()));
	}

	private <T> void integerBounds(ESExprCodec<T> codec, BigInteger min, BigInteger max) throws DecodeException {
		assertEquals(new ESExpr.Int(min), codec.encode(codec.decode(new ESExpr.Int(min))));
		assertEquals(new ESExpr.Int(max), codec.encode(codec.decode(new ESExpr.Int(max))));
		assertThrows(DecodeException.class, () -> codec.decode(new ESExpr.Int(min.subtract(BigInteger.ONE))));
		assertThrows(DecodeException.class, () -> codec.decode(new ESExpr.Int(max.add(BigInteger.ONE))));
	}

	@Test
	void signedIntegers() throws DecodeException {
		check(ESExprCodec.signedByteCodec(), Byte.MIN_VALUE, Byte.MAX_VALUE);
		check(ESExprCodec.signedShortCodec(), Short.MIN_VALUE, Short.MAX_VALUE);
		check(ESExprCodec.signedIntegerCodec(), Integer.MIN_VALUE, Integer.MAX_VALUE);
		check(ESExprCodec.signedLongCodec(), Long.MIN_VALUE, Long.MAX_VALUE);
		integerBounds(ESExprCodec.signedByteCodec(), BigInteger.valueOf(-128), BigInteger.valueOf(127));
		integerBounds(ESExprCodec.signedShortCodec(), BigInteger.valueOf(-32768), BigInteger.valueOf(32767));
		integerBounds(
			ESExprCodec.signedIntegerCodec(),
			BigInteger.valueOf(Integer.MIN_VALUE),
			BigInteger.valueOf(Integer.MAX_VALUE)
		);
		integerBounds(
			ESExprCodec.signedLongCodec(),
			BigInteger.valueOf(Long.MIN_VALUE),
			BigInteger.valueOf(Long.MAX_VALUE)
		);
	}

	@Test
	void unsignedIntegers() throws DecodeException {
		check(UnsignedByte.INSTANCE, UnsignedByte.fromByteBits((byte)0), UnsignedByte.fromByteBits((byte)-1));
		check(UnsignedShort.INSTANCE, UnsignedShort.fromShortBits((short)0), UnsignedShort.fromShortBits((short)-1));
		check(
			ESExprCodec.unsignedIntegerCodec(),
			com.google.common.primitives.UnsignedInteger.ZERO,
			com.google.common.primitives.UnsignedInteger.MAX_VALUE
		);
		check(
			ESExprCodec.unsignedLongCodec(),
			com.google.common.primitives.UnsignedLong.ZERO,
			com.google.common.primitives.UnsignedLong.MAX_VALUE
		);
		integerBounds(UnsignedByte.INSTANCE, BigInteger.ZERO, BigInteger.valueOf(255));
		integerBounds(UnsignedShort.INSTANCE, BigInteger.ZERO, BigInteger.valueOf(65535));
		integerBounds(
			ESExprCodec.unsignedIntegerCodec(),
			BigInteger.ZERO,
			BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE)
		);
		integerBounds(
			ESExprCodec.unsignedLongCodec(),
			BigInteger.ZERO,
			BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
		);
		check(ESExprCodec.bigIntegerCodec(), BigInteger.ONE.shiftLeft(200), BigInteger.ONE.shiftLeft(200).negate());
		check(
			UnsignedBigInteger.CODEC,
			UnsignedBigInteger.ZERO,
			UnsignedBigInteger.valueOf(BigInteger.ONE.shiftLeft(200))
		);
		assertThrows(
			DecodeException.class,
			() -> UnsignedBigInteger.CODEC.decode(new ESExpr.Int(BigInteger.valueOf(-1)))
		);
	}

	@Test
	void scalarsAndFloatingPointBits() throws DecodeException {
		check(ESExprCodec.booleanCodec(), true, false);
		check(ESExprCodec.stringCodec(), "", "hello λ");
		check(ESExprCodec.floatCodec(), 0.0f, -0.0f);
		check(ESExprCodec.doubleCodec(), 0.0, -0.0);
		float f = Float.intBitsToFloat(0x7fc00001);
		double d = Double.longBitsToDouble(0x7ff8000000000001L);
		assertEquals(
			Float.floatToRawIntBits(f),
			Float.floatToRawIntBits(ESExprCodec.floatCodec().decode(ESExprCodec.floatCodec().encode(f)))
		);
		assertEquals(
			Double.doubleToRawLongBits(d),
			Double.doubleToRawLongBits(ESExprCodec.doubleCodec().decode(ESExprCodec.doubleCodec().encode(d)))
		);
		assertFalse(ESExprCodec.floatCodec().isEncodedEqual(f, Float.intBitsToFloat(0x7fc00002)));
		assertFalse(ESExprCodec.doubleCodec().isEncodedEqual(d, Double.longBitsToDouble(0x7ff8000000000002L)));
	}

	@Test
	void primitiveArraysAndLists() throws DecodeException {
		check(ESExprCodec.byteArrayCodec(), new byte[0], new byte[]{0, -1, 127});
		check(ESExprCodec.shortArrayCodec(), new short[0], new short[]{0, -1, Short.MAX_VALUE});
		check(ESExprCodec.intArrayCodec(), new int[0], new int[]{0, -1, Integer.MAX_VALUE});
		check(ESExprCodec.longArrayCodec(), new long[0], new long[]{0, -1, Long.MAX_VALUE});
		check(ESExprCodec.immutableByteListCodec(), ByteLists.immutable.empty(), ByteLists.immutable.of((byte)-1));
		check(ESExprCodec.immutableShortListCodec(), ShortLists.immutable.empty(), ShortLists.immutable.of((short)-1));
		check(ESExprCodec.immutableIntListCodec(), IntLists.immutable.empty(), IntLists.immutable.of(-1));
		check(ESExprCodec.immutableLongListCodec(), LongLists.immutable.empty(), LongLists.immutable.of(-1L));
	}
}
