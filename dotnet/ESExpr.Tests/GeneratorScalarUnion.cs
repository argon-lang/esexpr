using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public abstract partial record GeneratorScalarUnion {
	private GeneratorScalarUnion() { }

	[InlineValue]
	public sealed record Boolean : GeneratorScalarUnion {
		public required bool Value { get; init; }
	}

	[InlineValue]
	public sealed record Integer : GeneratorScalarUnion {
		public required int Value { get; init; }
	}

	[InlineValue]
	public sealed record Text : GeneratorScalarUnion {
		public required string Value { get; init; }
	}

	[InlineValue]
	public sealed record HalfValue : GeneratorScalarUnion {
		public required Half Value { get; init; }
	}

	[InlineValue]
	public sealed record Single : GeneratorScalarUnion {
		public required float Value { get; init; }
	}

	[InlineValue]
	public sealed record DoubleValue : GeneratorScalarUnion {
		public required double Value { get; init; }
	}

	[InlineValue]
	public sealed record Bytes : GeneratorScalarUnion {
		public required ImmutableArray<byte> Value { get; init; }
	}

	[InlineValue]
	public sealed record Words : GeneratorScalarUnion {
		public required ImmutableArray<ushort> Value { get; init; }
	}

	[InlineValue]
	public sealed record DWords : GeneratorScalarUnion {
		public required ImmutableArray<uint> Value { get; init; }
	}

	[InlineValue]
	public sealed record QWords : GeneratorScalarUnion {
		public required ImmutableArray<ulong> Value { get; init; }
	}

	[InlineValue]
	public sealed record WideWords : GeneratorScalarUnion {
		public required ImmutableArray<UInt128> Value { get; init; }
	}

	[TypeClassInstance]
	public static partial IESExprCodec<GeneratorScalarUnion> Codec { get; }
}
