using System.Collections.Generic;
using System.Collections.Immutable;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator.TypeClass;

internal abstract record SourceModelTypeClassInstance {
	public sealed record Method: SourceModelTypeClassInstance {
		public required ImmutableList<SourceModelType> TypeArguments { get; init; }
		public required ImmutableList<SourceModelTypeClassInstance> Arguments { get; init; }
		public required SourceModelType DeclaringType { get; init; }
		public required string Name { get; init; }
	}

	public sealed record Property : SourceModelTypeClassInstance {
		public required SourceModelType DeclaringType { get; init; }
		public required string Name { get; init; }
	}

	public sealed record Local : SourceModelTypeClassInstance {
		public required string Name { get; init; }
	}
}
