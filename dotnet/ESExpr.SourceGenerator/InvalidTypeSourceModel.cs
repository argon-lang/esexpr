using System.Collections.Immutable;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

internal class InvalidTypeSourceModel : ITypeSourceModel {
	public required DiagnosticDescriptor Descriptor { get; init; }
	public required Location Location { get; init; }
	public required ImmutableList<string> MessageArgs { get; init; }

	public ICodecGenerator Generator(SourceProductionContext context) {
		return new ErrorCodecGenerator(context, this);
	}
}
