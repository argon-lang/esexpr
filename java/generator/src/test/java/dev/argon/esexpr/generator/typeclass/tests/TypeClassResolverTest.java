package dev.argon.esexpr.generator.typeclass.tests;

import com.google.common.collect.ImmutableList;
import dev.argon.esexpr.generator.typeclass.TypeClassLocalScope;
import dev.argon.esexpr.generator.typeclass.TypeClassResolver;
import dev.argon.esexpr.generator.typeclass.TypeClassResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TypeClassResolverTest {

	@Test
	public void testResolution() throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		StandardJavaFileManager fileManager = compiler
			.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);

		var sourceFiles = ImmutableList.of(
			new SimpleJavaFileObject(
				java.net.URI.create("string:///test/CustomType.java"),
				JavaFileObject.Kind.SOURCE
			) {
				@Override
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return """
						 package test;
						 import dev.argon.esexpr.TypeClassInstance;

						 public class CustomType {

						     @TypeClassInstance
						     public static final MyTypeClass<CustomType> tcInstance = new MyTypeClass<CustomType>() {};

						     @TypeClassInstance
						     public static final MyTypeClass<String> customStringInstance = new MyTypeClass<String>() {};

						 }
						""";
				}
			},

			new SimpleJavaFileObject(
				java.net.URI.create("string:///test/MyTypeClass.java"),
				JavaFileObject.Kind.SOURCE
			) {
				@Override
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return """
						 package test;
						 import dev.argon.esexpr.TypeClassInstance;

						 import java.util.List;
						 import java.util.Set;

						 public interface MyTypeClass<T> {
						     @TypeClassInstance
						     public static final MyTypeClass<String> stringInstance = new MyTypeClass<String>() {};

						     @TypeClassInstance
						     public static MyTypeClass<Integer> intInstance() { return null; }

						     @TypeClassInstance
						     public static <T> MyTypeClass<List<T>> listInstance(MyTypeClass<T> elementInstance) { return null; }

						     @TypeClassInstance
						     public static MyTypeClass<byte[]> byteArrayInstance = new MyTypeClass<byte[]>() {};
						     @TypeClassInstance
						     public static <T> MyTypeClass<T[]> arrayInstance(MyTypeClass<T> element) { return null; }
						     record Pair<A, B>() {}
						     interface Binary<A, B> {}
						     class Outer<T> { public class Inner {} }
						     @TypeClassInstance
						     public static MyTypeClass<Outer<String>.Inner> nestedInstance() { return null; }
						     @TypeClassInstance
						     public static <T> MyTypeClass<Pair<T, T>> pairInstance(MyTypeClass<T> element) { return null; }
						     @TypeClassInstance
						     public static <T, U> MyTypeClass<Set<T>> uninferred(MyTypeClass<U> element) { return null; }
						     public static <T> MyTypeClass<T> unbound() { return null; }
						     public static <T> MyTypeClass<T[]> unboundArray() { return null; }
						     public static <T> MyTypeClass<? extends T> unboundExtends() { return null; }
						     public static <T> MyTypeClass<? super T> unboundSuper() { return null; }
						     public static <T> MyTypeClass<? extends T> extending(MyTypeClass<T> element) { return null; }
						     public static <T> MyTypeClass<? super T> accepting(MyTypeClass<T> element) { return null; }
						     public static MyTypeClass<?> wildcard() { return null; }
						     @TypeClassInstance
						     public static MyTypeClass<Short> fixed(MyTypeClass<String> element) { return null; }
						     @TypeClassInstance
						     public static MyTypeClass<Double> recursive(MyTypeClass<Double> element) { return null; }
						     @TypeClassInstance
						     private static MyTypeClass<Long> privateInstance() { return null; }
						     @TypeClassInstance
						     default MyTypeClass<Long> nonStaticInstance() { return null; }
						 }
						""";
				}
			},

			new SimpleJavaFileObject(
				java.net.URI.create("string:///test/TypeClassUser.java"),
				JavaFileObject.Kind.SOURCE
			) {
				@Override
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return """
						 package test;
						 import dev.argon.esexpr.TypeClassInstance;
						 import dev.argon.esexpr.Unsigned;

						 import java.util.List;
						 import java.util.Set;

						 public interface TypeClassUser {
						     public static <T> void useTypeClass(MyTypeClass<T> instance) {}
						     public static <T> void useCodec(dev.argon.esexpr.ESExprCodec<T> codec) {}
						     public static <T, U> void binary(MyTypeClass.Binary<T, T> wrong) {}
						     public static <T, U> void locals(MyTypeClass<U> wrong, MyTypeClass<T> right,
						         MyTypeClass<T[]> array, MyTypeClass raw) {}

						     public static List<@Unsigned Integer> unsignedList() { return null; }

						 }
						""";
				}
			},

			new SimpleJavaFileObject(java.net.URI.create("string:///module-info.java"), JavaFileObject.Kind.SOURCE) {
				@Override
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return """
						module test {
							requires dev.argon.esexpr;

							exports test;
						}
						""";
				}
			}
		);

		JavaCompiler.CompilationTask task = compiler.getTask(
			null,
			fileManager,
			diagnostics,
			ImmutableList.of(
				"-proc:only",
				"--module-path",
				Objects.requireNonNull(System.getProperty("jdk.module.path"), "jdk.module.path must be set")
			),
			null,
			sourceFiles
		);

		final List<@Nullable TypeClassResult> results = new ArrayList<>();

		task.setProcessors(ImmutableList.of(new AbstractProcessor() {
			@Override
			public Set<String> getSupportedAnnotationTypes() {
				return Set.of("*");
			}

			@Override
			public SourceVersion getSupportedSourceVersion() {
				return SourceVersion.latestSupported();
			}

			@Override
			public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
				if(roundEnv.processingOver())
					return false;

				Elements elements = processingEnv.getElementUtils();
				Types types = processingEnv.getTypeUtils();

				TypeElement typeClassElement = elements.getTypeElement("test.MyTypeClass");
				TypeElement stringElement = elements.getTypeElement("java.lang.String");
				TypeElement integerElement = elements.getTypeElement("java.lang.Integer");
				TypeElement listElement = elements.getTypeElement("java.util.List");

				TypeClassResolver resolver = new TypeClassResolver(types, new TypeClassLocalScope(ImmutableList.of()));

				// 1. Resolve MyTypeClass<String> -> should find field stringInstance
				results.add(resolver.resolve(typeClassElement, stringElement.asType()));

				// 2. Resolve MyTypeClass<Integer> -> should find method intInstance
				results.add(resolver.resolve(typeClassElement, integerElement.asType()));

				// 3. Resolve MyTypeClass<List<String>> -> should find method listInstance with
				// arg stringInstance
				TypeMirror listStringType = types.getDeclaredType(listElement, stringElement.asType());
				results.add(resolver.resolve(typeClassElement, listStringType));

				// 4. Resolve MyTypeClass<List<Integer>> -> should find method listInstance with
				// arg intInstance
				TypeMirror listIntType = types.getDeclaredType(listElement, integerElement.asType());
				results.add(resolver.resolve(typeClassElement, listIntType));

				// 5. Resolve MyTypeClass<CustomType> -> should find field tcInstance in
				// CustomType
				TypeElement customTypeElement = elements.getTypeElement("test.CustomType");
				results.add(resolver.resolve(typeClassElement, customTypeElement.asType()));

				// 6. Resolve MyTypeClass<byte[]> -> should find field byteArrayInstance
				TypeMirror byteArrayType = types.getArrayType(types.getPrimitiveType(TypeKind.BYTE));
				results.add(resolver.resolve(typeClassElement, byteArrayType));

				// 7. Resolve MyTypeClass<List<T>> with a local type T
				var useTypeClassMethod = (ExecutableElement)elements.getTypeElement("test.TypeClassUser")
					.getEnclosedElements().stream()
					.filter(
						e -> e.getKind() == ElementKind.METHOD && e.getSimpleName().toString().equals("useTypeClass")
					).findFirst().orElseThrow();

				var useTypeClassTypeParam = useTypeClassMethod.getTypeParameters().get(0);
				var useTypeClassParam = useTypeClassMethod.getParameters().get(0);

				resolver = new TypeClassResolver(types, new TypeClassLocalScope(ImmutableList.of(useTypeClassParam)));
				results.add(
					resolver
						.resolve(typeClassElement, types.getDeclaredType(listElement, useTypeClassTypeParam.asType()))
				);

				assertTrue(
					types.isSameType(
						resolver.getTypeClassType(Objects.requireNonNull(results.get(0))),
						types.getDeclaredType(typeClassElement, stringElement.asType())
					)
				);
				assertTrue(
					types.isSameType(
						resolver.getTypeClassType(Objects.requireNonNull(results.get(2))),
						types.getDeclaredType(typeClassElement, listStringType)
					)
				);
				var localResult = ((TypeClassResult.Method)Objects.requireNonNull(results.get(6))).arguments().get(0);
				assertTrue(types.isSameType(resolver.getTypeClassType(localResult), useTypeClassParam.asType()));
				var checkedResolver = resolver;
				var listMethod = ((TypeClassResult.Method)results.get(2)).method();
				assertThrows(
					IllegalArgumentException.class,
					() -> checkedResolver.getTypeClassType(new TypeClassResult.Method(listMethod, ImmutableList.of()))
				);
				var fixedMethod = (ExecutableElement)typeClassElement.getEnclosedElements().stream()
					.filter(e -> e.getSimpleName().contentEquals("fixed")).findFirst().orElseThrow();
				assertThrows(
					IllegalArgumentException.class,
					() -> checkedResolver.getTypeClassType(
						new TypeClassResult.Method(
							fixedMethod,
							ImmutableList.of(Objects.requireNonNull(results.get(1)))
						)
					)
				);
				var unboundMethod = (ExecutableElement)typeClassElement.getEnclosedElements().stream()
					.filter(e -> e.getSimpleName().contentEquals("unbound")).findFirst().orElseThrow();
				assertThrows(
					IllegalArgumentException.class,
					() -> checkedResolver
						.getTypeClassType(new TypeClassResult.Method(unboundMethod, ImmutableList.of()))
				);
				for(var name : List.of("extending", "accepting", "wildcard")) {
					var method = (ExecutableElement)typeClassElement.getEnclosedElements().stream()
						.filter(e -> e.getSimpleName().contentEquals(name)).findFirst().orElseThrow();
					var arguments = name.equals("wildcard")
						? ImmutableList.<TypeClassResult>of()
						: ImmutableList.of(Objects.requireNonNull(results.get(0)));
					var reconstructed = resolver.getTypeClassType(new TypeClassResult.Method(method, arguments));
					var suffix = switch(name) {
						case "extending" -> "? extends java.lang.String";
						case "accepting" -> "? super java.lang.String";
						default -> "?";
					};
					assertEquals("test.MyTypeClass<" + suffix + ">", reconstructed.toString());
				}
				for(var name : List.of("unboundArray", "unboundExtends", "unboundSuper")) {
					var method = (ExecutableElement)typeClassElement.getEnclosedElements().stream()
						.filter(e -> e.getSimpleName().contentEquals(name)).findFirst().orElseThrow();
					var error = assertThrows(
						IllegalArgumentException.class,
						() -> checkedResolver.getTypeClassType(new TypeClassResult.Method(method, ImmutableList.of()))
					);
					assertEquals("Could not substitute types for method " + method, error.getMessage());
				}
				var arrayResult = resolver.resolve(typeClassElement, types.getArrayType(stringElement.asType()));
				assertInstanceOf(TypeClassResult.Method.class, arrayResult);
				assertTrue(
					types.isSameType(
						resolver.getTypeClassType(Objects.requireNonNull(arrayResult)),
						types.getDeclaredType(typeClassElement, types.getArrayType(stringElement.asType()))
					)
				);
				assertNull(resolver.resolve(typeClassElement, elements.getTypeElement("java.lang.Double").asType()));
				assertNull(resolver.resolve(typeClassElement, elements.getTypeElement("java.lang.Long").asType()));
				assertNull(
					resolver.resolve(
						typeClassElement,
						types.getDeclaredType(elements.getTypeElement("java.util.Set"), stringElement.asType())
					)
				);
				assertNull(
					resolver.resolve(
						typeClassElement,
						types.getArrayType(elements.getTypeElement("java.lang.Long").asType())
					)
				);
				assertNull(resolver.resolve(types.getPrimitiveType(TypeKind.INT)));
				var emptyScope = new TypeClassResolver(types, new TypeClassLocalScope(ImmutableList.of()));
				assertNull(emptyScope.resolve(typeClassElement, useTypeClassTypeParam.asType()));
				var locals = (ExecutableElement)elements.getTypeElement("test.TypeClassUser").getEnclosedElements()
					.stream().filter(e -> e.getSimpleName().contentEquals("locals")).findFirst().orElseThrow();
				var target = types.getDeclaredType(typeClassElement, locals.getTypeParameters().get(0).asType());
				var mismatched = new TypeClassResolver(
					types,
					new TypeClassLocalScope(List.of(locals.getParameters().get(0)))
				);
				assertNull(mismatched.resolve(target));
				var matching = new TypeClassResolver(
					types,
					new TypeClassLocalScope(List.copyOf(locals.getParameters()))
				);
				var matched = assertInstanceOf(TypeClassResult.Local.class, matching.resolve(target));
				assertEquals(locals.getParameters().get(1), matched.localVar());
				assertNull(
					new TypeClassResolver(types, new TypeClassLocalScope(List.of(locals.getParameters().get(2))))
						.resolve(target)
				);
				assertNull(
					new TypeClassResolver(types, new TypeClassLocalScope(List.of(locals.getParameters().get(3))))
						.resolve(target)
				);
				assertNull(emptyScope.resolve(typeClassElement, types.getDeclaredType(listElement)));
				var pairElement = elements.getTypeElement("test.MyTypeClass.Pair");
				assertInstanceOf(
					TypeClassResult.Method.class,
					emptyScope.resolve(
						typeClassElement,
						types.getDeclaredType(pairElement, stringElement.asType(), stringElement.asType())
					)
				);
				assertNull(
					emptyScope.resolve(
						typeClassElement,
						types.getDeclaredType(pairElement, stringElement.asType(), integerElement.asType())
					)
				);
				var stringArray = types.getArrayType(stringElement.asType());
				assertInstanceOf(
					TypeClassResult.Method.class,
					emptyScope.resolve(typeClassElement, types.getDeclaredType(pairElement, stringArray, stringArray))
				);
				assertNull(
					emptyScope.resolve(
						typeClassElement,
						types.getDeclaredType(pairElement, stringArray, types.getArrayType(integerElement.asType()))
					)
				);
				assertNull(
					emptyScope
						.resolve(typeClassElement, types.getDeclaredType(pairElement, listStringType, listIntType))
				);
				assertInstanceOf(
					TypeClassResult.Method.class,
					emptyScope
						.resolve(typeClassElement, types.getDeclaredType(pairElement, listStringType, listStringType))
				);
				assertNull(emptyScope.resolve(typeClassElement, types.getDeclaredType(pairElement)));
				var outer = elements.getTypeElement("test.MyTypeClass.Outer");
				var inner = elements.getTypeElement("test.MyTypeClass.Outer.Inner");
				var stringInner = types.getDeclaredType(types.getDeclaredType(outer, stringElement.asType()), inner);
				var integerInner = types.getDeclaredType(types.getDeclaredType(outer, integerElement.asType()), inner);
				assertInstanceOf(TypeClassResult.Method.class, emptyScope.resolve(typeClassElement, stringInner));
				assertNull(emptyScope.resolve(typeClassElement, integerInner));
				assertInstanceOf(
					TypeClassResult.Method.class,
					emptyScope.resolve(typeClassElement, types.getDeclaredType(pairElement, stringInner, stringInner))
				);
				assertNull(
					emptyScope.resolve(typeClassElement, types.getDeclaredType(pairElement, stringInner, integerInner))
				);
				var useCodec = (ExecutableElement)elements.getTypeElement("test.TypeClassUser").getEnclosedElements()
					.stream().filter(e -> e.getSimpleName().contentEquals("useCodec")).findFirst().orElseThrow();
				checkTagLookup(processingEnv, useCodec, elements.getTypeElement("java.lang.Thread").asType());
				var binaryMethod = (ExecutableElement)elements.getTypeElement("test.TypeClassUser")
					.getEnclosedElements().stream().filter(e -> e.getSimpleName().contentEquals("binary")).findFirst()
					.orElseThrow();
				var binaryClass = elements.getTypeElement("test.MyTypeClass.Binary");
				var binaryResolver = new TypeClassResolver(
					types,
					new TypeClassLocalScope(List.copyOf(binaryMethod.getParameters()))
				);
				var firstType = binaryMethod.getTypeParameters().get(0).asType();
				var secondType = binaryMethod.getTypeParameters().get(1).asType();
				assertNull(binaryResolver.resolve(types.getDeclaredType(binaryClass, firstType, secondType)));
				var binaryLocal = assertInstanceOf(
					TypeClassResult.Local.class,
					binaryResolver.resolve(types.getDeclaredType(binaryClass, firstType, firstType))
				);
				assertEquals(binaryMethod.getParameters().get(0), binaryLocal.localVar());
				checkCacheReuse(
					emptyScope,
					matching,
					mismatched,
					target,
					types.getDeclaredType(typeClassElement, stringElement.asType())
				);

				return false;
			}
		}));

		assertTrue(
			task.call(),
			() -> "Compilation failed: "
				+ diagnostics.getDiagnostics().stream().map(Object::toString).reduce("", (a, b) -> a + "\n" + b)
		);

		assertNotNull(results.get(0));
		assertTrue(results.get(0) instanceof TypeClassResult.Field);
		assertEquals("stringInstance", ((TypeClassResult.Field)results.get(0)).field().getSimpleName().toString());

		assertNotNull(results.get(1));
		assertTrue(results.get(1) instanceof TypeClassResult.Method);
		assertEquals("intInstance", ((TypeClassResult.Method)results.get(1)).method().getSimpleName().toString());
		assertTrue(((TypeClassResult.Method)results.get(1)).arguments().isEmpty());

		assertNotNull(results.get(2));
		assertTrue(results.get(2) instanceof TypeClassResult.Method);
		assertEquals("listInstance", ((TypeClassResult.Method)results.get(2)).method().getSimpleName().toString());
		assertEquals(1, ((TypeClassResult.Method)results.get(2)).arguments().size());
		assertTrue(((TypeClassResult.Method)results.get(2)).arguments().get(0) instanceof TypeClassResult.Field);
		assertEquals(
			"stringInstance",
			((TypeClassResult.Field)((TypeClassResult.Method)results.get(2)).arguments().get(0)).field().getSimpleName()
				.toString()
		);

		assertNotNull(results.get(3));
		assertTrue(results.get(3) instanceof TypeClassResult.Method);
		assertEquals("listInstance", ((TypeClassResult.Method)results.get(3)).method().getSimpleName().toString());
		assertEquals(1, ((TypeClassResult.Method)results.get(3)).arguments().size());
		assertTrue(((TypeClassResult.Method)results.get(3)).arguments().get(0) instanceof TypeClassResult.Method);
		assertEquals(
			"intInstance",
			((TypeClassResult.Method)((TypeClassResult.Method)results.get(3)).arguments().get(0)).method()
				.getSimpleName().toString()
		);

		assertNotNull(results.get(4));
		assertTrue(results.get(4) instanceof TypeClassResult.Field);
		assertEquals("tcInstance", ((TypeClassResult.Field)results.get(4)).field().getSimpleName().toString());

		assertNotNull(results.get(5));
		assertTrue(results.get(5) instanceof TypeClassResult.Field);
		assertEquals("byteArrayInstance", ((TypeClassResult.Field)results.get(5)).field().getSimpleName().toString());

		assertNotNull(results.get(6));
		assertTrue(results.get(6) instanceof TypeClassResult.Method);
		assertEquals(
			"listInstance",
			((TypeClassResult.Method)Objects.requireNonNull(results.get(6))).method().getSimpleName().toString()
		);
		assertEquals(1, ((TypeClassResult.Method)Objects.requireNonNull(results.get(6))).arguments().size());
		assertTrue(
			((TypeClassResult.Method)Objects.requireNonNull(results.get(6))).arguments()
				.get(0) instanceof TypeClassResult.Local
		);
		assertEquals(
			"instance",
			((TypeClassResult.Local)((TypeClassResult.Method)Objects.requireNonNull(results.get(6))).arguments().get(0))
				.localVar().getSimpleName().toString()
		);
	}

	private static void checkCacheReuse(
		TypeClassResolver global,
		TypeClassResolver matching,
		TypeClassResolver mismatched,
		TypeMirror localType,
		TypeMirror globalType
	) {
		try {
			var cacheClass = Class.forName("dev.argon.esexpr.generator.lookup.MetadataCache");
			var constructor = cacheClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			var cache = constructor.newInstance();
			var resolve = cacheClass.getDeclaredMethod("resolveTypeClass", TypeClassResolver.class, TypeMirror.class);
			resolve.setAccessible(true);
			var first = resolve.invoke(cache, global, globalType);
			assertInstanceOf(TypeClassResult.Field.class, first);
			assertSame(first, resolve.invoke(cache, global, globalType));
			assertInstanceOf(TypeClassResult.Local.class, resolve.invoke(cache, matching, localType));
			assertNull(resolve.invoke(cache, mismatched, localType));
			assertNull(resolve.invoke(cache, global, localType));
			assertInstanceOf(TypeClassResult.Local.class, resolve.invoke(cache, matching, localType));
		}
		catch(ReflectiveOperationException ex) {
			throw new LinkageError(ex.getMessage(), ex);
		}
	}

	private static void checkTagLookup(
		ProcessingEnvironment env,
		ExecutableElement useCodec,
		TypeMirror missingCodecType
	) {
		try {
			var cacheClass = Class.forName("dev.argon.esexpr.generator.lookup.MetadataCache");
			var cacheConstructor = cacheClass.getDeclaredConstructor();
			cacheConstructor.setAccessible(true);
			var lookupClass = Class.forName("dev.argon.esexpr.generator.lookup.Lookup");
			var constructor = lookupClass.getDeclaredConstructor(
				ProcessingEnvironment.class,
				cacheClass,
				TypeClassLocalScope.class,
				Element.class
			);
			constructor.setAccessible(true);
			var lookup = constructor.newInstance(
				env,
				cacheConstructor.newInstance(),
				new TypeClassLocalScope(List.copyOf(useCodec.getParameters())),
				useCodec
			);
			var tags = lookupClass.getDeclaredMethod("lookupTags", TypeMirror.class);
			tags.setAccessible(true);
			assertEquals(
				new dev.argon.esexpr.ESExprTagSet.All(),
				tags.invoke(lookup, useCodec.getTypeParameters().get(0).asType())
			);
			var error = assertThrows(
				java.lang.reflect.InvocationTargetException.class,
				() -> tags.invoke(lookup, missingCodecType)
			);
			assertEquals(
				"Could not find codec for java.lang.Thread",
				Objects.requireNonNull(error.getCause()).getMessage()
			);
			var kindClass = Class.forName("dev.argon.esexpr.generator.lookup.CodecType");
			var elementType = lookupClass.getDeclaredMethod("findCodecElementType", TypeMirror.class, kindClass);
			elementType.setAccessible(true);
			var valueKind = kindClass.getField("VALUE").get(null);
			var varargKind = kindClass.getField("VARARG").get(null);
			var valueError = assertThrows(
				java.lang.reflect.InvocationTargetException.class,
				() -> elementType.invoke(lookup, missingCodecType, valueKind)
			);
			assertInstanceOf(IllegalArgumentException.class, valueError.getCause());
			assertEquals(
				"Cannot find codec element type for VALUE codec",
				Objects.requireNonNull(valueError.getCause()).getMessage()
			);
			var missingError = assertThrows(
				java.lang.reflect.InvocationTargetException.class,
				() -> elementType.invoke(lookup, missingCodecType, varargKind)
			);
			assertEquals(
				"Could not find VARARG for java.lang.Thread",
				Objects.requireNonNull(missingError.getCause()).getMessage()
			);
		}
		catch(ReflectiveOperationException ex) {
			throw new LinkageError(ex.getMessage(), ex);
		}
	}
}
