using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

internal class SimpleEnumCodecGenerator : ICodecGenerator {
	public SimpleEnumCodecGenerator(SourceProductionContext context, SimpleEnumSourceModel typeModel) { }

	// Enum codecs use the runtime implementation; validation runs before emission.
	public void Generate() { }
}
