package dev.argon.esexpr.generator;

import dev.argon.esexpr.Constructor;
import dev.argon.esexpr.ESExprTag;
import dev.argon.esexpr.ESExprTagSet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

final class SimpleEnumCodecGenerator extends GeneratorBase {
	public SimpleEnumCodecGenerator(PrintWriter writer, ProcessingEnvironment env, MetadataCache metadataCache, TypeElement elem) {
		super(writer, env, metadataCache, elem);
	}

	private List<VariableElement> getCases() {
		return elem.getEnclosedElements()
			.stream()
			.filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
			.map(e -> (VariableElement)e)
			.toList();
	}

	@Override
	protected void validateAnnotations() throws AbortException {
		if(elem.getAnnotation(Constructor.class) != null) {
			throw new AbortException("Constructor name may only be specified for records", elem);
		}
	}

	@Override
	protected ESExprTagSet getTags(Element associatedElement) throws AbortException {
		return ESExprTagSet.of(ESExprTag.STR);
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

		println("default -> throw new dev.argon.esexpr.DecodeException(\"Invalid simple enum value\", path);");

		dedent();
		println("};");

		dedent();
		println("}");
		println("else {");
		indent();


		println("throw new dev.argon.esexpr.DecodeException(\"Simple enum must be a string\", path);");

		dedent();
		println("}");

	}
}
