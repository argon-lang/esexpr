using System.Collections.Immutable;
using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal abstract class BaseTypeSourceModelDeclaration : ITypeSourceModel {
	public required ImmutableList<SourceModelSyntax<UsingDirectiveSyntax>> Usings { get; init; }
	public required ImmutableList<string> Namespace { get; init; }
	public required string TypeName { get; init; }
	public required Location Location { get; init; }

	public abstract ICodecGenerator Generator(SourceProductionContext context, TypeInfoHandler overrideHandler);

	public virtual SourceModelType SourceModelType =>
		new SourceModelType.NamedSymbol(Namespace, TypeName) {
			TypeArguments = [],
			IsEnum = false,
		};
}
