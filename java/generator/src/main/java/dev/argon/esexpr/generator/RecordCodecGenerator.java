package dev.argon.esexpr.generator;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;

final class RecordCodecGenerator extends GeneratorBase {
	public RecordCodecGenerator(PrintWriter writer, ProcessingEnvironment env, TypeElement elem) {
		super(writer, env, elem);
	}

	@Override
	protected void writeTagsImpl() throws IOException, AbortException {
		println("var tags = new java.util.HashSet<dev.argon.esexpr.ESExprTag>();");
		print("tags.add(new dev.argon.esexpr.ESExprTag.Constructor(");
		printStringLiteral(getConstructorName(elem));
		println("));");
		println("return tags;");
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

		println("var args = new java.util.ArrayList<>(args0);");
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
