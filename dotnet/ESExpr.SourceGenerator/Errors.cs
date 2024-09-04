using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

internal static class Errors {
	public static readonly DiagnosticDescriptor InvalidMappedType = new DiagnosticDescriptor(
		id: "ESX0001",
		title: "Invalid mapped type",
		messageFormat: "ESExprTypeMappingAttribute for '{0}' must have a single Type argument",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor InvalidNestedESExprType = new DiagnosticDescriptor(
		id: "ESX0002",
		title: "Invalid nested ESExpr type",
		messageFormat: "ESExpr type '{0}' must not be nested",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor InvalidESExprTypeDeclaration = new DiagnosticDescriptor(
		id: "ESX0003",
		title: "Invalid ESExpr type declaration",
		messageFormat: "ESExpr type '{0}' must be a record, an abstract record, or an enum",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor InvalidESExprRecordDeclaration = new DiagnosticDescriptor(
		id: "ESX0004",
		title: "Invalid ESExpr record declaration",
		messageFormat: "ESExpr record type '{0}' must be sealed and contain only named fields",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor InvalidESExprEnumDeclaration = new DiagnosticDescriptor(
		id: "ESX0005",
		title: "Invalid ESExpr abstract record declaration",
		messageFormat: "ESExpr abstract record type '{0}' must contain only a single private constructor",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor CouldNotDetermineCodec = new DiagnosticDescriptor(
		id: "ESX0006",
		title: "Could not determine ESExprCodec",
		messageFormat: "Could not determine {0} for type {1}",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor InvalidInlineValue = new DiagnosticDescriptor(
		id: "ESX0007",
		title: "Invalid inline value",
		messageFormat: "Inline value must contain exactly one property",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor MultipleVarargs = new DiagnosticDescriptor(
		id: "ESX0008",
		title: "Multiple varargs",
		messageFormat: "Only one vararg parameter is allowed",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor PositionalAfterVararg = new DiagnosticDescriptor(
		id: "ESX0009",
		title: "Positional after vararg",
		messageFormat: "Positional parmaeters must come before varargs",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor MultipleDict = new DiagnosticDescriptor(
		id: "ESX0010",
		title: "Multiple dict",
		messageFormat: "Only one dict parameter is allowed",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	
	public static readonly DiagnosticDescriptor KeywordAfterDict = new DiagnosticDescriptor(
		id: "ESX0011",
		title: "Keyword after dict",
		messageFormat: "Keyword parameters must come before dict",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
}
