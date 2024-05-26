package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.*;
import dev.argon.esexpr.generator.gen.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;


public abstract class TestBase {
	protected <T> void assertCodecMatch(ESExprCodec<T> codec, ESExpr expr, T value) throws DecodeException {
		assertCodecMatch(codec, expr, value, expr);
	}

	protected <T> void assertCodecMatch(ESExprCodec<T> codec, ESExpr expr, T value, ESExpr encExpr) throws DecodeException {
		assertEquals(encExpr, codec.encode(value));
		assertEquals(value, codec.decode(expr));
	}
}
