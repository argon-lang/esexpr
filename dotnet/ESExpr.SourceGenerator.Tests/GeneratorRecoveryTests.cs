using ESExpr.Runtime;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;

namespace ESExpr.SourceGenerator.Tests;

public class GeneratorRecoveryTests {
	private static readonly CSharpParseOptions ParseOptions = new(LanguageVersion.Preview);
	private static readonly MetadataReference[] References = ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!)
		.Split(Path.PathSeparator).Select(p => MetadataReference.CreateFromFile(p))
		.Append(MetadataReference.CreateFromFile(typeof(IESExprCodec<>).Assembly.Location)).ToArray();
	private static CSharpCompilation Compile(string source) => CSharpCompilation.Create("Recovery",
		[CSharpSyntaxTree.ParseText("using System; using ESExpr.Runtime;\n" + source, ParseOptions)], References,
		new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
	private static GeneratorDriver Driver() => CSharpGeneratorDriver.Create([new ESExprCodecSourceGenerator().AsSourceGenerator()], parseOptions: ParseOptions);
	private const string Valid = """
		[ESExprCodec] public sealed partial record Valid {
			public required int Value { get; init; }
			[TypeClassInstance] public static partial IESExprCodec<Valid> Codec { get; }
		}
		""";

	[TestCase("""
		[ESExprCodec] public sealed partial record Invalid {
			public required DateTime A { get; init; }
			public required DateTime B { get; init; }
		}
		""", new[] { "ESX0015", "ESX0006", "ESX0006" })]
	[TestCase("""
		[ESExprCodec] public sealed partial record Invalid {
			[Keyword("same")] public required DateTime A { get; init; }
			[Keyword("same")] public required DateTime B { get; init; }
			[TypeClassInstance] public static partial IESExprCodec<Invalid> Codec { get; }
		}
		""", new[] { "ESX0006", "ESX0006", "ESX0013" })]
	[TestCase("""
		[ESExprCodec] public closed partial record Invalid {
			[InlineValue] public sealed record A : Invalid;
			[InlineValue] public sealed record B : Invalid { [Keyword] public required DateTime Value { get; init; } }
			[Constructor("same")] public sealed record C : Invalid;
			[Constructor("same")] public sealed record D : Invalid;
			[TypeClassInstance] public static partial IESExprCodec<Invalid> Codec { get; }
		}
		""", new[] { "ESX0007", "ESX0020", "ESX0006", "ESX0009" })]
	[TestCase("""
		[ESExprCodec(Flags = true)] public sealed partial record Invalid {
			[FlagBits("bad")] public required bool A { get; init; }
			[FlagBits(1)] public required bool B { get; init; }
			[FlagBits(1)] public required bool C { get; init; }
			public required Choice D { get; init; }
			public enum Choice { A, B }
			[TypeClassInstance] public static partial IESExprCodec<Invalid> Codec { get; }
		}
		""", new[] { "ESX0018", "ESX0018", "ESX0018", "ESX0019" })]
	public void IndependentErrorsDoNotPreventValidGeneration(string source, string[] expected) {
		var driver = Driver().RunGeneratorsAndUpdateCompilation(Compile(source + Valid), out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EquivalentTo(expected), string.Join(Environment.NewLine, diagnostics));
		Assert.That(diagnostics.All(d => d.Location.IsInSource && d.Severity == DiagnosticSeverity.Error), Is.True);
		Assert.That(driver.GetRunResult().Results[0].GeneratedSources.Select(s => s.HintName), Has.Exactly(1).Contains("Valid.ESExprCodec.g.cs"));
	}

	[Test]
	public void FixingInputRemovesDiagnosticsAndRestoresSource() {
		var bad = Compile(Valid.Replace("required int", "required DateTime", StringComparison.Ordinal));
		var driver = Driver().RunGeneratorsAndUpdateCompilation(bad, out _, out var diagnostics);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0006" }));
		Assert.That(driver.GetRunResult().GeneratedTrees, Is.Empty);
		var fixedInput = bad.ReplaceSyntaxTree(bad.SyntaxTrees.Single(), Compile(Valid).SyntaxTrees.Single());
		driver = driver.RunGeneratorsAndUpdateCompilation(fixedInput, out var output, out diagnostics);
		Assert.That(diagnostics, Is.Empty);
		Assert.That(driver.GetRunResult().GeneratedTrees, Has.Length.EqualTo(1));
		Assert.That(output.GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error), Is.Empty);
	}

	[Test]
	public void CancellationAndFatalExceptionsAreNotRecovered() {
		var diagnostics = new GenerationDiagnostics();
		foreach(var exception in new Exception[] { new OperationCanceledException(), new OutOfMemoryException(), new StackOverflowException(), new AccessViolationException() }) {
			Assert.Throws(exception.GetType(), () => diagnostics.Recover<bool>(Location.None, () => throw exception, false));
		}
		using var cancellation = new CancellationTokenSource();
		cancellation.Cancel();
		Assert.Throws<OperationCanceledException>(() => Driver().RunGenerators(Compile(Valid), cancellation.Token));
		Assert.That(diagnostics.Diagnostics, Is.Empty);
	}

	[TestCase("Scalar = null!")]
	[TestCase("Constructors = null!")]
	[TestCase("UnionWithTypeParameters = null!")]
	public void NullTagArraysProduceLocatedDiagnostics(string metadata) {
		var provider = $$"""
			public sealed record Item {
				[TypeClassInstance, ESExprTags({{metadata}})]
				public static IESExprCodec<Item> Codec => throw new NotImplementedException();
			}
			""";
		var driver = Driver().RunGeneratorsAndUpdateCompilation(Compile(provider + Valid.Replace("required int", "required Item", StringComparison.Ordinal)), out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0016" }));
		Assert.That(diagnostics[0].Location.IsInSource, Is.True);
		Assert.That(diagnostics[0].GetMessage(), Does.Contain("Item.Codec"));
	}

	[Test]
	public void UnsupportedSourceTypeIsReported() {
		var source = Valid.Replace("required int", "required dynamic", StringComparison.Ordinal);
		var driver = Driver().RunGeneratorsAndUpdateCompilation(Compile(source), out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0017" }));
		Assert.That(diagnostics[0].Location.IsInSource, Is.True);
	}

	[Test]
	public void MetadataErrorsFromReferencedAssemblyUseConsumerLocation() {
		var provider = Compile("""
			public sealed record ForeignItem {
				[TypeClassInstance, ESExprTags(Constructors = null!)]
				public static IESExprCodec<ForeignItem> Codec => throw new NotImplementedException();
			}
			""").WithAssemblyName("Provider");
		using var stream = new MemoryStream();
		var emitted = provider.Emit(stream);
		Assert.That(emitted.Success, Is.True, string.Join(Environment.NewLine, emitted.Diagnostics));
		var consumer = Compile(Valid.Replace("required int", "required ForeignItem", StringComparison.Ordinal))
			.AddReferences(MetadataReference.CreateFromImage(stream.ToArray()));
		var driver = Driver().RunGeneratorsAndUpdateCompilation(consumer, out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0016" }));
		Assert.That(diagnostics[0].Location.SourceTree, Is.SameAs(consumer.SyntaxTrees.Single()));
		Assert.That(diagnostics[0].Location.SourceTree!.GetText().ToString(diagnostics[0].Location.SourceSpan), Is.EqualTo("Value"));
		Assert.That(diagnostics[0].GetMessage(), Does.Contain("ForeignItem.Codec"));
	}

	[Test]
	public void InvalidElementCodecIsReported() {
		var source = """
			public sealed record Item {
				[TypeClassInstance] public static IOptionalValueCodec<Item, dynamic> Codec => throw new NotImplementedException();
			}
			""" + Valid.Replace("public required int", "[Optional] public required Item", StringComparison.Ordinal);
		var driver = Driver().RunGeneratorsAndUpdateCompilation(Compile(source), out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0014" }));
		Assert.That(diagnostics[0].Location.IsInSource, Is.True);
	}

	[Test]
	public void UnexpectedFailureRetainsEarlierDiagnosticsAndUsesCurrentLocation() {
		var location = Compile(Valid).SyntaxTrees.Single().GetRoot().GetLocation();
		var diagnostics = new GenerationDiagnostics();
		diagnostics.Report(Diagnostic.Create(Errors.InvalidFlag, location, "invalid mask"));
		diagnostics.Report(Diagnostic.Create(Errors.InvalidFlag, location, "invalid mask"));
		diagnostics.Recover<bool>(location, () => throw new InvalidOperationException("test invariant"), false);
		Assert.That(diagnostics.Diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0018", "SG001" }));
		Assert.That(diagnostics.Diagnostics[1].Location, Is.EqualTo(location));
		Assert.That(diagnostics.Diagnostics[1].GetMessage(), Does.Contain("test invariant"));
	}

	[Test]
	public void MissingRuntimeInterfaceIsReportedAtProperty() {
		var stubs = """
			namespace ESExpr.Runtime {
				public sealed class ESExprCodecAttribute : Attribute { }
				public sealed class TypeClassInstanceAttribute : Attribute { }
				public sealed class OptionalAttribute : Attribute { }
				public interface IESExprCodec<T> { }
			}
			""";
		var compilation = Compile(stubs + Valid.Replace("public required int", "[Optional] public required int", StringComparison.Ordinal))
			.RemoveReferences(References.Where(r => Path.GetFileName(r.Display) == "ESExpr.Runtime.dll"));
		var driver = Driver().RunGeneratorsAndUpdateCompilation(compilation, out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0015" }));
		Assert.That(diagnostics[0].Location.SourceTree!.GetText().ToString(diagnostics[0].Location.SourceSpan), Is.EqualTo("Value"));
	}

	[Test]
	public void MalformedEnumNamesAreCollectedWithoutDependentOverlapErrors() {
		var source = """
			[ESExprCodec] public enum Invalid {
				[Constructor(Name)] A,
				[Constructor(Name)] B,
			}
			""".Replace("Name", "nameof(Invalid)", StringComparison.Ordinal);
		var driver = Driver().RunGeneratorsAndUpdateCompilation(Compile(source), out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0016", "ESX0016" }));
		Assert.That(diagnostics.All(d => d.Location.IsInSource), Is.True);
	}

	[Test]
	public void MissingMetadataForDistinctReferencedEnumMembersIsNotDeduplicated() {
		var provider = Compile("public enum ForeignChoice { A, B }").WithAssemblyName("Provider");
		using var stream = new MemoryStream();
		Assert.That(provider.Emit(stream).Success, Is.True);
		var consumer = Compile(Valid.Replace("[ESExprCodec]", "[ESExprCodec(Flags = true)]", StringComparison.Ordinal)
			.Replace("required int", "required ForeignChoice", StringComparison.Ordinal))
			.AddReferences(MetadataReference.CreateFromImage(stream.ToArray()));
		var driver = Driver().RunGeneratorsAndUpdateCompilation(consumer, out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0018", "ESX0018" }));
		Assert.That(diagnostics[0].GetMessage(), Does.Contain("ForeignChoice.A"));
		Assert.That(diagnostics[1].GetMessage(), Does.Contain("ForeignChoice.B"));
		Assert.That(diagnostics.All(d => d.Location.SourceTree == consumer.SyntaxTrees.Single()), Is.True);
	}

	[Test]
	public void FailedFieldDoesNotCauseDependentPositionalTagErrors() {
		var source = """
			[ESExprCodec] public sealed partial record Invalid {
				[Optional] public Option<int> A { get; init; }
				public required DateTime B { get; init; }
				public required int C { get; init; }
				[TypeClassInstance] public static partial IESExprCodec<Invalid> Codec { get; }
			}
			""";
		var driver = Driver().RunGeneratorsAndUpdateCompilation(Compile(source), out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0006" }));
	}

	[Test]
	public void InvalidDeclarationDoesNotRequireAccessorInitialization() {
		var driver = Driver().RunGeneratorsAndUpdateCompilation(Compile("[ESExprCodec] public struct Invalid { }"), out _, out var diagnostics);
		Assert.That(driver.GetRunResult().Results[0].Exception, Is.Null);
		Assert.That(diagnostics.Select(d => d.Id), Is.EqualTo(new[] { "ESX0003" }));
	}
}
