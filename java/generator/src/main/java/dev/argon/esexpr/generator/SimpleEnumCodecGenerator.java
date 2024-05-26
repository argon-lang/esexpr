package dev.argon.esexpr.generator;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

final class SimpleEnumCodecGenerator extends GeneratorBase {
	public SimpleEnumCodecGenerator(PrintWriter writer, ProcessingEnvironment env, TypeElement elem) {
		super(writer, env, elem);
	}

	private List<VariableElement> getCases() {
		return elem.getEnclosedElements()
			.stream()
			.filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
			.map(e -> (VariableElement)e)
			.toList();
	}


	@Override
	protected void writeTagsImpl() throws IOException, AbortException {
		println("var tags = new java.util.HashSet<dev.argon.esexpr.ESExprTag>();");
		println("tags.add(new dev.argon.esexpr.ESExprTag.Str());");
		println("return tags;");
	}

	@Override
	protected void writeEncodeImpl() throws IOException, AbortException {
		println("var s = switch(value) {");
		indent();

		for(var c : getCases()) {
			print("case ");
			print(c.getSimpleName());
			print(" -> ");
			printStringLiteral(getConstructorNameSimpleEnum(c));
			println(";");
		}

		dedent();
		println("};");

		println("return new dev.argon.esexpr.ESExpr.Str(s);");
	}

	@Override
	protected void writeDecodeImpl() throws IOException, AbortException {
		println("if(expr instanceof dev.argon.esexpr.ESExpr.Str(var s)) {");
		indent();

		println("return switch(s) {");
		indent();

		for(var c : getCases()) {
			print("case ");
			printStringLiteral(getConstructorNameSimpleEnum(c));
			print(" -> ");
			print(elem.getQualifiedName());
			print(".");
			print(c.getSimpleName());
			println(";");
		}

		println("default -> throw new dev.argon.esexpr.DecodeException(\"Invalid simple enum value\");");

		dedent();
		println("};");

		dedent();
		println("}");
		println("else {");
		indent();


		println("throw new dev.argon.esexpr.DecodeException(\"Simple enum must be a string\");");

		dedent();
		println("}");

	}
}
