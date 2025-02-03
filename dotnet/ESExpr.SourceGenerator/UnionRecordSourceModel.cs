using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

public record UnionRecordSourceModel : ITypeSourceModelDeclaration {
	public required VList<SourceModelSyntax<UsingDirectiveSyntax>> Usings { get; init; }
	
	public required VList<string> Namespace { get; init; }
	public required string TypeName { get; init; }
	public required Location Location { get; init; }
	public required VList<SourceModelSyntax<TypeParameterSyntax>> TypeParameters { get; init; }
	public required VList<SourceModelEnumCase> Cases { get; init; }

	public ICodecGenerator Generator(SourceProductionContext context, CodecOverrideHandler overrideHandler) {
		return new UnionRecordCodecGenerator {
			Context = context,
			CodecOverrideHandler = overrideHandler,
			TypeModel = this,
		};
	}

};
