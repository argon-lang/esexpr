using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

internal record InvalidTypeSourceModel : ITypeSourceModel {
	public required DiagnosticDescriptor Descriptor { get; init; }
	public required Location Location { get; init; }
	public required VList<string> MessageArgs { get; init; }

	public ICodecGenerator Generator(SourceProductionContext context, TypeInfoHandler overrideHandler) {
		return new ErrorCodecGenerator(context, this);
	}
}
