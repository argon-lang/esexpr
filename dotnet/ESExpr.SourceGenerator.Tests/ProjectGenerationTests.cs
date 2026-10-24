using System.Runtime.CompilerServices;
using ESExpr.SourceGenerator;
using Microsoft.Build.Locator;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.MSBuild;

namespace ESExpr.SourceGenerator.Tests;

public class ProjectGenerationTests {
	[Test]
	public async Task GenerateCodecsForRuntimeTestProject() {
		// Register before the separate method loads any MSBuild APIs.
		if(!MSBuildLocator.IsRegistered) {
			MSBuildLocator.RegisterDefaults();
		}

		await GenerateProject();
	}

	[MethodImpl(MethodImplOptions.NoInlining)]
	private static async Task GenerateProject() {
		var projectPath = FindRuntimeTestProject();
		using var workspace = MSBuildWorkspace.Create();
		workspace.LoadMetadataForReferencedProjects = true;
		var project = await workspace.OpenProjectAsync(projectPath);
		Assert.That(workspace.Diagnostics.Where(d => d.Kind == WorkspaceDiagnosticKind.Failure), Is.Empty,
			string.Join(Environment.NewLine, workspace.Diagnostics));

		// Remove analyzers before requesting a compilation so workspace-generated
		// codecs cannot become inputs to the explicit generator run below.
		// Roslyn 5.9 exposes closed hierarchies through its preview language mode.
		project = project.WithAnalyzerReferences([])
			.WithParseOptions(((CSharpParseOptions)project.ParseOptions!).WithLanguageVersion(LanguageVersion.Preview));
		var compilation = await project.GetCompilationAsync();
		Assert.That(compilation, Is.Not.Null);
		var parseOptions = (CSharpParseOptions)project.ParseOptions!;
		GeneratorDriver driver = CSharpGeneratorDriver.Create(
			[new ESExprCodecSourceGenerator().AsSourceGenerator()], parseOptions: parseOptions);
		driver = driver.RunGeneratorsAndUpdateCompilation(compilation!, out var outputCompilation, out var diagnostics);
		var result = driver.GetRunResult();

		Assert.That(result.Results, Has.Length.EqualTo(1));
		Assert.That(result.Results[0].Exception, Is.Null);
		Assert.That(diagnostics, Is.Empty, string.Join(Environment.NewLine, diagnostics));
		Assert.That(result.GeneratedTrees, Is.Not.Empty);
		// Representative fixtures cover records, unions, flags and generic records.
		foreach(var fixture in new[] { "PrimitiveFields", "MyEnum", "TwoFlags", "MyGenericRecord" }) {
			Assert.That(result.Results[0].GeneratedSources.Any(source => source.HintName.Contains(fixture, StringComparison.Ordinal)),
				Is.True, $"No codec generated for {fixture}");
		}
		var outputDiagnostics = outputCompilation.GetDiagnostics();
		Assert.That(outputDiagnostics.Where(d => d.Id == "CS8669"), Is.Empty, "Generated nullable annotations must have an explicit nullable context.");
		var errors = outputDiagnostics.Where(d => d.Severity == DiagnosticSeverity.Error).ToArray();
		Assert.That(errors, Is.Empty, string.Join(Environment.NewLine, errors.Select(d => d.ToString())));
	}

	private static string FindRuntimeTestProject() {
		for(var directory = new DirectoryInfo(AppContext.BaseDirectory); directory is not null; directory = directory.Parent) {
			var path = Path.Combine(directory.FullName, "ESExpr.Tests", "ESExpr.Tests.csproj");
			if(File.Exists(path)) return path;
		}

		throw new FileNotFoundException("Could not locate ESExpr.Tests.csproj above the test output directory.");
	}
}
