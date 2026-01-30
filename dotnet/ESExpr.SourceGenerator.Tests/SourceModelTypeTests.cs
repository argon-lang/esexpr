using System.Collections.Immutable;
using ESExpr.SourceGenerator;

namespace ESExpr.SourceGenerator.Tests;

public class SourceModelTypeTests {
	[Test]
	public void NamespaceSymbolParentEqualityTests() {
		var ns1 = new SourceModelType.NamespaceSymbolParent(ImmutableList.Create("A", "B"));
		var ns2 = new SourceModelType.NamespaceSymbolParent(ImmutableList.Create("A", "B"));
		var ns3 = new SourceModelType.NamespaceSymbolParent(ImmutableList.Create("A", "C"));
		var nsEmpty = new SourceModelType.NamespaceSymbolParent(ImmutableList<string>.Empty);
		var nsEmpty2 = new SourceModelType.NamespaceSymbolParent(ImmutableList<string>.Empty);

		Assert.That(ns1, Is.EqualTo(ns2));
		Assert.That(ns1.GetHashCode(), Is.EqualTo(ns2.GetHashCode()));

		Assert.That(ns1, Is.Not.EqualTo(ns3));

		Assert.That(nsEmpty, Is.EqualTo(nsEmpty2));
		Assert.That(nsEmpty.GetHashCode(), Is.EqualTo(nsEmpty2.GetHashCode()));
		
		Assert.That(ns1, Is.Not.EqualTo(nsEmpty));
		
		// Interface equality
		SourceModelType.INamedSymbolParent i1 = ns1;
		SourceModelType.INamedSymbolParent i2 = ns2;
		Assert.That(i1.Equals(i2), Is.True);
	}

	[Test]
	public void NamedSymbolEqualityTests() {
		var ns = new SourceModelType.NamespaceSymbolParent(ImmutableList.Create("MyNamespace"));
		var ns2 = new SourceModelType.NamespaceSymbolParent(ImmutableList.Create("MyNamespace"));
		
		var t1 = new SourceModelType.NamedSymbol(ns, "MyClass") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		var t2 = new SourceModelType.NamedSymbol(ns, "MyClass") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		var t2b = new SourceModelType.NamedSymbol(ns2, "MyClass") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		var t3 = new SourceModelType.NamedSymbol(ns, "OtherClass") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		
		Assert.That(t1, Is.EqualTo(t2));
		Assert.That(t1, Is.EqualTo(t2b));
		Assert.That<SourceModelType>(t1, Is.EqualTo<SourceModelType>(t2));
		Assert.That(t1.GetHashCode(), Is.EqualTo(t2.GetHashCode()));
		Assert.That(t1.GetHashCode(), Is.EqualTo(t2b.GetHashCode()));
		Assert.That(t1, Is.Not.EqualTo(t3));

		// Different Parent
		var nsOther = new SourceModelType.NamespaceSymbolParent(ImmutableList.Create("OtherNamespace"));
		var t4 = new SourceModelType.NamedSymbol(nsOther, "MyClass") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		Assert.That(t1, Is.Not.EqualTo(t4));

		// Different IsEnum
		var t5 = new SourceModelType.NamedSymbol(ns, "MyClass") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = true
		};
		Assert.That(t1, Is.Not.EqualTo(t5));

		// Different TypeArguments
		var t6 = new SourceModelType.NamedSymbol(ns, "MyClass") {
			TypeArguments = ImmutableList.Create<SourceModelType>(new SourceModelType.TypeParameter("T")),
			IsEnum = false
		};
		var t7 = new SourceModelType.NamedSymbol(ns, "MyClass") {
			TypeArguments = ImmutableList.Create<SourceModelType>(new SourceModelType.TypeParameter("T")),
			IsEnum = false
		};
		var t8 = new SourceModelType.NamedSymbol(ns, "MyClass") {
			TypeArguments = ImmutableList.Create<SourceModelType>(new SourceModelType.TypeParameter("U")),
			IsEnum = false
		};

