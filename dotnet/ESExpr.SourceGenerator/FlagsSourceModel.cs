using System.Collections.Immutable;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

internal class FlagsSourceModel : TypeSourceModelDeclaration {
	public required ImmutableList<SourceModelFlagsField> Fields { get; init; }

	public override ICodecGenerator Generator(SourceProductionContext context, TypeInfoHandler overrideHandler) {
		return new FlagsCodecGenerator {
			Context = context,
			TypeInfoHandler = overrideHandler,
			TypeModel = this,
		};
	}
}
