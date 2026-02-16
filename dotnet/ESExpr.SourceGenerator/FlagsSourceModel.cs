using System.Collections.Immutable;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

internal class FlagsSourceModel : TypeSourceModelDeclaration {
	public required ImmutableList<SourceModelFlagsField> Fields { get; init; }

	public override ESExprTagSet Tags { get; init; } = ESExprTagSet.Create([new ESExprTag.Int()]);


	public override ICodecGenerator Generator(SourceProductionContext context) {
		return new FlagsCodecGenerator {
			Context = context,
			TypeModel = this,
		};
	}
}
