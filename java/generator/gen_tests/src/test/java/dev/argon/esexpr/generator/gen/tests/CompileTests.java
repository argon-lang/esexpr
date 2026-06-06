package dev.argon.esexpr.generator.gen.tests;

import org.junit.jupiter.api.Test;

import javax.tools.*;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import dev.argon.esexpr.generator.ESExprGeneratorProcessor;

import static org.junit.jupiter.api.Assertions.*;

public class CompileTests {

	private static final String MODULE_INFO = """
		module dev.argon.esexpr.gen_error_tests {
			requires dev.argon.esexpr;
			exports dev.argon.esexpr.gen_error_tests;
		}
	""";

	private void assertFails(String errorMessage, String className, String sourceCode) throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

		try(StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {

			JavaFileObject moduleInfo = new InMemoryJavaFileObject("module-info", MODULE_INFO);

			JavaFileObject javaFileObject = new InMemoryJavaFileObject(className, sourceCode);

			Iterable<? extends JavaFileObject> compilationUnits = List.of(javaFileObject, moduleInfo);
			JavaCompiler.CompilationTask task = compiler.getTask(
				Writer.nullWriter(),
				fileManager,
				diagnostics,
				List.of(
					"--module-path",
					System.getProperty("jdk.module.path"),
					"-s",
					"build/test_output_files",
					"-d",
					"build/test_output_files"
				),
				null,
				compilationUnits
			);

			task.setProcessors(List.of(new ESExprGeneratorProcessor()));

			assertFalse(task.call());

			assertEquals(List.of(errorMessage), diagnostics.getDiagnostics().stream().map(diag -> diag.getMessage(Locale.ROOT)).toList());
		}


	}


	private static class InMemoryJavaFileObject extends SimpleJavaFileObject {
		private final String sourceCode;

		private InMemoryJavaFileObject(String className, String sourceCode) {
			super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
			this.sourceCode = sourceCode;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return sourceCode;
		}
	}

	private static final String IMPORTS = """
  		package dev.argon.esexpr.gen_error_tests;
		import dev.argon.esexpr.*;
		import java.util.*;
	""";

	@Test
	public void generateForClass() throws Throwable {
		assertFails(
			"ESExprCodeGen must be used with a record, sealed interface (of records), or an enum.",
			"HelloWorld",
			IMPORTS + "@ESExprCodecGen public class HelloWorld {}"
		);
	}

	@Test
	public void generateForInterface() throws Throwable {
		assertFails(
			"ESExprCodeGen must be used with a record, sealed interface (of records), or an enum.",
			"HelloWorld",
			IMPORTS + "@ESExprCodecGen public interface HelloWorld {}"
		);
	}

	@Test
	public void constructorOnEnum() throws Throwable {
		assertFails(
			"Constructor name may only be specified for records",
			"ConstructorNameEnum",
			IMPORTS + """
				@ESExprCodecGen
				@Constructor("my-ctor")
				public sealed interface ConstructorNameEnum {
					record A() implements ConstructorNameEnum {}
					
					@TypeClassInstance
					static ESExprCodec<ConstructorNameEnum> codec() { return ConstructorNameEnum_CodecImpl.INSTANCE; }
				}
				"""
		);
	}

	@Test
	public void kwargWithDict() throws Throwable {
		assertFails(
			"Keyword arguments cannot be used with dict arguments",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@Dict
					KeywordMapping<String> a,
					
					@Keyword
					String b
				) {
					
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
		assertFails(
			"Keyword arguments cannot be used with dict arguments",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@Keyword
					String b,
					
					@Dict
					KeywordMapping<String> a
				) {
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
	}

	@Test
	public void multipleDict() throws Throwable {
		assertFails(
			"Only a single dict argument is allowed",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@Dict
					KeywordMapping<String> a,
					
					@Dict
					KeywordMapping<String> b
				) {
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
	}

	@Test
	public void multipleVararg() throws Throwable {
		assertFails(
			"Field 'b' must have distinct tags from immediately preceding optional positional arguments",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@Vararg
					List<String> a,
					
					@Vararg
					List<String> b
				) {
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
	}

	@Test
	public void argAfterVararg() throws Throwable {
		assertFails(
			"Field 'b' must have distinct tags from immediately preceding optional positional arguments",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@Vararg
					List<String> a,
					
					String b
				) {
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
	}

	@Test
	public void optionalArgAfterVararg() throws Throwable {
		assertFails(
			"Field 'b' must have distinct tags from immediately preceding optional positional arguments",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@Vararg
					List<String> a,
					
					@OptionalValue
					Optional<String> b
				) {
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
	}

	@Test
	public void multipleOptionalPos() throws Throwable {
		assertFails(
			"Field 'b' must have distinct tags from immediately preceding optional positional arguments",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@OptionalValue
					Optional<String> a,
					
					@OptionalValue
					Optional<String> b
				) {
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
	}

	@Test
	public void multiplePosDefault() throws Throwable {
		assertFails(
			"Field 'b' must have distinct tags from immediately preceding optional positional arguments",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@DefaultValue("\\"A\\"") String a,
					@DefaultValue("\\"A\\"") String b
				) {
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
	}

	@Test
	public void argAfterOptionalPos() throws Throwable {
		assertFails(
			"Field 'b' must have distinct tags from immediately preceding optional positional arguments",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@OptionalValue
					Optional<String> a,
					
					String b
				) {
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
	}

	@Test
	public void argAfterPosDefault() throws Throwable {
		assertFails(
			"Field 'b' must have distinct tags from immediately preceding optional positional arguments",
			"MyRecord",
			IMPORTS + """
				@ESExprCodecGen
				public record MyRecord(
					@DefaultValue("\\"A\\"")
					Optional<String> a,
					
					String b
				) {
					@TypeClassInstance
					static ESExprCodec<MyRecord> codec() { return MyRecord_CodecImpl.INSTANCE; }
				}
				"""
		);
	}


}
