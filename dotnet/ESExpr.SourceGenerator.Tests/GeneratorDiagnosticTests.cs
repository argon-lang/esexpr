using ESExpr.Runtime;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;

namespace ESExpr.SourceGenerator.Tests;

public class GeneratorDiagnosticTests {
	private const string Imports = """
		using System;
		using System.Collections.Immutable;
		using ESExpr.Runtime;
		""";

	private static string Record(string fields) => $$"""
		[ESExprCodec]
		public sealed partial record InvalidRecord {
			{{fields}}
			[TypeClassInstance]
			public static partial IESExprCodec<InvalidRecord> Codec { get; }
		}
		""";

	private static string Union(string cases) => $$"""
		[ESExprCodec]
		public abstract partial record InvalidUnion {
			private InvalidUnion() { }
			{{cases}}
			[TypeClassInstance]
			public static partial IESExprCodec<InvalidUnion> Codec { get; }
		}
		""";

	private static string Flags(string fields) => Record(fields).Replace("[ESExprCodec]", "[ESExprCodec(Flags = true)]", StringComparison.Ordinal);

	private static IEnumerable<TestCaseData> InvalidInputs() {
		yield return new TestCaseData("ESX0002", "must not be nested", "public class Outer { " + Record("") + " }").SetName("NestedRecordIsRejected");
		yield return new TestCaseData("ESX0003", "must be a record", "[ESExprCodec] public struct InvalidStruct { [TypeClassInstance] public static IESExprCodec<InvalidStruct> Codec => null!; }").SetName("StructIsRejected");
		yield return new TestCaseData("ESX0004", "only named fields", "[ESExprCodec] public sealed partial record Positional(int Value) { [TypeClassInstance] public static partial IESExprCodec<Positional> Codec { get; } }").SetName("PositionalRecordIsRejected");
		yield return new TestCaseData("ESX0005", "single private constructor", "[ESExprCodec] public abstract partial record MissingConstructor { [TypeClassInstance] public static partial IESExprCodec<MissingConstructor> Codec { get; } }").SetName("UnionRequiresPrivateConstructor");
		yield return new TestCaseData("ESX0005", "single private constructor", "[ESExprCodec] public abstract partial record PublicConstructor { public PublicConstructor() { } [TypeClassInstance] public static partial IESExprCodec<PublicConstructor> Codec { get; } }").SetName("UnionRejectsPublicConstructor");
		yield return new TestCaseData("ESX0005", "single private constructor", "[ESExprCodec] public abstract partial record TwoConstructors { private TwoConstructors() { } private TwoConstructors(int n) { } [TypeClassInstance] public static partial IESExprCodec<TwoConstructors> Codec { get; } }").SetName("UnionRejectsMultipleConstructors");
		yield return new TestCaseData("ESX0013", "Duplicate keyword", Record("[Keyword(\"same\")] public required int A { get; init; } [Keyword(\"same\")] public required int B { get; init; }")).SetName("DuplicateRequiredKeywordsAreRejected");
		yield return new TestCaseData("ESX0013", "Duplicate keyword", Record("[Optional, Keyword(\"same\")] public Option<int> A { get; init; } [Optional, Keyword(\"same\")] public Option<int> B { get; init; }")).SetName("DuplicateOptionalKeywordsAreRejected");
		yield return new TestCaseData("ESX0009", "overlapping", Union("[Constructor(\"same\")] public sealed record A : InvalidUnion; [Constructor(\"same\")] public sealed record B : InvalidUnion;")).SetName("DuplicateUnionConstructorsAreRejected");
		yield return new TestCaseData("ESX0009", "overlapping", Union("[InlineValue] public sealed record A : InvalidUnion { public required int Value { get; init; } } [InlineValue] public sealed record B : InvalidUnion { public required int Value { get; init; } }")).SetName("OverlappingInlineScalarsAreRejected");
		yield return new TestCaseData("ESX0009", "overlapping", Union("[InlineValue] public sealed record A : InvalidUnion { public required Expr Value { get; init; } } public sealed record B : InvalidUnion;")).SetName("UniversalInlineCaseOverlapsConstructor");
		yield return new TestCaseData("SG001", "required positional argument", Union("[InlineValue] public sealed record A : InvalidUnion { [Keyword] public required int Value { get; init; } }")).SetName("InlineKeywordIsRejected");
		yield return new TestCaseData("SG001", "required positional argument", Union("[InlineValue] public sealed record A : InvalidUnion { [Optional] public Option<int> Value { get; init; } }")).SetName("InlineOptionalIsRejected");
		yield return new TestCaseData("SG001", "at least one bit", Flags("[FlagBits(0)] public required bool A { get; init; }")).SetName("ZeroFlagMaskIsRejected");
		yield return new TestCaseData("SG001", "only one bit", Flags("[FlagBits(3)] public required bool A { get; init; }")).SetName("MultipleBitFlagMaskIsRejected");
		yield return new TestCaseData("SG001", "Duplicate enum mask", Flags("public required Choice A { get; init; } public required Choice B { get; init; } public enum Choice { [FlagBits(1)] One }")).SetName("OverlappingEnumMasksAreRejected");
		yield return new TestCaseData("ESX0011", "Keyword arguments", Record("[Dict] public required ImmutableDictionary<string, int> A { get; init; } [Optional, Keyword] public Option<int> B { get; init; }")).SetName("OptionalKeywordAfterDictIsRejected");
		foreach(var (constraint, argument) in new[] { ("struct", "string"), ("class", "int"), ("unmanaged", "string"), ("new()", "string"), ("IDisposable", "int") }) {
			var provider = $$"""
				public sealed record Wrapper<T>(T Value);
				public static class Wrapper {
					[TypeClassInstance, ESExprTags(Constructors = ["wrapper"])]
					public static IESExprCodec<Wrapper<T>> Codec<T>(IESExprCodec<T> codec) where T : {{constraint}} => throw new NotImplementedException();
				}
				""";
			yield return new TestCaseData("CS8785", "Could not resolve codec", provider + Record($"public required Wrapper<{argument}> Value {{ get; init; }}")).SetName($"UnsatisfiedCodecConstraint_{constraint}");
		}
		yield return new TestCaseData("CS8785", "Could not resolve codec", """
			public sealed record Wrapper<T>(T Value);
			public static class Wrapper {
				[TypeClassInstance, ESExprTags(Constructors = ["wrapper"])]
				public static IESExprCodec<Wrapper<T>> Codec<T>(IESExprCodec<Wrapper<T>> codec) => throw new NotImplementedException();
			}
			""" + Record("public required Wrapper<int> Value { get; init; }")).SetName("RecursiveCodecResolutionStopsAtDepthLimit");
		yield return new TestCaseData("CS8785", "Could not load type tags", """
			public sealed record Untagged(int Value) {
				[TypeClassInstance] public static IESExprCodec<Untagged> Codec => throw new NotImplementedException();
			}
			""" + Record("public required Untagged Value { get; init; }")).SetName("CustomCodecRequiresTagMetadata");
		yield return new TestCaseData("CS8785", "Unknown enum value", """
			public sealed record InvalidTags(int Value) {
				[TypeClassInstance, ESExprTags(Scalar = [(ESExprTag.ScalarType)999])]
				public static IESExprCodec<InvalidTags> Codec => throw new NotImplementedException();
			}
			""" + Record("public required InvalidTags Value { get; init; }")).SetName("UnknownScalarTagIsRejected");
		yield return new TestCaseData("SG001", "Duplicate flag mask", Flags("[FlagBits(1)] public required bool A { get; init; } [FlagBits(1)] public required bool B { get; init; }")).SetName("DuplicateBooleanFlagMasksAreRejected");
		yield return new TestCaseData("SG001", "Duplicate flag mask", Flags("public required Choice A { get; init; } [FlagBits(1)] public required bool B { get; init; } public enum Choice { [FlagBits(1)] One }")).SetName("BooleanMaskOverlappingEarlierEnumIsRejected");
		yield return new TestCaseData("SG001", "Duplicate enum mask", Flags("[FlagBits(1)] public required bool A { get; init; } public required Choice B { get; init; } public enum Choice { [FlagBits(1)] One }")).SetName("EnumMaskOverlappingEarlierBooleanIsRejected");
		foreach(var (metadata, message) in new[] {
			("Constructors = [null!]", "Expected string value for constructor tag"),
			("UnionWithTypeParameters = [null!]", "Expected string value for constructor tag"),
			("UnionWithTypeParameters = [\"Unknown\"]", "Unknown type parameter"),
		}) {
			var provider = $$"""
				public sealed record BadMetadata<T>(T Value);
				public static class BadMetadata {
					[TypeClassInstance, ESExprTags({{metadata}})]
					public static IESExprCodec<BadMetadata<T>> Codec<T>(IESExprCodec<T> itemCodec) => throw new NotImplementedException();
				}
				""";
			yield return new TestCaseData("CS8785", message, provider + Record("public required BadMetadata<int> Value { get; init; }")).SetName($"InvalidTagMetadata_{metadata}");
		}
		yield return new TestCaseData("CS8785", "Could not resolve codec", """
			#nullable enable
			public sealed record NotNullBox<T>(T Value);
			public static class NotNullBox {
				[TypeClassInstance, ESExprTags(Constructors = ["box"])]
				public static IESExprCodec<NotNullBox<T>> Codec<T>(IESExprCodec<T> itemCodec) where T : notnull => throw new NotImplementedException();
			}
			""" + Record("public required NotNullBox<string?> Value { get; init; }")).SetName("NotNullCodecConstraintRejectsNullableArgument");
		yield return new TestCaseData("CS8785", "Could not resolve codec", """
			public sealed record Pair<A, B>(A First, B Second);
			public static class Pair {
				[TypeClassInstance, ESExprTags(Constructors = ["pair"])]
				public static IESExprCodec<Pair<T, T>> Codec<T>(IESExprCodec<T> itemCodec) => throw new NotImplementedException();
			}
			""" + Record("public required Pair<int, string> Value { get; init; }")).SetName("RepeatedTypeParameterRejectsDifferentArguments");
		yield return new TestCaseData("CS8785", "Could not resolve codec", """
			public struct ManagedValue {
				public string Value;
				[TypeClassInstance, ESExprTags(Scalar = [ESExprTag.ScalarType.Str])]
				public static IESExprCodec<ManagedValue> Codec => throw new NotImplementedException();
			}
			public sealed record ManagedBox<T>(T Value);
			public static class ManagedBox {
				[TypeClassInstance, ESExprTags(Constructors = ["box"])]
				public static IESExprCodec<ManagedBox<T>> Codec<T>(IESExprCodec<T> codec) where T : unmanaged => throw new NotImplementedException();
			}
			""" + Record("public required ManagedBox<ManagedValue> Value { get; init; }")).SetName("UnmanagedConstraintRejectsStructContainingReferences");
		yield return new TestCaseData("CS8785", "Could not resolve codec", """
			public sealed record UninferredBox<T>(T Value);
			public static class UninferredBox {
				[TypeClassInstance, ESExprTags(Constructors = ["box"])]
				public static IESExprCodec<UninferredBox<T>> Codec<T, U>(IESExprCodec<U> codec) => throw new NotImplementedException();
			}
			""" + Record("public required UninferredBox<int> Value { get; init; }")).SetName("UninferredFactoryDependencyIsRejected");
		yield return new TestCaseData("CS8785", "Could not resolve codec", """
			[ESExprCodec] public sealed record MissingLocal<T> { public required T Value { get; init; } }
			public static partial class MissingLocal {
				[TypeClassInstance] public static partial IESExprCodec<MissingLocal<T>> Codec<T>(IESExprCodec<string> wrongCodec);
			}
			""").SetName("MismatchedLocalCodecIsRejected");
		yield return new TestCaseData("CS8785", "Unable to substitute type parameters", Union("[InlineValue] public sealed record ValueCase<U> : InvalidUnion { public required U Value { get; init; } }")).SetName("UnresolvedInlineCaseTypeParameterIsRejected");
		yield return new TestCaseData("CS8785", "Unable to find TypeClassInstance", "[ESExprCodec] public sealed record NoCompanion<T> { public required T Value { get; init; } }").SetName("GenericRecordWithoutCodecCompanionIsRejected");
		yield return new TestCaseData("CS8785", "Unable to find TypeClassInstance", "[ESExprCodec] public sealed partial record UnmarkedAccessor { public static IESExprCodec<UnmarkedAccessor> Codec => throw new NotImplementedException(); }").SetName("AccessorWithoutInstanceAttributeIsRejected");
		// These failures arise while constructing the incremental model; Roslyn
		// reports them as CS8785 rather than the output-stage SG001 diagnostic.
		yield return new TestCaseData("CS8785", "Unable to find TypeClassInstance", "[ESExprCodec] public sealed partial record MissingAccessor { }").SetName("MissingCodecAccessorIsRejected");
		yield return new TestCaseData("CS8785", "Could not resolve codec", Record("public required DateTime Value { get; init; }")).SetName("UnresolvableCodecIsRejected");
		yield return new TestCaseData("CS8785", "Could not resolve optional value codec", Record("[Optional] public int Value { get; init; }")).SetName("UnresolvableOptionalCodecIsRejected");
		yield return new TestCaseData("CS8785", "Could not resolve dict codec", Record("[Dict] public int Value { get; init; }")).SetName("UnresolvableDictCodecIsRejected");
		yield return new TestCaseData("CS8785", "Could not resolve vararg codec", Record("[Vararg] public int Value { get; init; }")).SetName("UnresolvableVarargCodecIsRejected");
		yield return new TestCaseData("CS8785", "AbortGenerationException", Union("[InlineValue] public sealed record A : InvalidUnion;")).SetName("InlineCaseWithoutFieldIsRejected");
		yield return new TestCaseData("CS8785", "AbortGenerationException", Union("[InlineValue] public sealed record A : InvalidUnion { public required int AValue { get; init; } public required string BValue { get; init; } }")).SetName("InlineCaseWithTwoFieldsIsRejected");
		yield return new TestCaseData("CS8785", "Flag must specify FlagBitsAttribute", Flags("public required bool Value { get; init; }")).SetName("FlagRequiresMaskAttribute");
		yield return new TestCaseData("CS8785", "non-negative", Flags("[FlagBits(\"-1\")] public required bool Value { get; init; }")).SetName("NegativeFlagMaskIsRejected");
		yield return new TestCaseData("CS8785", "FormatException", Flags("[FlagBits(\"invalid\")] public required bool Value { get; init; }")).SetName("InvalidStringFlagMaskIsRejected");
		yield return new TestCaseData("CS8785", "Enum field must be marked", Flags("public required Choice Value { get; init; } public enum Choice { One }")).SetName("FlagEnumRequiresMaskAttributes");
		yield return new TestCaseData("CS8785", "non-negative", Flags("public required Choice Value { get; init; } public enum Choice { [FlagBits(\"-1\")] One }")).SetName("NegativeEnumMaskIsRejected");
		yield return new TestCaseData("CS8785", "bool or enum", Flags("public required int Value { get; init; }")).SetName("UnsupportedFlagTypeIsRejected");
		yield return new TestCaseData("CS8785", "KeywordAttribute must have a string literal", Record("private const string Name = \"key\"; [Keyword(Name)] public required int Value { get; init; }")).SetName("NonLiteralKeywordNameIsRejected");
		yield return new TestCaseData("CS8785", "ConstructorAttribute must have a string literal", Record("").Replace("[ESExprCodec]", "[ESExprCodec, Constructor(Name)]", StringComparison.Ordinal).Replace("[TypeClassInstance]", "private const string Name = \"value\"; [TypeClassInstance]", StringComparison.Ordinal)).SetName("NonLiteralConstructorNameIsRejected");
	}

