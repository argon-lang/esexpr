using System;
using System.Linq;
using System.Text.RegularExpressions;

#if ESEXPR_SOURCE_GENERATOR
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static ESExpr.SourceGenerator.GenUtils;
#else
using System.Reflection;
#endif

#if ESEXPR_SOURCE_GENERATOR
namespace ESExpr.SourceGenerator;
#else
namespace ESExpr.Runtime;
#endif

internal static partial class NameUtils {

	private const string RegexPattern = "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])_(?=[0-9])";
	
#if ESEXPR_SOURCE_GENERATOR
	public static string GetConstructorName(TypeDeclarationSyntax decl, SemanticModel semanticModel) {
		if(
			GetAttribute(decl, "ESExpr.Runtime.ConstructorAttribute", semanticModel) is { ArgumentList.Arguments: var args } &&
			args.Count == 1 &&
			args[0].Expression is LiteralExpressionSyntax value
		) {
			return value.Token.ValueText;
		}
		else {
			return NameToKebabCase(decl.Identifier.Text);
		}
	}
	
	
	public static string GetConstructorName(INamedTypeSymbol symbol) {
		var attr = GetAttribute(symbol, "ESExpr.Runtime.ESExprConstructorAttribute");
		if(attr is not null && attr.ConstructorArguments.Length == 1 && attr.ConstructorArguments[0].Value is string name) {
			return name;
		}

		return NameToKebabCase(symbol.Name);
	}
	
	private static Regex NameSeparatorRegex() =>
		new Regex(RegexPattern, RegexOptions.CultureInvariant);
#else
	public static string GetConstructorName<T>(T value) where T : struct, Enum {
		var name = Enum.GetName(value);
		if(name == null) {
			throw new Exception("Could not get enum value name");
		}

		if(typeof(T).GetField(name)?.GetCustomAttribute<ConstructorAttribute>() is { } ctor) {
			return ctor.Name;
		}
		else {
			return NameToKebabCase(name);
		}
	}
	
	
	[GeneratedRegex(RegexPattern, RegexOptions.CultureInvariant)]
	private static partial Regex NameSeparatorRegex();
#endif
	

	public static string NameToKebabCase(string name) =>
		string.Join(
			"-",
			NameSeparatorRegex()
				.Split(name)
				.Select(s => s.ToLowerInvariant())
		);
}
