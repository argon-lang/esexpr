package dev.argon.esexpr;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class RuntimeValuesTest {
	@Test
	void tagSetOperations() {
		var empty = ESExprTagSet.of();
		var strings = ESExprTagSet.of(ESExprTag.STR);
		var ints = ESExprTagSet.of(ESExprTag.INT);
		var all = new ESExprTagSet.All();
		assertTrue(empty.isEmpty());
		assertFalse(strings.isEmpty());
		assertFalse(strings.isAll());
		assertTrue(all.isAll());
		assertFalse(all.isEmpty());
		assertTrue(all.contains(new ESExprTag.Constructor("anything")));
		assertSame(strings, strings.add(ESExprTag.STR));
		assertEquals(strings.union(ints), strings.add(ESExprTag.INT));
		assertFalse(strings.contains(ESExprTag.INT));
		assertTrue(strings.isDisjoint(ints));
		assertFalse(strings.isDisjoint(strings));
		assertTrue(empty.isDisjoint(all));
		assertFalse(strings.isDisjoint(all));
		assertTrue(all.isDisjoint(empty));
		assertFalse(all.isDisjoint(strings));
		assertSame(all, strings.union(all));
		assertSame(all, all.union(strings));
		assertSame(all, all.add(ESExprTag.STR));
	}

	@Test
	void unsignedWrappersPreserveBitsAndValueEquality() {
		var b = UnsignedByte.fromByteBits((byte)-1);
		assertEquals(255, b.intValue());
		assertEquals(255, b.shortValue());
		assertEquals((byte)-1, b.byteValue());
		assertEquals("255", b.toString());
		assertEquals(b, UnsignedByte.fromByteBits((byte)-1));
		assertEquals(b.hashCode(), UnsignedByte.fromByteBits((byte)-1).hashCode());
		assertNotEquals(b, UnsignedByte.fromByteBits((byte)0));
		assertNotEquals(b, "255");
		var s = UnsignedShort.fromShortBits((short)-1);
		assertEquals(65535, s.intValue());
		assertEquals((short)-1, s.shortValue());
		assertEquals("65535", s.toString());
		assertEquals(s, UnsignedShort.fromShortBits((short)-1));
		assertEquals(s.hashCode(), UnsignedShort.fromShortBits((short)-1).hashCode());
		assertNotEquals(s, UnsignedShort.fromShortBits((short)0));
		assertNotEquals(s, "65535");
		assertEquals(256, UnsignedShort.fromShortBits((short)256).intValue());
		var big = UnsignedBigInteger.valueOf("123456789012345678901234567890");
		assertEquals(new BigInteger("123456789012345678901234567890"), big.toBigInteger());
		assertEquals("123456789012345678901234567890", big.toString());
		assertEquals(big, UnsignedBigInteger.valueOf(big.toBigInteger()));
		assertEquals(big.hashCode(), UnsignedBigInteger.valueOf(big.toBigInteger()).hashCode());
		assertEquals(UnsignedBigInteger.valueOf(2), UnsignedBigInteger.ONE.add(UnsignedBigInteger.ONE));
		assertNotEquals(big, UnsignedBigInteger.ZERO);
		assertNotEquals(big, "123");
		assertThrows(IllegalArgumentException.class, () -> UnsignedBigInteger.valueOf(-1));
		assertThrows(NumberFormatException.class, () -> UnsignedBigInteger.valueOf("bad"));
	}

	@Test
	@SuppressWarnings("NullAway") // Deliberately exercise defensive validation of invalid input.
	void unsignedBigIntegerRejectsNull() {
		assertThrows(IllegalArgumentException.class, () -> UnsignedBigInteger.valueOf((BigInteger)null));
	}
}