	[TestCaseSource(nameof(InvalidInputs))]
	public void InvalidSourceReportsExpectedDiagnostic(string id, string message, string source) {
		var parseOptions = new CSharpParseOptions(LanguageVersion.CSharp13);
		var tree = CSharpSyntaxTree.ParseText(Imports + Environment.NewLine + source, parseOptions);
		var assemblyPaths = ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!).Split(Path.PathSeparator);
		var references = assemblyPaths.Select(path => MetadataReference.CreateFromFile(path))
			.Append(MetadataReference.CreateFromFile(typeof(IESExprCodec<>).Assembly.Location));
		var compilation = CSharpCompilation.Create("InvalidGeneratorInput", [tree], references,
			new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
		Assert.That(compilation.GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error && d.Id != "CS9248" && d.Id != "CS8795"), Is.Empty,
			"The input should be valid C# apart from partial members that require generated implementations.");
		GeneratorDriver driver = CSharpGeneratorDriver.Create([new ESExprCodecSourceGenerator().AsSourceGenerator()], parseOptions: parseOptions);
		driver = driver.RunGeneratorsAndUpdateCompilation(compilation, out _, out var diagnostics);
		Assert.That(diagnostics.Any(d => d.Id == id && d.GetMessage().Contains(message, StringComparison.Ordinal)), Is.True,
			string.Join(Environment.NewLine, diagnostics));
	}
}
