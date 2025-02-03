using System.Linq;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

public class ErrorCodecGenerator(SourceProductionContext context, InvalidTypeSourceModel error) : ICodecGenerator {
	public void Generate() {
		context.ReportDiagnostic(Diagnostic.Create(
			error.Descriptor,
			error.Location,
			error.MessageArgs.Cast<object>().ToArray()
		));
	}
}
