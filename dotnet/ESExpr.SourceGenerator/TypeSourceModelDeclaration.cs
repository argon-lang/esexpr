using System.Collections.Immutable;
using System.Linq;
using ESExpr.SourceGenerator.TypeClass;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal abstract class TypeSourceModelDeclaration : BaseTypeSourceModelDeclaration {
	public required ImmutableList<SourceModelSyntax<TypeParameterSyntax>> TypeParameters { get; init; }
	public abstract ESExprTagSet Tags { get; init; }
	
	public required SourceModelTypeClassInstanceAccessor TypeClassInstanceAccessor { get; init; }

	public override SourceModelType SourceModelType =>
		new SourceModelType.NamedSymbol(new SourceModelType.NamespaceSymbolParent(Namespace), TypeName) {
			TypeArguments =
				TypeParameters
					.Select(tp => new SourceModelType.TypeParameter(tp.Syntax.Identifier.Text))
					.ToImmutableList<SourceModelType>(),
			IsEnum = false,
		};
}
