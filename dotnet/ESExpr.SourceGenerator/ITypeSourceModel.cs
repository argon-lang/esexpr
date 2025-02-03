using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

public interface ITypeSourceModel {
	ICodecGenerator Generator(SourceProductionContext context, CodecOverrideHandler overrideHandler);
}
