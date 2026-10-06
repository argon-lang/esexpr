package dev.argon.esexpr;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.junit.jupiter.api.Test;

class ExpressionTest {
	@Test
	void identityCodecAndTags() throws DecodeException {
		var codec = ESExpr.codec();
		var values = List.<ESExpr>of(
			new ESExpr.Null(BigInteger.ZERO),
			new ESExpr.Constructor("custom", ImmutableList.of(), ImmutableMap.of()),
			new ESExpr.Float16((short)0x3c00),
			new ESExpr.Array128(LongLists.immutable.of(0, 1))
		);
		assertTrue(codec.tags().isAll());
		for(var value : values) {
			assertSame(value, codec.encode(value));
			assertSame(value, codec.decode(value));
			assertTrue(codec.tags().contains(value.tag()));
			assertTrue(codec.isEncodedEqual(value, value));
		}
		assertFalse(codec.isEncodedEqual(values.get(0), values.get(1)));
		var optional = ESExprCodec.optionalCodec(codec);
		assertTrue(optional.tags().isAll());
		assertEquals(Optional.of(values.get(1)), optional.decode(optional.encode(Optional.of(values.get(1)))));
		assertEquals(ESExprTag.FLOAT16, values.get(2).tag());
		assertEquals(ESExprTag.ARRAY128, values.get(3).tag());
		assertEquals(ESExprTag.NULL, values.get(0).tag());
	}

	@Test
	void floatingExpressionsCompareRawBits() {
		var half = new ESExpr.Float16((short)0x3c00);
		assertEquals(half, new ESExpr.Float16((short)0x3c00));
		assertNotEquals(half, new ESExpr.Float16((short)0xbc00));
		assertNotEquals(half, "1");
		assertEquals(0x3c00, half.hashCode());
		assertEquals("Float16[f=1.0]", half.toString());
		var single = new ESExpr.Float32(Float.intBitsToFloat(0x7fc00001));
		assertEquals(single, new ESExpr.Float32(Float.intBitsToFloat(0x7fc00001)));
		assertNotEquals(single, new ESExpr.Float32(Float.intBitsToFloat(0x7fc00002)));
		assertNotEquals(single, "NaN");
		assertEquals(0x7fc00001, single.hashCode());
		var doubleValue = new ESExpr.Float64(Double.longBitsToDouble(0x7ff8000000000001L));
		assertEquals(doubleValue, new ESExpr.Float64(Double.longBitsToDouble(0x7ff8000000000001L)));
		assertNotEquals(doubleValue, new ESExpr.Float64(Double.longBitsToDouble(0x7ff8000000000002L)));
		assertNotEquals(doubleValue, "NaN");
		assertEquals(Long.hashCode(0x7ff8000000000001L), doubleValue.hashCode());
	}

	@Test
	void failurePathsPreserveParentsWhenExtended() {
		var current = new ESExprCodec.FailurePath.Current();
		var constructor = current.withConstructor("old");
		assertEquals(current.append("child", 2), constructor.append("child", 2));
		assertEquals(current.append("child", "key"), constructor.append("child", "key"));
		assertEquals(current.withConstructor("new"), constructor.withConstructor("new"));
		var positional = current.append("outer", 1);
		assertEquals(
			new ESExprCodec.FailurePath.Positional("outer", 1, current.append("inner", 2)),
			positional.append("inner", 2)
		);
		assertEquals(
			new ESExprCodec.FailurePath.Positional("outer", 1, current.append("inner", "key")),
			positional.append("inner", "key")
		);
		assertEquals(
			new ESExprCodec.FailurePath.Positional("outer", 1, current.withConstructor("inner")),
			positional.withConstructor("inner")
		);
		var keyword = current.append("outer", "field");
		assertEquals(
			new ESExprCodec.FailurePath.Keyword("outer", "field", current.append("inner", 2)),
			keyword.append("inner", 2)
		);
		assertEquals(
			new ESExprCodec.FailurePath.Keyword("outer", "field", current.append("inner", "key")),
			keyword.append("inner", "key")
		);
		assertEquals(
			new ESExprCodec.FailurePath.Keyword("outer", "field", current.withConstructor("inner")),
			keyword.withConstructor("inner")
		);
	}
}
