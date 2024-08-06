package dev.argon.esexpr.generator;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

final class EnumCodecGenerator extends GeneratorBase {
	public EnumCodecGenerator(PrintWriter writer, ProcessingEnvironment env, MetadataCache metadataCache, TypeElement elem) {
		super(writer, env, metadataCache, elem);
	}


	private List<TypeElement> getCases() {
		return elem.getEnclosedElements()
			.stream()
			.map(e -> e instanceof TypeElement te ? te : null)
			.filter(te -> te != null && te.getInterfaces().stream().anyMatch((TypeMirror iface) -> env.getTypeUtils().isSameType(iface, elem.asType())))
			.toList();
	}

	@Override
	protected void writeTagsImpl() throws IOException, AbortException {
		println("var tags = new java.util.HashSet<dev.argon.esexpr.ESExprTag>();");
		for(var c : getCases()) {
			if(isInlineValue(c)) {
				var field = getFields(c).get(0);
				print("tags.addAll(");
				printCodecExpr(field.asType(), field);
				println(".tags());");
			}
			else {
				print("tags.add(new dev.argon.esexpr.ESExprTag.Constructor(");
				printStringLiteral(getConstructorName(c));
				println("));");	
			}
		}
		println("return tags;");
	}

	@Override
	protected void writeEncodeImpl() throws IOException, AbortException {
		println("return switch(value) {");
		indent();

		for(var c : getCases()) {
			print("case ");
			print(elem.getQualifiedName());
			print(".");
			print(c.getSimpleName());
			printTypeArguments();
			println(" caseValue -> {");
			indent();

			if(isInlineValue(c)) {
				var field = getFields(c).get(0);
				print("yield ");
				printCodecExpr(field.asType(), field);
				print(".encode(caseValue.");
				print(field.getSimpleName());
				println("());");
			}
			else {
				writeEncodeFields(c, "caseValue", true);
			}


			dedent();
			println("}");
		}

		dedent();
		println("};");
	}

	@Override
	protected void writeDecodeImpl() throws IOException, AbortException {
		println("return switch(expr) {");
		indent();

		for(var c : getCases()) {
			if(isInlineValue(c)) {
				var field = getFields(c).get(0);

				print("case _ when ");
				printCodecExpr(field.asType(), field);
				println(".tags().contains(expr.tag()) -> {");
				indent();

				print("var inner = ");
				printCodecExpr(field.asType(), field);
				println(".decode(expr, path);");

				print("yield new ");
				print(elem.getQualifiedName());
				print(".");
				print(c.getSimpleName());
				printTypeArguments();
				println("(inner);");

				dedent();
				println("}");

			}
			else {
				print("case dev.argon.esexpr.ESExpr.Constructor(var name, var args0, var kwargs0) when name.equals(");
				printStringLiteral(getConstructorName(c));
				println(") -> {");
				indent();
				println("var args = new java.util.ArrayList<>(args0);");
				println("var kwargs = new java.util.HashMap<>(kwargs0);");
	
				writeDecodeFields(c, true);
	
				dedent();
				println("}");	
			}
		}

		println("default -> throw new dev.argon.esexpr.DecodeException(\"Unexpected value for enum\", path);");

		dedent();
		println("};");
	}

	private boolean isInlineValue(TypeElement c) {
		return hasAnnotation(c.getAnnotationMirrors(), "dev.argon.esexpr.InlineValue");
	}

}
