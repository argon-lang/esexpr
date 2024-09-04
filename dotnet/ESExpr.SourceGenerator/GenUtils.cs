using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal static class GenUtils {
	public static AttributeData? GetAttribute(ISymbol decl, string name) {
		IEnumerable<AttributeData> attributes = decl.GetAttributes();
		return attributes.FirstOrDefault(a => {
			var attrType = a.AttributeClass;

			return attrType != null && attrType.ToDisplayString() == name;
		});
	}

	public static bool HasAttribute(ISymbol decl, string name) => GetAttribute(decl, name) != null;

	public static AttributeSyntax? GetAttribute(MemberDeclarationSyntax decl, string name, SemanticModel semanticModel) =>
		decl.AttributeLists
			.SelectMany(a => a.Attributes)
			.FirstOrDefault(a => {
				var sym = semanticModel.GetSymbolInfo(a);
				var ctorSymbol = sym.Symbol as IMethodSymbol;
				var attrType = ctorSymbol?.ContainingType;
				
				return attrType != null && attrType.ToDisplayString() == name;
			});

	public static bool HasAttribute(MemberDeclarationSyntax decl, string name, SemanticModel semanticModel) =>
		GetAttribute(decl, name, semanticModel) != null;
}
