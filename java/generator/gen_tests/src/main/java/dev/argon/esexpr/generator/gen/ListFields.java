package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;
import java.util.List;

@ESExprCodecGen
public record ListFields(
	List<Boolean> a,
	List<Byte> b,
	List<@Unsigned Byte> b2,
	List<Short> c,
	List<@Unsigned Short> c2,
	List<Integer> d,
	List<@Unsigned Integer> d2,
	List<Long> e,
	List<@Unsigned Long> e2,
	List<Float> f,
	List<Double> g
) {}

