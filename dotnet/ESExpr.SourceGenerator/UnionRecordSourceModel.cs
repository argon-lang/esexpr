using System.Collections.Immutable;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal class UnionRecordSourceModel : TypeSourceModelDeclaration {
	public required ImmutableList<SourceModelEnumCase> Cases { get; init; }

	public override ICodecGenerator Generator(SourceProductionContext context, TypeInfoHandler overrideHandler) {
		return new UnionRecordCodecGenerator {
			Context = context,
			TypeInfoHandler = overrideHandler,
			TypeModel = this,
		};
	}

};
