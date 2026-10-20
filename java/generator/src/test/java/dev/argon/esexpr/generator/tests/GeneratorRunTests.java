package dev.argon.esexpr.generator.tests;

import dev.argon.esexpr.generator.ESExprGeneratorProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratorRunTests {

	@Test
	public void runGeneratorOnTestSources(@TempDir Path outputDirectory) throws IOException {
		Path sourceDirectory = Path.of(
			Objects.requireNonNull(
				System.getProperty("esexpr.generator.testSources"),
				"esexpr.generator.testSources must be set"
			)
		);
		List<Path> sourceFiles;
		try(var paths = Files.list(sourceDirectory)) {
			sourceFiles = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
		}
		assertFalse(sourceFiles.isEmpty());
		Path moduleInfo = outputDirectory.resolve("module-info.java");
		Files.writeString(moduleInfo, """
			module generator.fixtures {
			    requires dev.argon.esexpr;
			    requires com.google.common;
			    requires org.jspecify;
			    exports dev.argon.esexpr.generator.gen;
			}
			""");
		sourceFiles = new ArrayList<>(sourceFiles);
		sourceFiles.add(moduleInfo);

		var compiler = ToolProvider.getSystemJavaCompiler();
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		var compilerOutput = new StringWriter();
		try(var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
			var task = compiler.getTask(
				compilerOutput,
				fileManager,
				diagnostics,
				List.of(
					"--module-path",
					Objects.requireNonNull(System.getProperty("jdk.module.path"), "jdk.module.path must be set"),
					"-proc:only",
					"-s",
					outputDirectory.toString(),
					"-d",
					outputDirectory.toString()
				),
				null,
				fileManager.getJavaFileObjectsFromPaths(sourceFiles)
			);
			task.setProcessors(List.of(new ESExprGeneratorProcessor()));

			assertTrue(task.call(), () -> compilerOutput + "\n" + diagnostics.getDiagnostics());
		}
	}
}