		Assert.That(t6, Is.EqualTo(t7));
		Assert.That(t6.GetHashCode(), Is.EqualTo(t7.GetHashCode()));
		Assert.That(t6, Is.Not.EqualTo(t1));
		Assert.That(t6, Is.Not.EqualTo(t8));
	}

	[Test]
	public void TypeParameterEqualityTests() {
		var tp1 = new SourceModelType.TypeParameter("T");
		var tp2 = new SourceModelType.TypeParameter("T");
		var tp3 = new SourceModelType.TypeParameter("U");

		Assert.That(tp1, Is.EqualTo(tp2));
		Assert.That(tp1.GetHashCode(), Is.EqualTo(tp2.GetHashCode()));
		Assert.That(tp1, Is.Not.EqualTo(tp3));
	}

	[Test]
	public void WildcardEqualityTests() {
		var w1 = new SourceModelType.Wildcard("T");
		var w2 = new SourceModelType.Wildcard("T");
		var w3 = new SourceModelType.Wildcard("U");

		Assert.That(w1, Is.EqualTo(w2));
		Assert.That(w1.GetHashCode(), Is.EqualTo(w2.GetHashCode()));
		Assert.That(w1, Is.Not.EqualTo(w3));
	}

	[Test]
	public void NullableEqualityTests() {
		var inner = new SourceModelType.TypeParameter("T");
		var n1 = new SourceModelType.Nullable(inner);
		var n2 = new SourceModelType.Nullable(new SourceModelType.TypeParameter("T"));
		var n3 = new SourceModelType.Nullable(new SourceModelType.TypeParameter("U"));

		Assert.That(n1, Is.EqualTo(n2));
		Assert.That(n1.GetHashCode(), Is.EqualTo(n2.GetHashCode()));
		Assert.That(n1, Is.Not.EqualTo(n3));
		Assert.That(n1.Equals(inner), Is.False);
	}

	[Test]
	public void ArrayEqualityTests() {
		var element = new SourceModelType.TypeParameter("T");
		var a1 = new SourceModelType.Array(element);
		var a2 = new SourceModelType.Array(new SourceModelType.TypeParameter("T"));
		var a3 = new SourceModelType.Array(new SourceModelType.TypeParameter("U"));

		Assert.That(a1, Is.EqualTo(a2));
		Assert.That(a1.GetHashCode(), Is.EqualTo(a2.GetHashCode()));
		Assert.That(a1, Is.Not.EqualTo(a3));
		Assert.That(a1.Equals(element), Is.False);
	}

	[Test]
	public void PointerEqualityTests() {
		var pointedAt = new SourceModelType.TypeParameter("T");
		var p1 = new SourceModelType.Pointer(pointedAt);
		var p2 = new SourceModelType.Pointer(new SourceModelType.TypeParameter("T"));
		var p3 = new SourceModelType.Pointer(new SourceModelType.TypeParameter("U"));

		Assert.That(p1, Is.EqualTo(p2));
		Assert.That(p1.GetHashCode(), Is.EqualTo(p2.GetHashCode()));
		Assert.That(p1, Is.Not.EqualTo(p3));
		Assert.That(p1.Equals(pointedAt), Is.False);
	}
	
	[Test]
	public void NestedNamedSymbolEqualityTests() {
		var ns = new SourceModelType.NamespaceSymbolParent(ImmutableList.Create("N"));
		var outer = new SourceModelType.NamedSymbol(ns, "Outer") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		
		var inner1 = new SourceModelType.NamedSymbol(outer, "Inner") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		var inner2 = new SourceModelType.NamedSymbol(outer, "Inner") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		
		Assert.That(inner1, Is.EqualTo(inner2));
		Assert.That(inner1.GetHashCode(), Is.EqualTo(inner2.GetHashCode()));

		var otherOuter = new SourceModelType.NamedSymbol(ns, "OtherOuter") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		var inner3 = new SourceModelType.NamedSymbol(otherOuter, "Inner") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		Assert.That(inner1, Is.Not.EqualTo(inner3));
	}

	[Test]
	public void ImmutableDictionaryKeyTest() {
		var ns1 = new SourceModelType.NamespaceSymbolParent(ImmutableList.Create("N"));
		var ns2 = new SourceModelType.NamespaceSymbolParent(ImmutableList.Create("N"));

		var type1 = new SourceModelType.NamedSymbol(ns1, "MyClass") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};
		var type2 = new SourceModelType.NamedSymbol(ns2, "MyClass") {
			TypeArguments = ImmutableList<SourceModelType>.Empty,
			IsEnum = false
		};

		Assert.That(type1, Is.EqualTo(type2));
		Assert.That(type1, Is.Not.SameAs(type2));

		var dict = ImmutableDictionary<SourceModelType, string>.Empty.Add(type1, "Value");

		Assert.That(dict.ContainsKey(type2), Is.True);
		Assert.That(dict[type2], Is.EqualTo("Value"));
	}
}
