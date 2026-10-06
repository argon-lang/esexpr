package dev.argon.esexpr;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.ArrayDeque;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

class CollectionCodecTest {
	private static ESExpr.Constructor ctor(String name, ESExpr... args) {
		return new ESExpr.Constructor(name, ImmutableList.copyOf(args), ImmutableMap.of());
	}

	private <T> void collection(ESExprCodec<T> codec, String name, T empty, T one, T other) throws DecodeException {
		assertEquals(empty, codec.decode(codec.encode(empty)));
		assertEquals(one, codec.decode(codec.encode(one)));
		assertTrue(codec.tags().contains(new ESExprTag.Constructor(name)));
		assertTrue(codec.isEncodedEqual(empty, empty));
		assertTrue(codec.isEncodedEqual(one, one));
		assertFalse(codec.isEncodedEqual(empty, one));
		assertFalse(codec.isEncodedEqual(one, other));
		assertThrows(DecodeException.class, () -> codec.decode(new ESExpr.Bool(true)));
		assertThrows(DecodeException.class, () -> codec.decode(ctor("wrong")));
		var path = new ESExprCodec.FailurePath.Current();
		var error = assertThrows(
			DecodeException.class,
			() -> codec.decode(
				new ESExpr.Constructor(name, ImmutableList.of(), ImmutableMap.of("bad", new ESExpr.Bool(true))),
				path
			)
		);
		assertEquals(path.withConstructor(name), error.path);
		var badItem = assertThrows(
			DecodeException.class,
			() -> codec.decode(ctor(name, new ESExpr.Bool(true), new ESExpr.Bool(false)), path)
		);
		assertEquals(path.append(name, 0), badItem.path);
	}

	@Test
	void collections() throws DecodeException {
		var strings = ESExprCodec.stringCodec();
		collection(ESExprCodec.listCodec(strings), "list", List.of(), List.of("a"), List.of("b"));
		collection(ESExprCodec.setCodec(strings), "set", Set.of(), Set.of("a"), Set.of("b"));
		collection(
			ESExprCodec.immutableSetCodec(strings),
			"set",
			ImmutableSet.of(),
			ImmutableSet.of("a"),
			ImmutableSet.of("b")
		);
		collection(ESExprCodec.mapCodec(strings, strings), "map", Map.of(), Map.of("a", "b"), Map.of("a", "c"));
		collection(
			ESExprCodec.immutableMapCodec(strings, strings),
			"map",
			ImmutableMap.of(),
			ImmutableMap.of("a", "b"),
			ImmutableMap.of("a", "c")
		);
		var map = ESExprCodec.mapCodec(strings, strings);
		assertFalse(map.isEncodedEqual(Map.of("a", "b"), Map.of("c", "b")));
		assertThrows(DecodeException.class, () -> map.decode(ctor("map", new ESExpr.Str("a"))));
		var error = assertThrows(
			DecodeException.class,
			() -> map.decode(ctor("map", new ESExpr.Str("a"), new ESExpr.Bool(true)))
		);
		assertEquals(new ESExprCodec.FailurePath.Current().append("map", 1), error.path);
		assertEquals(
			Set.of("a"),
			ESExprCodec.setCodec(strings).decode(ctor("set", new ESExpr.Str("a"), new ESExpr.Str("a")))
		);
		assertEquals(
			List.of("a", "b"),
			ESExprCodec.listCodec(strings).decode(ctor("list", new ESExpr.Str("a"), new ESExpr.Str("b")))
		);
	}

	@Test
	void nestedOptionals() throws DecodeException {
		var codec = ESExprCodec.optionalCodec(ESExprCodec.optionalCodec(ESExprCodec.stringCodec()));
		List<Optional<Optional<String>>> values = List
			.of(Optional.empty(), Optional.of(Optional.empty()), Optional.of(Optional.of("value")));
		List<ESExpr> expected = List
			.of(new ESExpr.Null(BigInteger.ZERO), new ESExpr.Null(BigInteger.ONE), new ESExpr.Str("value"));
		for(int i = 0; i < values.size(); i++) {
			assertEquals(expected.get(i), codec.encode(values.get(i)));
			assertEquals(values.get(i), codec.decode(expected.get(i)));
			for(int j = 0; j < values.size(); j++)
				assertEquals(i == j, codec.isEncodedEqual(values.get(i), values.get(j)));
		}
		assertEquals(ESExprTagSet.of(ESExprTag.NULL, ESExprTag.STR), codec.tags());
		assertThrows(DecodeException.class, () -> codec.decode(new ESExpr.Bool(true)));
	}

	@Test
	void optionalArguments() throws DecodeException {
		var codec = OptionalValueCodec.optionalValueCodec(ESExprCodec.stringCodec());
		assertEquals(Optional.empty(), codec.encodeOptional(Optional.empty()));
		assertEquals(Optional.of(new ESExpr.Str("a")), codec.encodeOptional(Optional.of("a")));
		assertEquals(Optional.empty(), codec.decodeOptional(Optional.empty(), new ESExprCodec.FailurePath.Current()));
		assertEquals(
			Optional.of("a"),
			codec.decodeOptional(Optional.of(new ESExpr.Str("a")), new ESExprCodec.FailurePath.Current())
		);
		for(var x : List.of(Optional.<String>empty(), Optional.of("a"), Optional.of("b"))) {
			for(var y : List.of(Optional.<String>empty(), Optional.of("a"), Optional.of("b")))
				assertEquals(x.equals(y), codec.isEncodedEqual(x, y));
		}
		var path = new ESExprCodec.FailurePath.Current().append("record", "field");
		assertEquals(
			path,
			assertThrows(
				DecodeException.class,
				() -> codec.decodeOptional(Optional.of(new ESExpr.Bool(true)), path)
			).path
		);
	}

	@Test
	void varargsStopBeforeDifferentTagAndPreserveOffsets() throws DecodeException {
		var codec = VarargCodec.listCodec(ESExprCodec.signedByteCodec());
		var builder = ImmutableList.<ESExpr>builder();
		codec.encodeVararg(List.of((byte)1, (byte)2), builder);
		assertEquals(List.of(new ESExpr.Int(BigInteger.ONE), new ESExpr.Int(BigInteger.TWO)), builder.build());
		var queue = new ArrayDeque<ESExpr>(builder.build());
		queue.add(new ESExpr.Str("remaining"));
		var path = new ESExprCodec.FailurePath.Current().appenderWithOffset("record", 3);
		assertEquals(List.of((byte)1, (byte)2), codec.decodeVararg(queue, path));
		assertEquals(List.of(new ESExpr.Str("remaining")), List.copyOf(queue));
		assertEquals(List.of(), codec.decodeVararg(queue, path));
		queue.clear();
		assertEquals(List.of(), codec.decodeVararg(queue, path));
		queue.add(new ESExpr.Int(BigInteger.valueOf(256)));
		assertEquals(
			new ESExprCodec.FailurePath.Current().append("record", 3),
			assertThrows(DecodeException.class, () -> codec.decodeVararg(queue, path)).path
		);
		assertTrue(codec.isEncodedEqual(List.of((byte)1), List.of((byte)1)));
		assertFalse(codec.isEncodedEqual(List.of((byte)1), List.of((byte)2)));
		assertFalse(codec.isEncodedEqual(List.of(), List.of((byte)1)));
	}
}
