package dev.argon.esexpr.generator.typeclass.tests;

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
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);

		var sourceFiles = List.of(
			new SimpleJavaFileObject(java.net.URI.create("string:///test/CustomType.java"), JavaFileObject.Kind.SOURCE) {
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

			new SimpleJavaFileObject(java.net.URI.create("string:///test/MyTypeClass.java"), JavaFileObject.Kind.SOURCE) {
				@Override
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return """
		            package test;
		            import dev.argon.esexpr.TypeClassInstance;
		            
		            import java.util.List;
		            
		            public interface MyTypeClass<T> {
		                @TypeClassInstance
		                public static final MyTypeClass<String> stringInstance = new MyTypeClass<String>() {};
		                
		                @TypeClassInstance
		                public static MyTypeClass<Integer> intInstance() { return null; }
		                
		                @TypeClassInstance
		                public static <T> MyTypeClass<List<T>> listInstance(MyTypeClass<T> elementInstance) { return null; }
		                
		                @TypeClassInstance
		                public static MyTypeClass<byte[]> byteArrayInstance = new MyTypeClass<byte[]>() {};
		            }
	            """;
				}
			},

			new SimpleJavaFileObject(java.net.URI.create("string:///test/TypeClassUser.java"), JavaFileObject.Kind.SOURCE) {
				@Override
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return """
		            package test;
		            import dev.argon.esexpr.TypeClassInstance;
		            import dev.argon.esexpr.Unsigned;
		            
		            import java.util.List;
		            
		            public interface TypeClassUser {
		                public static <T> void useTypeClass(MyTypeClass<T> instance) {}
		                
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
	        List.of("-proc:only", "--module-path", System.getProperty("jdk.module.path")),
	        null,
	        sourceFiles
        );

		final List<@Nullable TypeClassResult> results = new ArrayList<>();

        task.setProcessors(List.of(new AbstractProcessor() {
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
                if (roundEnv.processingOver()) return false;

                Elements elements = processingEnv.getElementUtils();
                Types types = processingEnv.getTypeUtils();

                TypeElement typeClassElement = elements.getTypeElement("test.MyTypeClass");
                TypeElement stringElement = elements.getTypeElement("java.lang.String");
                TypeElement integerElement = elements.getTypeElement("java.lang.Integer");
                TypeElement listElement = elements.getTypeElement("java.util.List");

                TypeClassResolver resolver = new TypeClassResolver(types, new TypeClassLocalScope(List.of()));

                // 1. Resolve MyTypeClass<String> -> should find field stringInstance
                results.add(resolver.resolve(typeClassElement, stringElement.asType()));

                // 2. Resolve MyTypeClass<Integer> -> should find method intInstance
                results.add(resolver.resolve(typeClassElement, integerElement.asType()));

                // 3. Resolve MyTypeClass<List<String>> -> should find method listInstance with arg stringInstance
                TypeMirror listStringType = types.getDeclaredType(listElement, stringElement.asType());
                results.add(resolver.resolve(typeClassElement, listStringType));
                
                // 4. Resolve MyTypeClass<List<Integer>> -> should find method listInstance with arg intInstance
                TypeMirror listIntType = types.getDeclaredType(listElement, integerElement.asType());
                results.add(resolver.resolve(typeClassElement, listIntType));

                // 5. Resolve MyTypeClass<CustomType> -> should find field tcInstance in CustomType
                TypeElement customTypeElement = elements.getTypeElement("test.CustomType");
                results.add(resolver.resolve(typeClassElement, customTypeElement.asType()));

				// 6. Resolve MyTypeClass<byte[]> -> should find field byteArrayInstance
	            TypeMirror byteArrayType = types.getArrayType(types.getPrimitiveType(TypeKind.BYTE));
				results.add(resolver.resolve(typeClassElement, byteArrayType));

				// 7. Resolve MyTypeClass<List<T>> with a local type T
	            var useTypeClassMethod = (ExecutableElement)elements.getTypeElement("test.TypeClassUser").getEnclosedElements().stream()
		            .filter(e -> e.getKind() == ElementKind.METHOD && e.getSimpleName().toString().equals("useTypeClass"))
		            .findFirst()
		            .orElseThrow();

				var useTypeClassTypeParam = useTypeClassMethod.getTypeParameters().get(0);
	            var useTypeClassParam = useTypeClassMethod.getParameters().get(0);

				resolver = new TypeClassResolver(
					types,
					new TypeClassLocalScope(List.of(useTypeClassParam))
				);
	            results.add(resolver.resolve(typeClassElement, types.getDeclaredType(listElement, useTypeClassTypeParam.asType())));

                return false;
            }
        }));

        assertTrue(task.call(), () -> "Compilation failed: " + diagnostics.getDiagnostics().stream().map(Object::toString).reduce("", (a, b) -> a + "\n" + b));

        assertNotNull(results.get(0));
        assertTrue(results.get(0) instanceof TypeClassResult.Field);
        assertEquals("stringInstance", ((TypeClassResult.Field) results.get(0)).field().getSimpleName().toString());

        assertNotNull(results.get(1));
        assertTrue(results.get(1) instanceof TypeClassResult.Method);
        assertEquals("intInstance", ((TypeClassResult.Method) results.get(1)).method().getSimpleName().toString());
        assertTrue(((TypeClassResult.Method) results.get(1)).arguments().isEmpty());

        assertNotNull(results.get(2));
        assertTrue(results.get(2) instanceof TypeClassResult.Method);
        assertEquals("listInstance", ((TypeClassResult.Method) results.get(2)).method().getSimpleName().toString());
        assertEquals(1, ((TypeClassResult.Method) results.get(2)).arguments().size());
        assertTrue(((TypeClassResult.Method) results.get(2)).arguments().get(0) instanceof TypeClassResult.Field);
        assertEquals("stringInstance", ((TypeClassResult.Field)((TypeClassResult.Method) results.get(2)).arguments().get(0)).field().getSimpleName().toString());
        
        assertNotNull(results.get(3));
        assertTrue(results.get(3) instanceof TypeClassResult.Method);
        assertEquals("listInstance", ((TypeClassResult.Method) results.get(3)).method().getSimpleName().toString());
        assertEquals(1, ((TypeClassResult.Method) results.get(3)).arguments().size());
        assertTrue(((TypeClassResult.Method) results.get(3)).arguments().get(0) instanceof TypeClassResult.Method);
        assertEquals("intInstance", ((TypeClassResult.Method)((TypeClassResult.Method)results.get(3)).arguments().get(0)).method().getSimpleName().toString());

        assertNotNull(results.get(4));
        assertTrue(results.get(4) instanceof TypeClassResult.Field);
        assertEquals("tcInstance", ((TypeClassResult.Field) results.get(4)).field().getSimpleName().toString());

		assertNotNull(results.get(5));
        assertTrue(results.get(5) instanceof TypeClassResult.Field);
        assertEquals("byteArrayInstance", ((TypeClassResult.Field) results.get(5)).field().getSimpleName().toString());

		assertNotNull(results.get(6));
		assertTrue(results.get(6) instanceof TypeClassResult.Method);
	    assertEquals("listInstance", ((TypeClassResult.Method) results.get(6)).method().getSimpleName().toString());
	    assertEquals(1, ((TypeClassResult.Method) results.get(6)).arguments().size());
		assertTrue(((TypeClassResult.Method) results.get(6)).arguments().get(0) instanceof TypeClassResult.Local);
	    assertEquals("instance", ((TypeClassResult.Local)((TypeClassResult.Method)results.get(6)).arguments().get(0)).localVar().getSimpleName().toString());
    }
}
