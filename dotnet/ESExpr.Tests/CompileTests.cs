using ESExpr.Runtime;
using ESExpr.SourceGenerator;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;

namespace ESExpr.Tests;

public class CompileTests : TestBase {

	private void AssertFails(string errorCode, string sourceCode) {
		var syntaxTree = CSharpSyntaxTree.ParseText(sourceCode);

		var trustedAssemblies = ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!).Split(Path.PathSeparator);
		
		var neededAssemblies = new[]
		{
			"System.Private.CoreLib",
			"System.Runtime",
		};
		
		
		Compilation compilation = CSharpCompilation.Create(
			"ExampleAssembly",
			syntaxTrees: new[] { syntaxTree },
			references: [
				..trustedAssemblies
					.Where(path => neededAssemblies.Contains(Path.GetFileNameWithoutExtension(path)))
					.Select(path => MetadataReference.CreateFromFile(path)),
				MetadataReference.CreateFromFile(typeof(IESExprCodec<>).Assembly.Location),
			],
			options: new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary)
		);

		GeneratorDriver driver = CSharpGeneratorDriver.Create(new ESExprCodecSourceGenerator());
		driver.RunGeneratorsAndUpdateCompilation(compilation, out _, out var diagnostics);
		
		Assert.That(
			diagnostics.Any(diagnostic => diagnostic.Severity == DiagnosticSeverity.Error && diagnostic.Id == errorCode),
			diagnostics.Length == 0
				? "Generation succeeded, but was expected to fail"
				: $"Generation failed with wrong errors: {string.Join("\n", diagnostics)}"
		);
	}

	private const string Imports = @"
		using System;
		using ESExpr.Runtime;
	";
	
	[Test]
	public void GenerateForClass() {
		AssertFails(
		"ESX0003",
		Imports + "[ESExprCodec] public class HelloWorld {}"
		);
	}
	
	[Test]
	public void GenerateForInterface() {
		AssertFails(
			"ESX0003",
			Imports + "[ESExprCodec] public interface HelloWorld {}"
		);
	}
	
	[Test]
	public void KeywordAfterDict() {
		AssertFails(
			"ESX0011",
			Imports +
			@"[ESExprCodec]
			public sealed partial record HelloWorld {
				[Dict]
				public required VDict<string> A { get; init; }
				[Keyword]
				public required string B { get; init; }
			}
			"
		);
	}
	
	[Test]
	public void MultipleDict() {
		AssertFails(
			"ESX0010",
			Imports +
			@"[ESExprCodec]
			public sealed partial record HelloWorld {
				[Dict]
				public required VDict<string> A { get; init; }
				[Dict]
				public required VDict<string> B { get; init; }
			}
			"
		);
	}
	
	[Test]
	public void PositionalAfterVararg() {
		AssertFails(
			"ESX0009",
			Imports +
			@"[ESExprCodec]
			public sealed partial record HelloWorld {
				[Vararg]
				public required VList<string> A { get; init; }
				public required string B { get; init; }
			}
			"
		);
	}
	
	[Test]
	public void MultipleVarargs() {
		AssertFails(
			"ESX0008",
			Imports +
			@"[ESExprCodec]
			public sealed partial record HelloWorld {
				[Vararg]
				public required VList<string> A { get; init; }
				[Vararg]
				public required VList<string> B { get; init; }
			}
			"
		);
	}
	
}
