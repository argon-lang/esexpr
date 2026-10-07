using System.Collections.Immutable;
using ESExpr.SourceGenerator.TypeClass;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator.Tests;

public class GeneratorModelContractTests {
	[Test]
	public void SyntaxModelsCompareContentAcrossSeparatelyParsedTrees() {
		var first = new SourceModelSyntax<TypeSyntax>(SyntaxFactory.ParseTypeName("System.Int32"));
		var same = new SourceModelSyntax<TypeSyntax>(SyntaxFactory.ParseTypeName("/*different leading trivia*/ System.Int32"));
		var different = new SourceModelSyntax<TypeSyntax>(SyntaxFactory.ParseTypeName("System.String"));
		Assert.That(first.Equals(same), Is.True);
		Assert.That(first.GetHashCode(), Is.EqualTo(same.GetHashCode()));
		Assert.That(first.Equals(different), Is.False);
		Assert.That(first.Equals("System.Int32"), Is.False);
		Assert.That(first.ToString(), Is.EqualTo("System.Int32"));
		Assert.That(new HashSet<SourceModelSyntax<TypeSyntax>> { first, same, different }.Count, Is.EqualTo(2));
		Assert.That(first.Equals(null), Is.False);
	}

	private static SourceModelTypeClassInstanceAccessor Accessor(bool isProperty, params string[] parameterNames) => new() {
		Name = "Codec",
		IsProperty = isProperty,
		Parameters = parameterNames.Select(name => new SourceModelTypeClassInstanceAccessorParameter {
			Name = name, Type = new SourceModelType.TypeParameter("T"),
		}).ToImmutableList(),
	};

	[Test]
	public void CodecAccessorEqualityIncludesKindNameAndOrderedParameters() {
		var first = Accessor(false, "first", "second");
		var same = Accessor(false, "first", "second");
		Assert.That(first.Equals(same), Is.True);
		Assert.That(first.GetHashCode(), Is.EqualTo(same.GetHashCode()));
		Assert.That(first.Equals(first with { Name = "Other" }), Is.False);
		Assert.That(first.Equals(Accessor(false, "second", "first")), Is.False);
		Assert.That(first.Equals(Accessor(false, "first")), Is.False);
		Assert.That(first.Equals(first with {
			Parameters = [first.Parameters[0] with { Type = new SourceModelType.TypeParameter("U") }, first.Parameters[1]],
		}), Is.False);
		Assert.That(Accessor(true).Equals(Accessor(false)), Is.False);
		Assert.That(new HashSet<SourceModelTypeClassInstanceAccessor> { first, same, first with { Name = "Other" }, Accessor(true), Accessor(false) }.Count, Is.EqualTo(4));
		Assert.That(first.Equals(null), Is.False);
	}
}
