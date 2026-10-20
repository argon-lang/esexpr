package dev.argon.esexpr.generator.tests;

import dev.argon.esexpr.generator.ESExprGeneratorProcessor;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class GeneratorValidationTests {

	@TempDir
	public Path outputDirectory;

	private record InvalidSource(String name, String source, String error) {}

	@TestFactory
	public Stream<DynamicTest> invalidDefinitions() {
		return Stream.of(
			new InvalidSource(
				"unsigned float",
				"@ESExprCodecGen public record Example(@Unsigned float value) {}",
				"Unsupported unsigned type: @dev.argon.esexpr.Unsigned float"
			),
			new InvalidSource(
				"unsigned reference",
				"@ESExprCodecGen public record Example(@Unsigned String value) {}",
				"Unsupported unsigned type: java.lang.@dev.argon.esexpr.Unsigned String"
			),
			new InvalidSource(
				"missing field codec",
				"@ESExprCodecGen public record Example(Thread value) {}",
				"Could not find codec (dev.argon.esexpr.ESExprCodec) for type java.lang.Thread"
			),
			new InvalidSource(
				"unknown optional codec",
				"@ESExprCodecGen public record Example(@OptionalValue String value) {}",
				"Could not find codec (dev.argon.esexpr.OptionalValueCodec) for type java.lang.String"
			),
			new InvalidSource(
				"class",
				"@ESExprCodecGen public class Example {}",
				"ESExprCodeGen must be used with a record, sealed interface (of records), or an enum."
			),
			new InvalidSource(
				"unsealed interface",
				"@ESExprCodecGen public interface Example {}",
				"ESExprCodeGen must be used with a record, sealed interface (of records), or an enum."
			),
			new InvalidSource(
				"enum constructor",
				"@ESExprCodecGen @Constructor(\"x\") public enum Example { A }",
				"Constructor name may only be specified for records"
			),
			new InvalidSource(
				"sealed constructor",
				"@ESExprCodecGen @Constructor(\"x\") public sealed interface Example { record A() implements Example {} }",
				"Constructor name may only be specified for records"
			),
			new InvalidSource(
				"flags constructor",
				"@ESExprCodecGen @Flags @Constructor(\"x\") public record Example() {}",
				"Constructor name may not be specified for flags"
			),
			new InvalidSource(
				"missing mask",
				"@ESExprCodecGen @Flags public record Example(boolean a) {}",
				"Missing @FlagMask"
			),
			new InvalidSource(
				"negative mask",
				"@ESExprCodecGen @Flags public record Example(@FlagMask(-1) boolean a) {}",
				"Flag mask must not be negative"
			),
			new InvalidSource(
				"zero mask",
				"@ESExprCodecGen @Flags public record Example(@FlagMask(0) boolean a) {}",
				"Flag mask must be a single bit"
			),
			new InvalidSource(
				"multiple bits",
				"@ESExprCodecGen @Flags public record Example(@FlagMask(3) boolean a) {}",
				"Flag mask must be a single bit"
			),
			new InvalidSource(
				"malformed mask",
				"@ESExprCodecGen @Flags public record Example(@FlagMask(bits=\"0xZZ\") boolean a) {}",
				"Invalid flag mask"
			),
			new InvalidSource(
				"overlapping masks",
				"@ESExprCodecGen @Flags public record Example(@FlagMask(1) boolean a, @FlagMask(1) boolean b) {}",
				"Duplicate flag mask: 1"
			),
			new InvalidSource(
				"primitive flag",
				"@ESExprCodecGen @Flags public record Example(int a) {}",
				"Invalid field type for flags: int"
			),
			new InvalidSource(
				"reference flag",
				"@ESExprCodecGen @Flags public record Example(String a) {}",
				"Invalid field type for flags: java.lang.String"
			),
			new InvalidSource(
				"duplicate enum masks",
				"@ESExprCodecGen @Flags public record Example(Bits a) { public enum Bits { @FlagMask(1) A, @FlagMask(1) B } }",
				"Duplicate enum mask for B"
			),
			new InvalidSource(
				"duplicate keyword",
				"@ESExprCodecGen public record Example(@Keyword(\"x\") int a, @Keyword(\"x\") int b) {}",
				"Duplicate keyword argument: x"
			),
			new InvalidSource(
				"multiple dictionaries",
				"@ESExprCodecGen public record Example(@Dict KeywordMapping<String> a, @Dict KeywordMapping<String> b) {}",
				"Only a single dict argument is allowed"
			),
			new InvalidSource(
				"keyword before dictionary",
				"@ESExprCodecGen public record Example(@Keyword int a, @Dict KeywordMapping<String> b) {}",
				"Keyword arguments cannot be used with dict arguments"
			),
			new InvalidSource(
				"dictionary before keyword",
				"@ESExprCodecGen public record Example(@Dict KeywordMapping<String> a, @Keyword int b) {}",
				"Keyword arguments cannot be used with dict arguments"
			)
		).map(test -> DynamicTest.dynamicTest(test.name(), () -> compile(test.source(), test.error())));
	}

	@TestFactory
	public Stream<DynamicTest> flagMaskFormats() {
		return Stream.of("0x10", "0X10", "0b10000", "0B10000", "020", "16", "18446744073709551616").map(
			mask -> DynamicTest.dynamicTest(
				mask,
				() -> compile(
					"@ESExprCodecGen @Flags public record Example(@FlagMask(bits=\"" + mask + "\") boolean a) {}",
					""
				)
			)
		);
	}

	@Test
	public void enumMasksWithSmallAndLargeValues() throws IOException {
		compile("""
			@ESExprCodecGen @Flags public record Example(Bits bits) {
			    public enum Bits {
			        @FlagMask(0) ZERO,
			        @FlagMask(1) ONE,
			        @FlagMask(2) TWO,
			        @FlagMask(10) TEN,
			        @FlagMask(16) SIXTEEN,
			        @FlagMask(bits="18446744073709551616") LARGE
			    }
			}
			""", "");
	}

	@Test
	public void fieldBasedCodecInstance() throws IOException {
		compile("""
			@ESExprCodecGen public record Example(String value) {
			    @TypeClassInstance public static final ESExprCodec<Example> CODEC = Example_CodecImpl.INSTANCE;
			}
			""", "");
	}

	@Test
	public void duplicateEnumNames() throws IOException {
		compile("""
			@ESExprCodecGen public enum Example {
			    @Constructor("same") A, @Constructor("same") B;
			    @TypeClassInstance public static ESExprCodec<Example> codec() { return Example_CodecImpl.INSTANCE; }
			}
			""", "Duplicate enum value: same");
	}

	@Test
	public void sealedInterfaceWithoutNestedCases() throws IOException {
		compile("""
			@ESExprCodecGen public sealed interface Example permits Outside {
			    @TypeClassInstance static ESExprCodec<Example> codec() { return Example_CodecImpl.INSTANCE; }
			}
			record Outside() implements Example {}
			""", "No cases found for enum validation.Example");
	}

	@Test
	public void circularInlineTags() throws IOException {
		compile("""
			@ESExprCodecGen public sealed interface Example {
			    @InlineValue record Recursive(Example value) implements Example {}
			    @TypeClassInstance static ESExprCodec<Example> codec() { return Example_CodecImpl.INSTANCE; }
			}
			""", "Circular tags detected for type validation.Example");
	}

	@Test
	public void overlappingEnumTags() throws IOException {
		compile("""
			@ESExprCodecGen public sealed interface Example {
			    @InlineValue record First(String value) implements Example {}
			    @InlineValue record Second(String value) implements Example {}
			    @TypeClassInstance static ESExprCodec<Example> codec() { return Example_CodecImpl.INSTANCE; }
			}
			""", "Overlapping tags for enum: Tags[tags=[STR]] and Tags[tags=[STR]]");
	}

	@Test
	public void genericInlineTags() throws IOException {
		compile("""
			@ESExprCodecGen public sealed interface Example<T> {
			    @InlineValue record Value<T>(T value) implements Example<T> {}
			    @TypeClassInstance static <T> ESExprCodec<Example<T>> codec(ESExprCodec<T> element) {
			        return new Example_CodecImpl<T>(element);
			    }
			}
			""", "");
	}

	@Test
	public void unrestrictedCustomTags() throws IOException {
		compile("""
			@ESExprCodecGen public sealed interface Example {
			    @InlineValue record Case(Value value) implements Example {}
			    @TypeClassInstance static ESExprCodec<Example> codec() { return Example_CodecImpl.INSTANCE; }
			    record Value() {
			        @TypeClassInstance @ESExprCodecTags(all=true)
			        public static ESExprCodec<Value> codec() { return null; }
			    }
			}
			""", "");
	}

	@Test
	public void customCodecTags() throws IOException {
		compile("""
			@ESExprCodecGen public record Example(@OptionalValue java.util.Optional<Value> value) {
			    public record Value() {
			        @TypeClassInstance @ESExprCodecTags(constructors={"custom"})
			        public static final ESExprCodec<Value> CODEC = null;
			    }
			}
			""", "");
	}

	@Test
	public void missingCodecTags() throws IOException {
		compile(
			"""
				@ESExprCodecGen public record Example(@OptionalValue java.util.Optional<Value> value) {
				    public record Value() {
				        @TypeClassInstance public static ESExprCodec<Value> codec() { return null; }
				    }
				}
				""",
			"TypeClassInstances of ESExprCodec must be annotated with ESExprCodecTags, but validation.Example.Value.codec() is not"
		);
	}

	@Test
	public void unknownTagTypeParameter() throws IOException {
		compile("""
			@ESExprCodecGen public record Example(@OptionalValue java.util.Optional<Value> value) {
			    public record Value() {
			        @TypeClassInstance @ESExprCodecTags(unionWithTypeParameters={"Missing"})
			        public static ESExprCodec<Value> codec() { return null; }
			    }
			}
			""", "Could not determine tags for type parameter Missing");
	}

	private void compile(String source, String expectedError) throws IOException {
		compile(source, expectedError, true);
	}

	@Test
	public void nestedArrayAndMultipleInstanceParameters() throws IOException {
		compile("""
			@ESExprCodecGen public record Example<T>(T value) {
			    public static class Outer<A> { public class Inner<B> {} }
			    @TypeClassInstance public static <T> ESExprCodec<Example<T>> codec(
			        ESExprCodec<T> element,
			        Outer<String>.Inner<java.util.Map<String, Integer>> nested,
			        ESExprCodec<T>[] codecs, T[] values, int count
			    ) {
			        return new Example_CodecImpl<T>(element, nested, codecs, values, count);
			    }
			}
			""", "", false);
	}

	@TestFactory
	public Stream<DynamicTest> missingInstanceDeclarations() {
		return Stream.of(
			"@ESExprCodecGen public record Example() {}",
			"@ESExprCodecGen public record Example() { @TypeClassInstance public static int codec() { return 0; } }",
			"@ESExprCodecGen public record Example() { @TypeClassInstance public static final int CODEC = 0; }",
			"@ESExprCodecGen public record Example() { public static ESExprCodec<Example> codec() { return null; } }",
			"@ESExprCodecGen public record Example() { @TypeClassInstance public static String codec() { return null; } }",
			"@ESExprCodecGen public record Example() { @TypeClassInstance public static final String CODEC = null; }"
		).map(
			source -> DynamicTest.dynamicTest(
				source,
				() -> compile(source, "Could not find ESExprCodec TypeClassInstance method", false)
			)
		);
	}

	@Test
	public void unrestrictedTagsFromGenericParameter() throws IOException {
		compile("""
			@ESExprCodecGen public sealed interface Example<T> {
			    @InlineValue record Case<T>(Box<T> value) implements Example<T> {}
			    @TypeClassInstance static <T> ESExprCodec<Example<T>> codec(ESExprCodec<T> element) {
			        return new Example_CodecImpl<T>(element);
			    }
			    record Box<T>(T value) {
			        @TypeClassInstance @ESExprCodecTags(unionWithTypeParameters={"T"})
			        public static <T> ESExprCodec<Box<T>> codec(ESExprCodec<T> element) { return null; }
			    }
			}
			""", "", false);
	}

	@Test
	public void unrestrictedTagsAfterOptionalPositionalField() throws IOException {
		compile("""
			@ESExprCodecGen public record Example<T>(@OptionalValue java.util.Optional<String> first, T second) {
			    @TypeClassInstance public static <T> ESExprCodec<Example<T>> codec(ESExprCodec<T> element) {
			        return new Example_CodecImpl<T>(element);
			    }
			}
			""", "Field 'second' cannot follow optional positional arguments with all tags", false);
	}

	private void compile(String source, String expectedError, boolean addInstance) throws IOException {
		compile(source, expectedError, addInstance, false);
	}

	@Test
	public void defaultPackageGeneration() throws IOException {
		compile("""
			@ESExprCodecGen public record Example(String value) {
			    @TypeClassInstance public static ESExprCodec<Example> codec() { return Example_CodecImpl.INSTANCE; }
			}
			""", "", false, true);
	}

	@Test
	public void codecFactoryWithMultipleArguments() throws IOException {
		compile("""
			@ESExprCodecGen public record Example(Pair<String, Integer> value) {
			    public record Pair<A, B>(A first, B second) {
			        @TypeClassInstance public static <A, B> ESExprCodec<Pair<A, B>> codec(
			            ESExprCodec<A> first, ESExprCodec<B> second
			        ) { return null; }
			    }
			}
			""", "");
	}

	private void compile(String source, String expectedError, boolean addInstance, boolean defaultPackage)
		throws IOException {
		if(addInstance && source.contains("public record Example") && !source.contains("ESExprCodec<Example>")) {
			int bodyStart = source.indexOf('{') + 1;
			source = source.substring(
				0,
				bodyStart
			) + " @TypeClassInstance public static ESExprCodec<Example> codec() { return Example_CodecImpl.INSTANCE; } "
				+ source.substring(bodyStart);
		}
		Path directory = Files.createTempDirectory(outputDirectory, "compile-");
		Path moduleInfo = directory.resolve("module-info.java");
		Files.writeString(moduleInfo, "module generator.validation { requires dev.argon.esexpr; }");
		Path sourceFile = directory.resolve("Example.java");
		Files.writeString(
			sourceFile,
			(defaultPackage ? "" : "package validation; ") + "import dev.argon.esexpr.*; " + source
		);
		var compiler = ToolProvider.getSystemJavaCompiler();
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		var output = new StringWriter();
		try(var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
			var task = compiler.getTask(
				output,
				fileManager,
				diagnostics,
				List.of(
					defaultPackage ? "--class-path" : "--module-path",
					Objects.requireNonNull(System.getProperty("jdk.module.path"), "jdk.module.path must be set"),
					"-s",
					directory.toString(),
					"-d",
					directory.toString()
				),
				null,
				fileManager
					.getJavaFileObjectsFromPaths(defaultPackage ? List.of(sourceFile) : List.of(moduleInfo, sourceFile))
			);
			task.setProcessors(List.of(new ESExprGeneratorProcessor()));
			boolean success = task.call();
			var errors = diagnostics.getDiagnostics().stream()
				.filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
				.map(diagnostic -> diagnostic.getMessage(Locale.ROOT)).toList();
			assertEquals(expectedError.isEmpty(), success, () -> output + "\n" + errors);
			assertEquals(expectedError.isEmpty() ? List.of() : List.of(expectedError), errors);
		}
	}

	@Test
	public void genericCaseWithDifferentInterfaceArity() throws IOException {
		compile("""
			@ESExprCodecGen public sealed interface Example<T> {
			    record Case<T>(T value) implements Runnable, Example<T> { public void run() {} }
			    @TypeClassInstance static <T> ESExprCodec<Example<T>> codec(ESExprCodec<T> element) {
			        return new Example_CodecImpl<T>(element);
			    }
			}
			""", "", false);
	}

	@Test
	public void processorOutputFailure() throws IOException {
		Path directory = Files.createTempDirectory(outputDirectory, "io-failure-");
		Path source = directory.resolve("Example.java");
		Files.writeString(source, """
			import dev.argon.esexpr.*;
			@ESExprCodecGen public record Example() {
			    @TypeClassInstance public static ESExprCodec<Example> codec() { return Example_CodecImpl.INSTANCE; }
			}
			""");
		var compiler = ToolProvider.getSystemJavaCompiler();
		var failure = new IOException("Generated source output unavailable");
		try(var standard = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
			var fileManager = new ForwardingJavaFileManager<>(standard) {
				@Override
				public JavaFileObject getJavaFileForOutput(
					JavaFileManager.Location location,
					String className,
					JavaFileObject.Kind kind,
					@Nullable FileObject sibling
				) throws IOException {
					if(kind == JavaFileObject.Kind.SOURCE)
						throw failure;
					return super.getJavaFileForOutput(location, className, kind, sibling);
				}

				@Override
				public JavaFileObject getJavaFileForOutputForOriginatingFiles(
					JavaFileManager.Location location,
					String className,
					JavaFileObject.Kind kind,
					FileObject... originatingFiles
				) throws IOException {
					if(kind == JavaFileObject.Kind.SOURCE)
						throw failure;
					return super.getJavaFileForOutputForOriginatingFiles(location, className, kind, originatingFiles);
				}
			}) {
			var task = compiler.getTask(
				new StringWriter(),
				fileManager,
				null,
				List.of(
					"-proc:only",
					"--class-path",
					Objects.requireNonNull(System.getProperty("jdk.module.path"), "jdk.module.path must be set"),
					"-s",
					directory.toString()
				),
				null,
				standard.getJavaFileObjectsFromPaths(List.of(source))
			);
			task.setProcessors(List.of(new ESExprGeneratorProcessor()));
			var error = assertThrows(RuntimeException.class, task::call);
			Throwable cause = error;
			while (cause.getCause() != null)
				cause = java.util.Objects.requireNonNull(cause.getCause());
			assertSame(failure, cause);
		}
	}
}
