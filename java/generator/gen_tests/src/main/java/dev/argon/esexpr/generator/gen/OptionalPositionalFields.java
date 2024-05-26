package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;
import java.util.Optional;

@ESExprCodecGen
public record OptionalPositionalFields(
	Optional<Boolean> a,
	Optional<Byte> b,
	Optional<@Unsigned Byte> b2,
	Optional<Short> c,
	Optional<@Unsigned Short> c2,
	Optional<Integer> d,
	Optional<@Unsigned Integer> d2,
	Optional<Long> e,
	Optional<@Unsigned Long> e2,
	Optional<Float> f,
	Optional<Double> g
) {}

