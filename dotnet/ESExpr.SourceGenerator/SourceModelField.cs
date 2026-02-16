using ESExpr.SourceGenerator.TypeClass;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal sealed record SourceModelField {
	public required string Name { get; init; }
	public required Location Location { get; init; }
	public required SourceModelType Type { get; init; }
	
	public required FieldMode Mode { get; init; }

	public abstract record KeywordMode {
		public sealed record Keyword: KeywordMode {
			public required string KeywordName { get; init; }
		}

		public sealed record Positional: KeywordMode {
			public required ESExprTagSet Tags { get; init; }
		}
	}

	public abstract record FieldMode {
		public sealed record Normal: FieldMode {
			public required SourceModelTypeClassInstance CodecInstance { get; init; }
			public required SourceModelSyntax<ExpressionSyntax>? DefaultValue { get; init; }
			public required KeywordMode KeywordMode { get; init; }
		}
		
		public sealed record Optional: FieldMode {
			public required SourceModelType ElementType { get; init; }
			public required SourceModelTypeClassInstance OptionalValueCodecInstance { get; init; }
			public required KeywordMode KeywordMode { get; init; }
		}

		public sealed record Dict: FieldMode {
			public required SourceModelType ElementType { get; init; }
			public required SourceModelTypeClassInstance DictCodecInstance { get; init; }
		}

		public sealed record Vararg: FieldMode {
			public required ESExprTagSet ElementTags { get; init; }
			public required SourceModelType ElementType { get; init; }
			public required SourceModelTypeClassInstance VarargCodecInstance { get; init; }
		}
	}
	
}
