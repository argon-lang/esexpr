package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.*;
import dev.argon.esexpr.generator.gen.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;


public abstract class TestBase {
	protected <T> void assertCodecMatch(ESExprCodec<T> codec, ESExpr expr, T value) throws DecodeException {
		assertEquals(expr, codec.encode(value));
		assertEquals(value, codec.decode(expr));
	}
}
