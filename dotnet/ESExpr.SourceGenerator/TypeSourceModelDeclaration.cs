using System.Collections.Immutable;
using ESExpr.SourceGenerator.TypeClass;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal abstract class TypeSourceModelDeclaration : BaseTypeSourceModelDeclaration {
	public required ImmutableList<SourceModelSyntax<TypeParameterSyntax>> TypeParameters { get; init; }
	public abstract ESExprTagSet Tags { get; init; }

	public required SourceModelTypeClassInstanceAccessor TypeClassInstanceAccessor { get; init; }

}
