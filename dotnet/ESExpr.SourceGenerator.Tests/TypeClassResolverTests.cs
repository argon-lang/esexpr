using ESExpr.Runtime;
using ESExpr.SourceGenerator.TypeClass;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;

namespace ESExpr.SourceGenerator.Tests;

public class TypeClassResolverTests {
	private static CSharpCompilation Compile(string source) {
		var assemblyPaths = ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!).Split(Path.PathSeparator);
		var references = assemblyPaths.Select(path => MetadataReference.CreateFromFile(path))
			.Append(MetadataReference.CreateFromFile(typeof(IESExprCodec<>).Assembly.Location));
		var compilation = CSharpCompilation.Create("ResolverTest", [CSharpSyntaxTree.ParseText(source)], references,
			new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary, allowUnsafe: true));
		var errors = compilation.GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).ToArray();
		Assert.That(errors, Is.Empty, string.Join(Environment.NewLine, errors.Select(d => d.ToString())));
		return compilation;
	}

	[Test]
	public void SubstitutionPreservesArrayRankAndPointerShape() {
		var compilation = Compile("public unsafe class Shapes<T> where T : unmanaged { public T[,] Array; public T* Pointer; public T Value; }");
		var shapes = compilation.GetTypeByMetadataName("Shapes`1")!;
		var resolver = new TypeClassResolver(compilation, []);
		var integer = compilation.GetSpecialType(SpecialType.System_Int32);
		var mapping = new Dictionary<ITypeParameterSymbol, ITypeSymbol>(SymbolEqualityComparer.Default) { [shapes.TypeParameters[0]] = integer };
		var array = ((IFieldSymbol)shapes.GetMembers("Array").Single()).Type;
		var pointer = ((IFieldSymbol)shapes.GetMembers("Pointer").Single()).Type;
		var substitutedArray = (IArrayTypeSymbol)resolver.SubstituteTypes(array, mapping)!;
		Assert.That(substitutedArray.Rank, Is.EqualTo(2));
		Assert.That(SymbolEqualityComparer.Default.Equals(substitutedArray.ElementType, integer), Is.True);
		var substitutedPointer = (IPointerTypeSymbol)resolver.SubstituteTypes(pointer, mapping)!;
		Assert.That(SymbolEqualityComparer.Default.Equals(substitutedPointer.PointedAtType, integer), Is.True);
		Assert.That(resolver.SubstituteTypes(array, new(SymbolEqualityComparer.Default)), Is.Null);
		Assert.That(resolver.SubstituteTypes(pointer, new(SymbolEqualityComparer.Default)), Is.Null);
		Assert.That(SourceModelType.FromSymbol(array), Is.EqualTo(new SourceModelType.Array(new SourceModelType.TypeParameter("T"))));
		Assert.That(SourceModelType.FromSymbol(pointer), Is.EqualTo(new SourceModelType.Pointer(new SourceModelType.TypeParameter("T"))));
	}

	[Test]
	public void ArrayFactoryUnifiesRepeatedElementTypesAndResolvesItsDependency() {
		var compilation = Compile("""
			using ESExpr.Runtime;
			public sealed record ArrayPair<A, B>(A First, B Second);
			public static class ArrayPair {
				[TypeClassInstance]
				public static IESExprCodec<ArrayPair<T[], T[]>> Codec<T>(IESExprCodec<T> elementCodec) => throw new System.NotImplementedException();
			}
			""");
		var resolver = new TypeClassResolver(compilation, []);
		var integerArray = compilation.CreateArrayTypeSymbol(compilation.GetSpecialType(SpecialType.System_Int32));
		var textArray = compilation.CreateArrayTypeSymbol(compilation.GetSpecialType(SpecialType.System_String));
		var pair = compilation.GetTypeByMetadataName("ArrayPair`2")!;
		var codec = compilation.GetTypeByMetadataName("ESExpr.Runtime.IESExprCodec`1")!;
		var result = resolver.Resolve(codec.Construct(pair.Construct(integerArray, integerArray)));
		Assert.That(result, Is.TypeOf<TypeClassResult.Method>());
		var method = (TypeClassResult.Method)result!;
		Assert.That(method.TypeArguments, Has.Count.EqualTo(1));
		Assert.That(SymbolEqualityComparer.Default.Equals(method.TypeArguments[0], compilation.GetSpecialType(SpecialType.System_Int32)), Is.True);
		Assert.That(method.Arguments, Has.Count.EqualTo(1));
		Assert.That(SymbolEqualityComparer.Default.Equals(resolver.GetTypeClassType(method), codec.Construct(pair.Construct(integerArray, integerArray))), Is.True);
		Assert.That(resolver.Resolve(codec.Construct(pair.Construct(integerArray, textArray))), Is.Null);
	}

	[Test]
	public void LocalResolutionRequiresAnExactCodecType() {
		var compilation = Compile("using ESExpr.Runtime; public class Locals<T> { public static void Method(IESExprCodec<T> good, IESExprCodec<string> wrong) {} }");
		var method = (IMethodSymbol)compilation.GetTypeByMetadataName("Locals`1")!.GetMembers("Method").Single();
		var good = new LocalInfo("good", method.Parameters[0].Type);
		var wrong = new LocalInfo("wrong", method.Parameters[1].Type);
		var target = (INamedTypeSymbol)good.Type;
		Assert.That(new TypeClassResolver(compilation, []).Resolve(target), Is.Null);
		Assert.That(new TypeClassResolver(compilation, [wrong]).Resolve(target), Is.Null);
		var resolver = new TypeClassResolver(compilation, [wrong, good]);
		var result = resolver.Resolve(target);
		Assert.That(result, Is.EqualTo(new TypeClassResult.Local(good)));
		Assert.That(SymbolEqualityComparer.Default.Equals(resolver.GetTypeClassType(result!), target), Is.True);
	}

	[Test]
	public void FactoryArgumentUnificationPreservesPointerElementTypes() {
		var compilation = Compile("public class Box<T> {} public unsafe class PointerShapes { public static Box<T> Factory<T>(T* pointer) where T : unmanaged => throw new System.NotImplementedException(); }");
		var method = (IMethodSymbol)compilation.GetTypeByMetadataName("PointerShapes")!.GetMembers("Factory").Single();
		var integer = compilation.GetSpecialType(SpecialType.System_Int32);
		var pointer = compilation.CreatePointerTypeSymbol(integer);
		var result = new TypeClassResult.Method([integer], [new TypeClassResult.Local(new LocalInfo("pointer", pointer))], method);
		var expected = compilation.GetTypeByMetadataName("Box`1")!.Construct(integer);
		Assert.That(SymbolEqualityComparer.Default.Equals(new TypeClassResolver(compilation, []).GetTypeClassType(result), expected), Is.True);
	}

	[Test]
	public void SymbolConversionPreservesNestedGenericParentsAndRejectsFunctionPointers() {
		var compilation = Compile("namespace Models { public class Outer<T> { public class Inner<U> {} } public unsafe class Shapes { public Outer<int>.Inner<string> Nested; public delegate*<int, void> Function; } }");
		var shapes = compilation.GetTypeByMetadataName("Models.Shapes")!;
		var nested = (SourceModelType.NamedSymbol)SourceModelType.FromSymbol(((IFieldSymbol)shapes.GetMembers("Nested").Single()).Type);
		Assert.That(nested.Name, Is.EqualTo("Inner"));
		Assert.That(nested.TypeArguments, Is.EqualTo(new[] { SourceModelType.FromSymbol(compilation.GetSpecialType(SpecialType.System_String)) }));
		var outer = (SourceModelType.NamedSymbol)nested.Parent.Value!;
		Assert.That(outer.Name, Is.EqualTo("Outer"));
		Assert.That(outer.TypeArguments, Is.EqualTo(new[] { SourceModelType.FromSymbol(compilation.GetSpecialType(SpecialType.System_Int32)) }));
		Assert.That(((SourceModelType.NamespaceSymbolParent)outer.Parent.Value!).Namespace, Is.EqualTo(new[] { "Models" }));
		var function = ((IFieldSymbol)shapes.GetMembers("Function").Single()).Type;
		Assert.Throws<NotSupportedException>(() => SourceModelType.FromSymbol(function));
	}

	[Test]
	public void NullableReferenceSymbolPreservesItsUnderlyingType() {
		var compilation = Compile("#nullable enable\npublic class NullableShape { public string? Value; }");
		var field = (IFieldSymbol)compilation.GetTypeByMetadataName("NullableShape")!.GetMembers("Value").Single();
		var model = (SourceModelType.Nullable)SourceModelType.FromSymbol(field.Type);
		Assert.That(model.Inner, Is.EqualTo(SourceModelType.FromSymbol(compilation.GetSpecialType(SpecialType.System_String))));
	}

	[Test]
	public void LocalMatchingHandlesArrayElementsAndDynamicArguments() {
		var compilation = Compile("public class Probe<A, B> {} public class Shapes<T> { public Probe<T, T[]> Array; public Probe<T, string[]> WrongArray; public Probe<T, dynamic> Dynamic; public Probe<T, int> Integer; }");
		var shapes = compilation.GetTypeByMetadataName("Shapes`1")!;
		ITypeSymbol Field(string name) => ((IFieldSymbol)shapes.GetMembers(name).Single()).Type;
		var array = new LocalInfo("array", Field("Array"));
		Assert.That(new TypeClassResolver(compilation, [array]).Resolve(Field("Array")), Is.EqualTo(new TypeClassResult.Local(array)));
		Assert.That(new TypeClassResolver(compilation, [array]).Resolve(Field("WrongArray")), Is.Null);
		var dynamic = new LocalInfo("dynamicCodec", Field("Dynamic"));
		Assert.That(new TypeClassResolver(compilation, [dynamic]).Resolve(Field("Integer")), Is.EqualTo(new TypeClassResult.Local(dynamic)));
	}

	[Test]
	public void FactoryConstraintAcceptsTheExactBaseTypeAndRejectsAnUnrelatedType() {
		var compilation = Compile("using ESExpr.Runtime; public class Base {} public class Box<T> {} public static class Box { [TypeClassInstance] public static IESExprCodec<Box<T>> Codec<T>() where T : Base => throw new System.NotImplementedException(); }");
		var resolver = new TypeClassResolver(compilation, []);
		var codec = compilation.GetTypeByMetadataName("ESExpr.Runtime.IESExprCodec`1")!;
		var box = compilation.GetTypeByMetadataName("Box`1")!;
		Assert.That(resolver.Resolve(codec.Construct(box.Construct(compilation.GetTypeByMetadataName("Base")!))), Is.TypeOf<TypeClassResult.Method>());
		Assert.That(resolver.Resolve(codec.Construct(box.Construct(compilation.GetSpecialType(SpecialType.System_String)))), Is.Null);
	}
}
