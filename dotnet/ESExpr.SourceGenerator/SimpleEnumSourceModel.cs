using System.Collections.Immutable;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal class SimpleEnumSourceModel : BaseTypeSourceModelDeclaration {
	public required ImmutableList<SourceModelSimpleEnumCase> Cases { get; init; }

	public override ICodecGenerator Generator(SourceProductionContext context, TypeInfoHandler overrideHandler) {
		return new SimpleEnumCodecGenerator(context, this);
	}

};
