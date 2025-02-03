using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

public interface ITypeSourceModelDeclaration : ITypeSourceModel {
	VList<SourceModelSyntax<UsingDirectiveSyntax>> Usings { get; }
	VList<string> Namespace { get; }
	string TypeName { get; }
	Location Location { get; }
	VList<SourceModelSyntax<TypeParameterSyntax>> TypeParameters { get; }
}
