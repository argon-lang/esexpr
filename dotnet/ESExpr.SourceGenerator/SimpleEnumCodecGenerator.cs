using System.Collections.Generic;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

internal class SimpleEnumCodecGenerator(SourceProductionContext context, SimpleEnumSourceModel typeModel) : ICodecGenerator {
	public void Generate() {
		var prevTags = new HashSet<string>();
		
		foreach(var c in typeModel.Cases) {
			if(prevTags.Contains(c.ConstructorName)) {
				context.ReportDiagnostic(Diagnostic.Create(
					Errors.OverlappingEnumConstructors,
					c.Location,
					typeModel.TypeName,
					c.ConstructorName,
					prevTags
				));
			}
			
			prevTags.Add(c.ConstructorName);
		}
	}
}
