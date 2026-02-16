using System.Collections.Immutable;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal class UnionRecordSourceModel : TypeSourceModelDeclaration {
	public required ImmutableList<SourceModelEnumCase> Cases { get; init; }

	public override required ESExprTagSet Tags { get; init; }

	public override ICodecGenerator Generator(SourceProductionContext context) {
		return new UnionRecordCodecGenerator {
			Context = context,
			TypeModel = this,
		};
	}

};
