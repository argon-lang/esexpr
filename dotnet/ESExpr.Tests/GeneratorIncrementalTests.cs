using ESExpr.Runtime;
using ESExpr.SourceGenerator;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;

namespace ESExpr.Tests;

public class GeneratorIncrementalTests {
	private const string RecordSource = """
		using ESExpr.Runtime;
		[ESExprCodec]
		public sealed partial record IncrementalFixture {
			public required int Value { get; init; }
			[TypeClassInstance]
			public static partial IESExprCodec<IncrementalFixture> Codec { get; }
		}
		""";

	private static readonly CSharpParseOptions ParseOptions = new(LanguageVersion.Preview);

	private static CSharpCompilation Compile(params SyntaxTree[] trees) {
		var paths = ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!).Split(Path.PathSeparator);
		var references = paths.Select(path => MetadataReference.CreateFromFile(path))
			.Append(MetadataReference.CreateFromFile(typeof(IESExprCodec<>).Assembly.Location));
		return CSharpCompilation.Create("IncrementalInput", trees, references, new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
	}

	private static GeneratorDriver Driver() => CSharpGeneratorDriver.Create(
		[new ESExprCodecSourceGenerator().AsSourceGenerator()], parseOptions: ParseOptions,
		driverOptions: new GeneratorDriverOptions(default, trackIncrementalGeneratorSteps: true));

	private static GeneratorDriver Run(GeneratorDriver driver, Compilation input) {
		driver = driver.RunGeneratorsAndUpdateCompilation(input, out var output, out var diagnostics);
		Assert.That(diagnostics, Is.Empty, string.Join(Environment.NewLine, diagnostics));
		var errors = output.GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).ToArray();
		Assert.That(errors, Is.Empty, string.Join(Environment.NewLine, errors.Select(d => d.ToString())));
		return driver;
	}

	private static string GeneratedText(GeneratorDriver driver) => driver.GetRunResult().Results.Single().GeneratedSources.Single().SourceText.ToString();

	[Test]
	public void UnchangedCompilationReusesCachedGeneratorOutput() {
		var input = Compile(CSharpSyntaxTree.ParseText(RecordSource, ParseOptions));
		var driver = Run(Driver(), input);
		var original = GeneratedText(driver);
		driver = Run(driver, input);
		Assert.That(GeneratedText(driver), Is.EqualTo(original));
		var outputs = driver.GetRunResult().Results.Single().TrackedOutputSteps.Values
			.SelectMany(steps => steps).SelectMany(step => step.Outputs).ToArray();
		Assert.That(outputs, Is.Not.Empty);
		Assert.That(outputs.All(output => output.Reason == IncrementalStepRunReason.Cached), Is.True);
	}

	[Test]
	public void EditingAFieldRegeneratesItsCodecAndRemovingTheTypeRemovesOutput() {
		var tree = CSharpSyntaxTree.ParseText(RecordSource, ParseOptions);
		var input = Compile(tree);
		var driver = Run(Driver(), input);
		Assert.That(GeneratedText(driver), Does.Contain("IntCodec"));
		var replacement = CSharpSyntaxTree.ParseText(RecordSource.Replace("int Value", "string Value", StringComparison.Ordinal), ParseOptions);
		input = input.ReplaceSyntaxTree(tree, replacement);
		driver = Run(driver, input);
		Assert.That(GeneratedText(driver), Does.Contain("StrCodec").And.Not.Contain("IntCodec"));
		driver = Run(driver, input.RemoveSyntaxTrees(replacement));
		Assert.That(driver.GetRunResult().GeneratedTrees, Is.Empty);
	}

	[Test]
	public void EditingUnrelatedSourcePreservesTheGeneratedCodec() {
		var unrelated = CSharpSyntaxTree.ParseText("public class Unrelated { public int Value => 1; }", ParseOptions);
		var input = Compile(CSharpSyntaxTree.ParseText(RecordSource, ParseOptions), unrelated);
		var driver = Run(Driver(), input);
		var original = GeneratedText(driver);
		var edited = CSharpSyntaxTree.ParseText("public class Unrelated { public int Value => 2; }", ParseOptions);
		driver = Run(driver, input.ReplaceSyntaxTree(unrelated, edited));
		Assert.That(GeneratedText(driver), Is.EqualTo(original));
	}

	[Test]
	public void RecursiveInlineTagsFallBackToAllWithoutRecursingForever() {
		const string source = """
			using ESExpr.Runtime;
			[ESExprCodec]
			public closed partial record RecursiveTags {
				private RecursiveTags() {}
				[InlineValue] public sealed record Loop : RecursiveTags { public required RecursiveTags Value { get; init; } }
				[TypeClassInstance] public static partial IESExprCodec<RecursiveTags> Codec { get; }
			}
			""";
		var driver = Run(Driver(), Compile(CSharpSyntaxTree.ParseText(source, ParseOptions)));
		Assert.That(GeneratedText(driver), Does.Contain("All = true"));
	}
}
