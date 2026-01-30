package dev.argon.esexpr.generator;

import com.google.common.collect.ImmutableSet;
import dev.argon.esexpr.Constructor;
import dev.argon.esexpr.ESExprTag;
import dev.argon.esexpr.ESExprTagSet;
import dev.argon.esexpr.InlineValue;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import static dev.argon.esexpr.generator.NameUtils.getConstructorName;

final class EnumCodecGenerator extends GeneratorBase {
	public EnumCodecGenerator(PrintWriter writer, ProcessingEnvironment env, MetadataCache metadataCache, TypeElement elem) {
		super(writer, env, metadataCache, elem);
	}


	private List<TypeElement> getCases() throws AbortException {
		return getEnumCases(elem, env);
	}

	public static List<TypeElement> getEnumCases(TypeElement elem, ProcessingEnvironment env) throws AbortException {
		var cases = elem.getEnclosedElements()
			.stream()
			.map(e -> e instanceof TypeElement te ? te : null)
			.filter(te -> {
				if(te == null) {
					return false;
				}

				return te.getInterfaces().stream().anyMatch((TypeMirror ifaceMirror) -> {
					var iface = (DeclaredType)ifaceMirror;
					if(iface.getTypeArguments().size() != elem.getTypeParameters().size()) {
						return false;
					}

					var elemType = env.getTypeUtils().getDeclaredType(elem, iface.getTypeArguments().toArray(TypeMirror[]::new));

					return env.getTypeUtils().isSameType(iface, elemType);
				});
			})
			.toList();

		if(cases.isEmpty()) {
			throw new AbortException("No cases found for enum " + elem.getQualifiedName(), elem);
		}

		return cases;
	}

	@Override
	protected void validateAnnotations() throws AbortException {
		if(elem.getAnnotation(Constructor.class) != null) {
			throw new AbortException("Constructor name may only be specified for records", elem);
		}
	}

	@Override
	protected void writeEncodedEqualImpl() throws IOException, AbortException {
		println("return switch(x) {");
		indent();

		for(var c : getCases()) {
			print("case ");
			print(elem.getQualifiedName());
			print(".");
			print(c.getSimpleName());
			printTypeArguments();
			println(" x2 -> {");
			indent();

			print("if(!(y instanceof ");
			print(elem.getQualifiedName());
			print(".");
			print(c.getSimpleName());
			printTypeArguments();
			println(" y2)) {");
			indent();
			println("yield false;");
			dedent();
			println("}");

			writeEncodedEqualFields(c, "x2", "y2", true);

			dedent();
			println("}");
		}

		dedent();
		println("};");
	}

	@Override
	protected void writeEncodeImpl() throws IOException, AbortException {
		ESExprTagSet tags = ESExprTagSet.of();

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

			ESExprTagSet caseTags;

			if(isInlineValue(c)) {
				var field = getFields(c).get(0);

				caseTags = metadataCache.lookupTags(field.asType(), elem, env);

				print("yield ");
				printCodecExpr(field.asType(), field);
				print(".encode(caseValue.");
				print(field.getSimpleName());
				println("());");
			}
			else {
				caseTags = ESExprTagSet.of(new ESExprTag.Constructor(getConstructorName(c)));
				writeEncodeFields(c, "caseValue", true);
			}

			if(!tags.isDisjoint(caseTags)) {
				env.getMessager().printError("Overlapping tags for enum: " + tags + " and " + caseTags, c);
			}

			tags = tags.union(caseTags);

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
				println("var args = new java.util.ArrayDeque<>(args0);");
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
		return c.getAnnotation(InlineValue.class) != null;
	}

}
