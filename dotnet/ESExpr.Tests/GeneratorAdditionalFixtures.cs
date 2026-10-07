using System.Collections.Immutable;
using ESExpr.Runtime;

// Exercise code generation without a containing namespace and with a method accessor.
[ESExprCodec]
[Constructor("global-fixture")]
public sealed partial record GeneratorGlobalRecord {
	[Keyword("answer")]
	[DefaultValue("42")]
	public int Value { get; init; }

	[TypeClassInstance]
	public static partial IESExprCodec<GeneratorGlobalRecord> CreateCodec();
}

namespace ESExpr.Tests.GeneratorFixtures {
	namespace Nested {
		[ESExprCodec]
		public sealed partial record NestedRecord {
			public required string Value { get; init; }
			[TypeClassInstance]
			public static partial IESExprCodec<NestedRecord> Codec { get; }
		}
	}
}

namespace ESExpr.Tests {
	[ESExprCodec]
	public sealed partial record GeneratorNullableField {
		public required string? Value { get; init; }
		[TypeClassInstance]
		public static partial IESExprCodec<GeneratorNullableField> Codec { get; }
	}

	[ESExprCodec]
	public sealed partial record GeneratorKeywordThenDict {
		[Keyword("first")]
		public required int First { get; init; }
		[Dict]
		public required ImmutableDictionary<string, int> Rest { get; init; }
		[TypeClassInstance]
		public static partial IESExprCodec<GeneratorKeywordThenDict> Codec { get; }
	}

	[ESExprCodec]
	public abstract partial record GeneratorAnyUnion {
		private GeneratorAnyUnion() { }
		[InlineValue]
		public sealed record ValueCase : GeneratorAnyUnion {
			public required Expr Value { get; init; }
		}
		[TypeClassInstance]
		public static partial IESExprCodec<GeneratorAnyUnion> Codec { get; }
	}

	[ESExprCodec]
	public abstract partial record GeneratorNullableUnion {
		private GeneratorNullableUnion() { }
		[InlineValue]
		public sealed record ValueCase : GeneratorNullableUnion {
			public required Option<int> Value { get; init; }
		}
		[TypeClassInstance]
		public static partial IESExprCodec<GeneratorNullableUnion> Codec { get; }
	}

	[ESExprCodec(Flags = true)]
	public sealed partial record GeneratorWideFlags {
		[FlagBits("18446744073709551616")]
		public required bool High { get; init; }
		public required Choice Mode { get; init; }
		public enum Choice {
			[FlagBits("0")]
			None,
			[FlagBits("36893488147419103232")]
			High,
		}
		[TypeClassInstance]
		public static partial IESExprCodec<GeneratorWideFlags> Codec { get; }
	}
}
