using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal abstract class TypeSourceModelDeclaration : ITypeSourceModel {
	public required VList<SourceModelSyntax<UsingDirectiveSyntax>> Usings { get; init; }
	public required VList<string> Namespace { get; init; }
	public required string TypeName { get; init; }
	public required Location Location { get; init; }
	public required VList<SourceModelSyntax<TypeParameterSyntax>> TypeParameters { get; init; }

	public abstract ICodecGenerator Generator(SourceProductionContext context, TypeInfoHandler overrideHandler);

	public SourceModelType SourceModelType =>
		new SourceModelType.NamedSymbol(Namespace, TypeName) {
			TypeArguments = VList.From<SourceModelType>(
				TypeParameters
					.Select(tp => new SourceModelType.TypeParameter(tp.Syntax.Identifier.Text))
			),
			IsEnum = false,
		};
}
