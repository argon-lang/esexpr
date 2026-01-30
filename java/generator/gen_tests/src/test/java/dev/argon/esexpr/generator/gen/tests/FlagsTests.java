package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.DecodeException;
import dev.argon.esexpr.ESExpr;
import dev.argon.esexpr.generator.gen.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FlagsTests {

    @Test
    public void twoFlags() throws DecodeException {
        // 00 -> 0
        var value = new TwoFlags(false, false);
        ESExpr expr = new ESExpr.Int(BigInteger.ZERO);
        assertEquals(expr, TwoFlags.codec().encode(value));
        assertEquals(value, TwoFlags.codec().decode(expr));

        // 01 -> 1
        value = new TwoFlags(true, false);
        expr = new ESExpr.Int(BigInteger.ONE);
        assertEquals(expr, TwoFlags.codec().encode(value));
        assertEquals(value, TwoFlags.codec().decode(expr));

        // 10 -> 2
        value = new TwoFlags(false, true);
        expr = new ESExpr.Int(BigInteger.TWO);
        assertEquals(expr, TwoFlags.codec().encode(value));
        assertEquals(value, TwoFlags.codec().decode(expr));

        // 11 -> 3
        value = new TwoFlags(true, true);
        expr = new ESExpr.Int(BigInteger.valueOf(3));
        assertEquals(expr, TwoFlags.codec().encode(value));
        assertEquals(value, TwoFlags.codec().decode(expr));
    }

    @Test
    public void flagsWithEnum() throws DecodeException {
        // a = false
        var value = new FlagsWithEnum(false, FlagsWithEnum.MyFlagsEnum.A);
        ESExpr expr = new ESExpr.Int(BigInteger.ZERO);
        assertEquals(expr, FlagsWithEnum.codec().encode(value));
        assertEquals(value, FlagsWithEnum.codec().decode(expr));

        value = new FlagsWithEnum(false, FlagsWithEnum.MyFlagsEnum.B);
        expr = new ESExpr.Int(BigInteger.TWO);
        assertEquals(expr, FlagsWithEnum.codec().encode(value));
        assertEquals(value, FlagsWithEnum.codec().decode(expr));

        value = new FlagsWithEnum(false, FlagsWithEnum.MyFlagsEnum.C);
        expr = new ESExpr.Int(BigInteger.valueOf(4));
        assertEquals(expr, FlagsWithEnum.codec().encode(value));
        assertEquals(value, FlagsWithEnum.codec().decode(expr));

        // a = true
        value = new FlagsWithEnum(true, FlagsWithEnum.MyFlagsEnum.A);
        expr = new ESExpr.Int(BigInteger.ONE);
        assertEquals(expr, FlagsWithEnum.codec().encode(value));
        assertEquals(value, FlagsWithEnum.codec().decode(expr));

        value = new FlagsWithEnum(true, FlagsWithEnum.MyFlagsEnum.B);
        expr = new ESExpr.Int(BigInteger.valueOf(3));
        assertEquals(expr, FlagsWithEnum.codec().encode(value));
        assertEquals(value, FlagsWithEnum.codec().decode(expr));

        value = new FlagsWithEnum(true, FlagsWithEnum.MyFlagsEnum.C);
        expr = new ESExpr.Int(BigInteger.valueOf(5));
        assertEquals(expr, FlagsWithEnum.codec().encode(value));
        assertEquals(value, FlagsWithEnum.codec().decode(expr));

		assertThrows(DecodeException.class, () -> FlagsWithEnum.codec().decode(new ESExpr.Int(BigInteger.valueOf(6))));
    }
}
