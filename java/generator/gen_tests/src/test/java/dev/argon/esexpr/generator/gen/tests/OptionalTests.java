package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.DecodeException;
import dev.argon.esexpr.ESExpr;
import dev.argon.esexpr.generator.gen.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptionalTests {

    @Test
    public void varargAfterOptional() throws DecodeException {
        ESExpr expr = new ESExpr.Constructor(
            "vararg-after-optional",
            List.of(
                new ESExpr.Int(BigInteger.valueOf(5)),
                new ESExpr.Float32(1.0f),
                new ESExpr.Float32(2.0f),
                new ESExpr.Float32(3.0f)
            ),
            java.util.Map.of()
        );
        VarargAfterOptional value = new VarargAfterOptional(
            Optional.of(5),
            List.of(1.0f, 2.0f, 3.0f)
        );

		assertEquals(expr, VarargAfterOptional.codec().encode(value));
		assertEquals(value, VarargAfterOptional.codec().decode(expr));

		expr = new ESExpr.Constructor(
            "vararg-after-optional",
            List.of(
                new ESExpr.Float32(1.0f),
                new ESExpr.Float32(2.0f),
                new ESExpr.Float32(3.0f)
            ),
            java.util.Map.of()
        );
        value = new VarargAfterOptional(
            Optional.empty(),
            List.of(1.0f, 2.0f, 3.0f)
        );

        assertEquals(expr, VarargAfterOptional.codec().encode(value));
        assertEquals(value, VarargAfterOptional.codec().decode(expr));

        expr = new ESExpr.Constructor(
            "vararg-after-optional",
            List.of(new ESExpr.Int(BigInteger.valueOf(5))),
            java.util.Map.of()
        );
        value = new VarargAfterOptional(Optional.of(5), List.of());

        assertEquals(expr, VarargAfterOptional.codec().encode(value));
        assertEquals(value, VarargAfterOptional.codec().decode(expr));

        expr = new ESExpr.Constructor(
            "vararg-after-optional",
            List.of(),
            java.util.Map.of()
        );
        value = new VarargAfterOptional(Optional.empty(), List.of());

        assertEquals(expr, VarargAfterOptional.codec().encode(value));
        assertEquals(value, VarargAfterOptional.codec().decode(expr));
    }

    @Test
    public void optionalAfterVararg() throws DecodeException {
        ESExpr expr = new ESExpr.Constructor(
            "optional-after-vararg",
            List.of(
                new ESExpr.Float32(1.0f),
                new ESExpr.Float32(2.0f),
                new ESExpr.Float32(3.0f),
                new ESExpr.Int(BigInteger.valueOf(5))
            ),
            java.util.Map.of()
        );
        OptionalAfterVararg value = new OptionalAfterVararg(
            List.of(1.0f, 2.0f, 3.0f),
            Optional.of(5)
        );

        assertEquals(expr, OptionalAfterVararg.codec().encode(value));
        assertEquals(value, OptionalAfterVararg.codec().decode(expr));

        expr = new ESExpr.Constructor(
            "optional-after-vararg",
            List.of(
                new ESExpr.Float32(1.0f),
                new ESExpr.Float32(2.0f),
                new ESExpr.Float32(3.0f)
            ),
            java.util.Map.of()
        );
        value = new OptionalAfterVararg(
            List.of(1.0f, 2.0f, 3.0f),
            Optional.empty()
        );

        assertEquals(expr, OptionalAfterVararg.codec().encode(value));
        assertEquals(value, OptionalAfterVararg.codec().decode(expr));

        expr = new ESExpr.Constructor(
            "optional-after-vararg",
            List.of(new ESExpr.Int(BigInteger.valueOf(5))),
            java.util.Map.of()
        );
        value = new OptionalAfterVararg(List.of(), Optional.of(5));

        assertEquals(expr, OptionalAfterVararg.codec().encode(value));
        assertEquals(value, OptionalAfterVararg.codec().decode(expr));

        expr = new ESExpr.Constructor(
            "optional-after-vararg",
            List.of(),
            java.util.Map.of()
        );
        value = new OptionalAfterVararg(List.of(), Optional.empty());

        assertEquals(expr, OptionalAfterVararg.codec().encode(value));
        assertEquals(value, OptionalAfterVararg.codec().decode(expr));
    }

    @Test
    public void requiredAfterVararg() throws DecodeException {
        ESExpr expr = new ESExpr.Constructor(
            "required-after-vararg",
            List.of(
                new ESExpr.Float32(1.0f),
                new ESExpr.Float32(2.0f),
                new ESExpr.Float32(3.0f),
                new ESExpr.Int(BigInteger.valueOf(5))
            ),
            java.util.Map.of()
        );
        RequiredAfterVararg value = new RequiredAfterVararg(
            List.of(1.0f, 2.0f, 3.0f),
            5
        );

        assertEquals(expr, RequiredAfterVararg.codec().encode(value));
        assertEquals(value, RequiredAfterVararg.codec().decode(expr));

        expr = new ESExpr.Constructor(
            "required-after-vararg",
            List.of(new ESExpr.Int(BigInteger.valueOf(5))),
            java.util.Map.of()
        );
        value = new RequiredAfterVararg(List.of(), 5);

        assertEquals(expr, RequiredAfterVararg.codec().encode(value));
        assertEquals(value, RequiredAfterVararg.codec().decode(expr));
    }

    @Test
    public void multipleVararg() throws DecodeException {
        ESExpr expr = new ESExpr.Constructor(
            "multiple-vararg",
            List.of(
                new ESExpr.Float32(1.0f),
                new ESExpr.Float32(2.0f),
                new ESExpr.Float32(3.0f),
                new ESExpr.Int(BigInteger.valueOf(1)),
                new ESExpr.Int(BigInteger.valueOf(2)),
                new ESExpr.Int(BigInteger.valueOf(3))
            ),
            java.util.Map.of()
        );
        MultipleVararg value = new MultipleVararg(
            List.of(1.0f, 2.0f, 3.0f),
            List.of(1, 2, 3)
        );

        assertEquals(expr, MultipleVararg.codec().encode(value));
        assertEquals(value, MultipleVararg.codec().decode(expr));

        expr = new ESExpr.Constructor(
            "multiple-vararg",
            List.of(
                new ESExpr.Float32(1.0f),
                new ESExpr.Float32(2.0f),
                new ESExpr.Float32(3.0f)
            ),
            java.util.Map.of()
        );
        value = new MultipleVararg(
            List.of(1.0f, 2.0f, 3.0f),
            List.of()
        );

        assertEquals(expr, MultipleVararg.codec().encode(value));
        assertEquals(value, MultipleVararg.codec().decode(expr));

        expr = new ESExpr.Constructor(
            "multiple-vararg",
            List.of(
                new ESExpr.Int(BigInteger.valueOf(1)),
                new ESExpr.Int(BigInteger.valueOf(2)),
                new ESExpr.Int(BigInteger.valueOf(3))
            ),
            java.util.Map.of()
        );
        value = new MultipleVararg(List.of(), List.of(1, 2, 3));

        assertEquals(expr, MultipleVararg.codec().encode(value));
        assertEquals(value, MultipleVararg.codec().decode(expr));

        expr = new ESExpr.Constructor(
            "multiple-vararg",
            List.of(),
            java.util.Map.of()
        );
        value = new MultipleVararg(List.of(), List.of());

        assertEquals(expr, MultipleVararg.codec().encode(value));
        assertEquals(value, MultipleVararg.codec().decode(expr));
    }
	
	@Test
	public void positionalDefaultValue() throws DecodeException {
		ESExpr expr = new ESExpr.Constructor(
			"positional-default-value",
			List.of(
				new ESExpr.Int(BigInteger.valueOf(10)),
				new ESExpr.Float32(20.0f),
				new ESExpr.Str("abc")
			),
			Map.of()
		);
		PositionalDefaultValue value = new PositionalDefaultValue(10, 20.0f, "abc");

		assertEquals(expr, PositionalDefaultValue.codec().encode(value));
		assertEquals(value, PositionalDefaultValue.codec().decode(expr));

		expr = new ESExpr.Constructor(
			"positional-default-value",
			List.of(
				new ESExpr.Float32(20.0f),
				new ESExpr.Str("abc")
			),
			Map.of()
		);
		value = new PositionalDefaultValue(4, 20.0f, "abc");

		assertEquals(expr, PositionalDefaultValue.codec().encode(value));
		assertEquals(value, PositionalDefaultValue.codec().decode(expr));

		expr = new ESExpr.Constructor(
			"positional-default-value",
			List.of(
				new ESExpr.Int(BigInteger.valueOf(10)),
				new ESExpr.Str("abc")
			),
			Map.of()
		);
		value = new PositionalDefaultValue(10, 5.0f, "abc");

		assertEquals(expr, PositionalDefaultValue.codec().encode(value));
		assertEquals(value, PositionalDefaultValue.codec().decode(expr));

		expr = new ESExpr.Constructor(
			"positional-default-value",
			List.of(
				new ESExpr.Str("abc")
			),
			Map.of()
		);
		value = new PositionalDefaultValue(4, 5.0f, "abc");

		assertEquals(expr, PositionalDefaultValue.codec().encode(value));
		assertEquals(value, PositionalDefaultValue.codec().decode(expr));
	}
}
