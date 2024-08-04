/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package dev.argon.esexpr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

class BinaryEncodingTest {
	@ParameterizedTest
	@MethodSource("fileProvider")
	public void binaryEncoding(Path jsonPath) throws Exception {
		var esxbPath = jsonPath.resolveSibling(FilenameUtils.removeExtension(jsonPath.getFileName().toString()) + ".esxb");


		var jsonValue = parseJson(Files.readString(jsonPath));
		var esxbValue = parseEsxb(Files.readAllBytes(esxbPath));

		assertEquals(jsonValue, esxbValue);
	}

	private ESExpr parseJson(String value) throws Exception {
		var mapper = new ObjectMapper();

		var module = new SimpleModule();
		module.addDeserializer(ESExpr.class, new ESExprJsonDeserializer());
		mapper.registerModule(module);

		return mapper.readValue(value, ESExpr.class);
	}

	private ESExpr parseEsxb(byte[] value) throws Exception {
		var exprs = ESExprBinaryReader.readEmbeddedStringTable(new ByteArrayInputStream(value)).toList();

		assertEquals(1, exprs.size());
		return exprs.getFirst();
	}


	public static Stream<Path> fileProvider() throws IOException {
		Path directory = Paths.get("../../tests");
		return Files.list(directory)
			.filter(Files::isRegularFile)
			.filter(p -> FilenameUtils.getExtension(p.getFileName().toString()).equals("json"));
	}
}
