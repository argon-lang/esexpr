using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal interface ITypeSourceModel {
	ICodecGenerator Generator(SourceProductionContext context);
}
