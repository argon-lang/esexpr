using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public closed record GeneratorGenericInline<T> {
	[InlineValue]
	public sealed record ValueCase : GeneratorGenericInline<T> {
		public required T Value { get; init; }
	}
}

public static partial class GeneratorGenericInline {
	[TypeClassInstance]
	public static partial IESExprCodec<GeneratorGenericInline<T>> Codec<T>(IESExprCodec<T> itemCodec);
}

[ESExprCodec]
public closed record GeneratorGenericUnion<T> {
	public sealed record ValueCase : GeneratorGenericUnion<T> {
		public required T Value { get; init; }
	}
}

public static partial class GeneratorGenericUnion {
	[TypeClassInstance]
	public static partial IESExprCodec<GeneratorGenericUnion<T>> Codec<T>(IESExprCodec<T> itemCodec);
}

[ESExprCodec]
public sealed partial record GeneratorGenericInlineHolder {
	public required GeneratorGenericInline<int> Value { get; init; }
	[TypeClassInstance]
	public static partial IESExprCodec<GeneratorGenericInlineHolder> Codec { get; }
}

public class GeneratorGenericUnionTests : TestBase {
	[Test]
	public void GenericInlineUnionUsesTheProvidedCodec() {
		AssertCodecMatch(GeneratorGenericInline.Codec(IESExprCodec.IntCodec), new Expr.Int(7), new GeneratorGenericInline<int>.ValueCase { Value = 7 });
		AssertCodecMatch(GeneratorGenericInline.Codec(IESExprCodec.StrCodec), new Expr.Str("value"), new GeneratorGenericInline<string>.ValueCase { Value = "value" });
	}

	[Test]
	public void GenericConstructorUnionQualifiesItsNestedCase() {
		AssertCodecMatch(GeneratorGenericUnion.Codec(IESExprCodec.IntCodec),
			new Expr.Constructor("value-case", [new Expr.Int(7)], ImmutableDictionary<string, Expr>.Empty),
			new GeneratorGenericUnion<int>.ValueCase { Value = 7 });
	}

	[Test]
	public void ConcreteInlineUnionFieldSubstitutesTheGenericParameter() {
		AssertCodecMatch(GeneratorGenericInlineHolder.Codec,
			new Expr.Constructor("generator-generic-inline-holder", [new Expr.Int(7)], ImmutableDictionary<string, Expr>.Empty),
			new GeneratorGenericInlineHolder { Value = new GeneratorGenericInline<int>.ValueCase { Value = 7 } });
	}
}

[ESExprCodec]
public sealed partial record GeneratorNullableInlineHolder {
	public required GeneratorGenericInline<string?> Value { get; init; }
	[TypeClassInstance]
	public static partial IESExprCodec<GeneratorNullableInlineHolder> Codec { get; }
}

public class GeneratorNullableInlineTests : TestBase {
	[Test]
	public void NullableGenericArgumentProducesACompilableCodec() {
		AssertCodecMatch(GeneratorNullableInlineHolder.Codec,
			new Expr.Constructor("generator-nullable-inline-holder", [new Expr.Str("value")], ImmutableDictionary<string, Expr>.Empty),
			new GeneratorNullableInlineHolder { Value = new GeneratorGenericInline<string?>.ValueCase { Value = "value" } });
	}
}
