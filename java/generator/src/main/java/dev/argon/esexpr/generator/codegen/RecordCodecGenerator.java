package dev.argon.esexpr.generator.codegen;

import dev.argon.esexpr.generator.lookup.MetadataCache;

import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

import static dev.argon.esexpr.generator.utils.NameUtils.getConstructorName;

public final class RecordCodecGenerator extends GeneratorBase {
	public RecordCodecGenerator(PrintWriter writer, ProcessingEnvironment env, MetadataCache metadataCache, TypeElement elem) {
		super(writer, env, metadataCache, elem);
	}

	@Override
	protected void validateAnnotations() throws AbortException {

	}

	@Override
	protected void writeEncodedEqualImpl() throws IOException, AbortException {
		writeEncodedEqualFields(elem, "x", "y", false);
	}

	@Override
	protected void writeEncodeImpl() throws IOException, AbortException {
		writeEncodeFields(elem, "value", false);
	}

	@Override
	protected void writeDecodeImpl() throws IOException, AbortException {
		print("if(expr instanceof dev.argon.esexpr.ESExpr.Constructor(var name, var args0, var kwargs0) && name.equals(");
		printStringLiteral(getConstructorName(elem));
		println(")) {");
		indent();

		println("var args = new java.util.ArrayDeque<>(args0);");
		println("var kwargs = new java.util.HashMap<>(kwargs0);");

		writeDecodeFields(elem, false);

		dedent();
		println("}");
		println("else {");
		indent();

		print("throw new dev.argon.esexpr.DecodeException(");
		printStringLiteral("Expected a " + getConstructorName(elem) + " constructor");
		println(", path);");

		dedent();
		println("}");
	}
}
