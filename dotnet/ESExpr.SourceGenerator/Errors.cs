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
		messageFormat: "ESExpr type '{0}' must be a record, a closed record, or an enum",
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
		title: "Invalid ESExpr closed record declaration",
		messageFormat: "ESExpr closed record type '{0}' must have no primary constructor parameters and at most a single private parameterless constructor",
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

	public static readonly DiagnosticDescriptor OverlappingFieldTags = new DiagnosticDescriptor(
		id: "ESX0008",
		title: "Overlapping tags",
		messageFormat: "Field '{0}' must have distinct tags from immediately preceding optional positional arguments. Tags: {1}. Previous tags: {2}.",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);

	public static readonly DiagnosticDescriptor OverlappingEnumConstructors = new DiagnosticDescriptor(
		id: "ESX0009",
		title: "Overlapping enum constructors",
		messageFormat: "Enum '{0}' has overlapping constructor tags. Tags: {1}. Previous tags: {2}.",
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

	public static readonly DiagnosticDescriptor KeywordWithDict = new DiagnosticDescriptor(
		id: "ESX0011",
		title: "Keyword with dict",
		messageFormat: "Keyword arguments cannot be used with dict arguments",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);

	public static readonly DiagnosticDescriptor MissingTagsAttribute = new DiagnosticDescriptor(
		id: "ESX0012",
		title: "Missing ESExprTagsAttribute",
		messageFormat: "Codec ({0}) for {1} must have an ESExprTagsAttribute",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);

	public static readonly DiagnosticDescriptor DuplicateKeyword = new DiagnosticDescriptor(
		id: "ESX0013",
		title: "Duplicate keyword",
		messageFormat: "Duplicate keyword argument \"{0}\"",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);

	public static readonly DiagnosticDescriptor ElementCodec = new DiagnosticDescriptor(
		id: "ESX0014",
		title: "Codec element",
		messageFormat: "Could not determine element type from codec type {0}",
		category: "SourceGenerator",
		defaultSeverity: DiagnosticSeverity.Error,
		isEnabledByDefault: true
	);
	private static DiagnosticDescriptor InputError(string id, string title) =>
		new(id, title, "{0}", "SourceGenerator", DiagnosticSeverity.Error, true);

	public static readonly DiagnosticDescriptor MissingRuntimeMember = InputError("ESX0015", "Missing codec accessor or runtime interface");
	public static readonly DiagnosticDescriptor MalformedMetadata = InputError("ESX0016", "Malformed attribute or tag metadata");
	public static readonly DiagnosticDescriptor UnsupportedSourceType = InputError("ESX0017", "Unsupported source type");
	public static readonly DiagnosticDescriptor InvalidFlag = InputError("ESX0018", "Invalid flag definition");
	public static readonly DiagnosticDescriptor OverlappingFlagMasks = InputError("ESX0019", "Overlapping flag masks");
	public static readonly DiagnosticDescriptor InvalidInlineDeclaration = InputError("ESX0020", "Invalid inline declaration");
	public static readonly DiagnosticDescriptor InternalFailure = InputError("SG001", "Source Generator Exception");
}
