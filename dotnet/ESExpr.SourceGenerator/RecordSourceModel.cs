using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

public record RecordSourceModel : ITypeSourceModelDeclaration {
	public required VList<SourceModelSyntax<UsingDirectiveSyntax>> Usings { get; init; }
	public required VList<string> Namespace { get; init; }
	public required string TypeName { get; init; }
	public required Location Location { get; init; }
	public required string ConstructorName { get; init; }
	public required int ParameterCount { get; init; }
	public required VList<SourceModelSyntax<TypeParameterSyntax>> TypeParameters { get; init; }
	public required VList<SourceModelField> Fields { get; init; }

	public ICodecGenerator Generator(SourceProductionContext context, CodecOverrideHandler overrideHandler) {
		return new RecordCodecGenerator {
			Context = context,
			CodecOverrideHandler = overrideHandler,
			TypeModel = this,
		};
	}
};
