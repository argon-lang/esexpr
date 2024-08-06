package dev.argon.esexpr.generator.gen.tests;

import dev.argon.esexpr.*;
import dev.argon.esexpr.generator.gen.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ConstructorNameTests extends TestBase {
	@Test
	public void constructorNameConversion() throws Throwable {
		assertEquals(Set.of(new ESExprTag.Constructor("primitive-fields")), PrimitiveFields.codec().tags());
		assertCodecMatch(
			PrimitiveFields.codec(),
			new ESExpr.Constructor(
				"primitive-fields",
				List.of(
					new ESExpr.Bool(false),
					new ESExpr.Int(BigInteger.ZERO),
					new ESExpr.Int(BigInteger.ZERO),
					new ESExpr.Int(BigInteger.ZERO),
					new ESExpr.Int(BigInteger.ZERO),
					new ESExpr.Int(BigInteger.ZERO),
					new ESExpr.Int(BigInteger.ZERO),
					new ESExpr.Int(BigInteger.ZERO),
					new ESExpr.Int(BigInteger.ZERO),
					new ESExpr.Float32(0.0f),
					new ESExpr.Float64(0.0)
				),
				Map.of()
			),
			new PrimitiveFields(false, (byte)0, (byte)0, (short)0, (short)0, 0, 0, 0L, 0L, 0.0f, 0.0)
		);

		assertEquals(Set.of(new ESExprTag.Constructor("constructor-name123-conversion")), ConstructorName123Conversion.codec().tags());
		assertCodecMatch(
			ConstructorName123Conversion.codec(),
			new ESExpr.Constructor(
				"constructor-name123-conversion",
				List.of(new ESExpr.Int(BigInteger.ZERO)),
				Map.of()
			),
			new ConstructorName123Conversion(0)
		);

		assertEquals(Set.of(new ESExprTag.Constructor("constructor-name-456-conversion")), ConstructorName_456Conversion.codec().tags());
		assertCodecMatch(
			ConstructorName_456Conversion.codec(),
			new ESExpr.Constructor(
				"constructor-name-456-conversion",
				List.of(new ESExpr.Int(BigInteger.ZERO)),
				Map.of()
			),
			new ConstructorName_456Conversion(0)
		);

		assertEquals(Set.of(new ESExprTag.Constructor("my-case-a"), new ESExprTag.Constructor("my-case-b")), MyEnum.codec().tags());
		assertCodecMatch(
			MyEnum.codec(),
			new ESExpr.Constructor(
				"my-case-a",
				List.of(new ESExpr.Int(BigInteger.ZERO)),
				Map.of()
			),
			new MyEnum.MyCaseA(0)
		);
		assertCodecMatch(
			MyEnum.codec(),
			new ESExpr.Constructor(
				"my-case-b",
				List.of(new ESExpr.Float32(0.0f)),
				Map.of()
			),
			new MyEnum.MyCaseB(0.0f)
		);

	}

	@Test
	public void customNames() throws Throwable {
		assertEquals(Set.of(new ESExprTag.Constructor("my-ctor-name")), CustomConstructorRecord.codec().tags());
		assertCodecMatch(
			CustomConstructorRecord.codec(),
			new ESExpr.Constructor(
				"my-ctor-name",
				List.of(new ESExpr.Bool(true)),
				Map.of()
			),
			new CustomConstructorRecord(true)
		);


		assertEquals(Set.of(new ESExprTag.Constructor("my-ctor-name"), new ESExprTag.Constructor("normal-name-case")), CustomConstructorEnum.codec().tags());
		assertCodecMatch(
			CustomConstructorEnum.codec(),
			new ESExpr.Constructor(
				"my-ctor-name",
				List.of(new ESExpr.Int(BigInteger.ZERO)),
				Map.of()
			),
			new CustomConstructorEnum.CustomNameCase(0)
		);
		assertCodecMatch(
			CustomConstructorEnum.codec(),
			new ESExpr.Constructor(
				"normal-name-case",
				List.of(new ESExpr.Float32(0.0f)),
				Map.of()
			),
			new CustomConstructorEnum.NormalNameCase(0.0f)
		);
	}

	@Test
	public void simpleEnumNames() throws Throwable {
		assertEquals(Set.of(new ESExprTag.Str()), MySimpleEnum.codec().tags());
		assertCodecMatch(
			MySimpleEnum.codec(),
			new ESExpr.Str("test-name123"),
			MySimpleEnum.TEST_NAME123
		);
		assertCodecMatch(
			MySimpleEnum.codec(),
			new ESExpr.Str("test-name-123"),
			MySimpleEnum.TEST_NAME_123
		);
		assertCodecMatch(
			MySimpleEnum.codec(),
			new ESExpr.Str("other-name-here"),
			MySimpleEnum.OTHER_NAME_HERE
		);
		assertCodecMatch(
			MySimpleEnum.codec(),
			new ESExpr.Str("my-custom-name"),
			MySimpleEnum.CUSTOM_NAME
		);
	}
}
