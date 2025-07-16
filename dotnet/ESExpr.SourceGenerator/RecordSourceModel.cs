using System.Collections.Immutable;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

internal class RecordSourceModel : TypeSourceModelDeclaration {
	public required string ConstructorName { get; init; }
	public required ImmutableList<SourceModelField> Fields { get; init; }

	public override ICodecGenerator Generator(SourceProductionContext context, TypeInfoHandler overrideHandler) {
		return new RecordCodecGenerator {
			Context = context,
			TypeInfoHandler = overrideHandler,
			TypeModel = this,
		};
	}
};
