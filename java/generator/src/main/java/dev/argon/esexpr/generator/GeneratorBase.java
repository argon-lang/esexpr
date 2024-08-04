package dev.argon.esexpr.generator;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

import org.apache.commons.text.StringEscapeUtils;

abstract class GeneratorBase {
	public GeneratorBase(PrintWriter writer, ProcessingEnvironment env, TypeElement elem) {
		this.writer = writer;
		this.env = env;
		this.elem = elem;
	}

	private final PrintWriter writer;
	protected final ProcessingEnvironment env;
	protected final TypeElement elem;
	private int indentLevel = 0;
	private boolean needsIndent = true;


	public final void generate() throws IOException, AbortException {
		writePackage();
		writeClassImpl();
	}

	protected void indent() {
		indentLevel += 1;
	}

	protected void dedent() {
		indentLevel -= 1;
	}

	protected void print(CharSequence s) throws IOException {
		if(needsIndent) {
			for(int i = 0; i < indentLevel; ++i) {
				writer.print("\t");
			}

			needsIndent = false;
		}

		writer.print(s);
	}

	protected void println() throws IOException {
		writer.println();
		needsIndent = true;
	}

	protected void println(CharSequence s) throws IOException {
		print(s);
		println();
	}

	protected void printStringLiteral(String s) throws IOException {
		print("\"");
		print(StringEscapeUtils.escapeJava(s));
		print("\"");
	}

	protected void printCodecExpr(TypeMirror t, Element associatedElement) throws IOException, AbortException {
		switch(t.getKind()) {
			case BOOLEAN -> print("dev.argon.esexpr.ESExprCodec.BOOLEAN_CODEC");
			case BYTE -> {
				if(hasAnnotation(t.getAnnotationMirrors(), "dev.argon.esexpr.Unsigned")) {
					print("dev.argon.esexpr.ESExprCodec.UNSIGNED_BYTE_CODEC");
				}
				else {
					print("dev.argon.esexpr.ESExprCodec.SIGNED_BYTE_CODEC");
				}
			}
			case SHORT -> {
				if(hasAnnotation(t.getAnnotationMirrors(), "dev.argon.esexpr.Unsigned")) {
					print("dev.argon.esexpr.ESExprCodec.UNSIGNED_SHORT_CODEC");
				}
				else {
					print("dev.argon.esexpr.ESExprCodec.SIGNED_SHORT_CODEC");
				}
			}
			case INT -> {
				if(hasAnnotation(t.getAnnotationMirrors(), "dev.argon.esexpr.Unsigned")) {
					print("dev.argon.esexpr.ESExprCodec.UNSIGNED_INT_CODEC");
				}
				else {
					print("dev.argon.esexpr.ESExprCodec.SIGNED_INT_CODEC");
				}
			}
			case LONG -> {
				if(hasAnnotation(t.getAnnotationMirrors(), "dev.argon.esexpr.Unsigned")) {
					print("dev.argon.esexpr.ESExprCodec.UNSIGNED_LONG_CODEC");
				}
				else {
					print("dev.argon.esexpr.ESExprCodec.SIGNED_LONG_CODEC");
				}
			}
			case FLOAT -> print("dev.argon.esexpr.ESExprCodec.FLOAT_CODEC");
			case DOUBLE -> print("dev.argon.esexpr.ESExprCodec.DOUBLE_CODEC");

			case TYPEVAR -> {
				print(nameToCamelCase(((TypeVariable)t).asElement().getSimpleName().toString()));
				print("Codec");
			}

			case DECLARED -> {
				var declType = (DeclaredType)t;
				var tElem = (TypeElement)declType.asElement();

				switch(tElem.getQualifiedName().toString()) {
					case "java.lang.String" -> print("dev.argon.esexpr.ESExprCodec.STRING_CODEC");
					case "java.math.BigInteger" -> {
						if(hasAnnotation(t.getAnnotationMirrors(), "dev.argon.esexpr.Unsigned")) {
							print("dev.argon.esexpr.ESExprCodec.NAT_CODEC");
						}
						else {
							print("dev.argon.esexpr.ESExprCodec.BIG_INTEGER_CODEC");
						}
					}
					case "java.lang.Boolean" -> print("dev.argon.esexpr.ESExprCodec.BOOLEAN_CODEC");
					case "java.lang.Byte" -> {
						if(hasAnnotation(t.getAnnotationMirrors(), "dev.argon.esexpr.Unsigned")) {
							print("dev.argon.esexpr.ESExprCodec.UNSIGNED_BYTE_CODEC");
						}
						else {
							print("dev.argon.esexpr.ESExprCodec.SIGNED_BYTE_CODEC");
						}
					}
					case "java.lang.Short" -> {
						if(hasAnnotation(t.getAnnotationMirrors(), "dev.argon.esexpr.Unsigned")) {
							print("dev.argon.esexpr.ESExprCodec.UNSIGNED_SHORT_CODEC");
						}
						else {
							print("dev.argon.esexpr.ESExprCodec.SIGNED_SHORT_CODEC");
						}
					}
					case "java.lang.Integer" -> {
						if(hasAnnotation(t.getAnnotationMirrors(), "dev.argon.esexpr.Unsigned")) {
							print("dev.argon.esexpr.ESExprCodec.UNSIGNED_INT_CODEC");
						}
						else {
							print("dev.argon.esexpr.ESExprCodec.SIGNED_INT_CODEC");
						}
					}
					case "java.lang.Long" -> {
						if(hasAnnotation(t.getAnnotationMirrors(), "dev.argon.esexpr.Unsigned")) {
							print("dev.argon.esexpr.ESExprCodec.UNSIGNED_LONG_CODEC");
						}
						else {
							print("dev.argon.esexpr.ESExprCodec.SIGNED_LONG_CODEC");
						}
					}
					case "java.lang.Float" -> print("dev.argon.esexpr.ESExprCodec.FLOAT_CODEC");
					case "java.lang.Double" -> print("dev.argon.esexpr.ESExprCodec.DOUBLE_CODEC");

					case "java.util.List" -> {
						print("dev.argon.esexpr.ESExprCodec.listCodec(");
						int i = 0;
						for(var arg : declType.getTypeArguments()) {
							if(i > 0) {
								print(", ");
							}
							++i;

							printCodecExpr(arg, associatedElement);
						}
						print(")");
					}

					case "java.util.Optional" -> {
						print("dev.argon.esexpr.ESExprCodec.optionalCodec(");
						int i = 0;
						for(var arg : declType.getTypeArguments()) {
							if(i > 0) {
								print(", ");
							}
							++i;

							printCodecExpr(arg, associatedElement);
						}
						print(")");
					}

					case String s -> {
						print("new ");
						print(s);
						print("_Codec");

						if(declType.getTypeArguments().isEmpty()) {
							print("()");
						}
						else {
							print("<>(");
							int i = 0;
							for(var arg : declType.getTypeArguments()) {
								if(i > 0) {
									print(", ");
								}
								++i;

								printCodecExpr(arg, associatedElement);
							}
							print(")");
						}

					}
				}
			}

			case ARRAY -> {
				if(((ArrayType)t).getComponentType().getKind() == TypeKind.BYTE) {
					print("dev.argon.esexpr.ESExprCodec.BYTE_ARRAY_CODEC");
				}
				else {
					throw new AbortException("Unexpected type for codec: " + t, associatedElement);
				}
			}
			
			default -> {
				throw new AbortException("Unexpected type for codec: " + t, associatedElement);
			}
		}
	}

	protected boolean hasAnnotation(List<? extends AnnotationMirror> annotations, String name) {
		return getAnnotation(annotations, name).isPresent();
	}

	protected Optional<? extends AnnotationMirror> getAnnotation(List<? extends AnnotationMirror> annotations, String name) {
		return annotations.stream()
			.filter(ann -> ((TypeElement)ann.getAnnotationType().asElement()).getQualifiedName().toString().equals(name))
			.findFirst();
	}

	protected Optional<AnnotationValue> getAnnotationArgument(AnnotationMirror ann, String name) {
		return ann.getElementValues()
			.entrySet()
			.stream()
			.filter(entry -> entry.getKey().getSimpleName().toString().equals(name))
			.findFirst()
			.map(Map.Entry::getValue);
	}

	private final String NAME_SPLIT_PATTERN = "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])_(?=[0-9])";
	private String nameToKebabCase(String name) {
		return Arrays.stream(name.split(NAME_SPLIT_PATTERN))
			.map(s -> s.toLowerCase(Locale.ROOT))
			.collect(Collectors.joining("-"));
	}

	private String nameToCamelCase(String name) {
		var parts = name.split(NAME_SPLIT_PATTERN);
		parts[0] = parts[0].toLowerCase(Locale.ROOT);
		return String.join("", parts);
	}

	private String enumConstNameToKebabCase(String name) {
		return Arrays.stream(name.split("_"))
			.map(s -> s.toLowerCase(Locale.ROOT))
			.collect(Collectors.joining("-"));
	}


	private String getPackage() {
		String name = elem.getQualifiedName().toString();
		int lastDot = name.lastIndexOf('.');
		if(lastDot < 0) {
			return null;
		}

		return name.substring(0, lastDot);
	}


	protected String getConstructorName(TypeElement elem) {
		return getAnnotation(elem.getAnnotationMirrors(), "dev.argon.esexpr.Constructor")
			.map(ctorAnn -> ((String)getAnnotationArgument(ctorAnn, "value").get().getValue()))
			.orElseGet(() -> nameToKebabCase(elem.getSimpleName().toString()));
	}

	protected String getConstructorNameSimpleEnum(VariableElement elem) {
		return getAnnotation(elem.getAnnotationMirrors(), "dev.argon.esexpr.Constructor")
			.map(ctorAnn -> ((String)getAnnotationArgument(ctorAnn, "value").get().getValue()))
			.orElseGet(() -> enumConstNameToKebabCase(elem.getSimpleName().toString()));
	}




	private void writePackage() throws IOException {
		String pkg = getPackage();
		if(pkg != null) {
			print("package ");
			print(pkg);
			println(";");
		}
	}

	private void writeClassImpl() throws IOException, AbortException {
		print("public class ");
		print(elem.getSimpleName());
		print("_Codec");
		if(!elem.getTypeParameters().isEmpty()) {
			print("<");
			int i = 0;
			for(TypeParameterElement tp : elem.getTypeParameters()) {
				if(i > 0) {
					print(", ");
				}
				++i;

				print(tp.toString());
			}
			print(">");
		}
		print(" extends dev.argon.esexpr.ESExprCodec<");
		print(elem.getQualifiedName());
		printTypeArguments();
		println("> {");
		indent();

		if(elem.getTypeParameters().isEmpty()) {
			print("public static final dev.argon.esexpr.ESExprCodec<");
			print(elem.getQualifiedName());
			print("> INSTANCE = new ");
			print(elem.getQualifiedName());
			println("_Codec();");
		}
		else {
			println("public ");
			print(elem.getSimpleName());
			print("_Codec(");

			int i = 0;
			for(TypeParameterElement tp : elem.getTypeParameters()) {
				if(i > 0) {
					print(", ");
				}
				++i;

				print("dev.argon.esexpr.ESExprCodec<");
				print(tp.getSimpleName());
				print("> ");
				print(nameToCamelCase(tp.getSimpleName().toString()));
				print("Codec");
			}

			println(") {");
			indent();
			

			for(TypeParameterElement tp : elem.getTypeParameters()) {
				var name = nameToCamelCase(tp.getSimpleName().toString());
				print("this.");
				print(name);
				print("Codec = ");
				print(name);
				println("Codec;");
			}

			dedent();
			println("}");

			for(TypeParameterElement tp : elem.getTypeParameters()) {
				print("private final dev.argon.esexpr.ESExprCodec<");
				print(tp.getSimpleName());
				print("> ");
				print(nameToCamelCase(tp.getSimpleName().toString()));
				println("Codec;");
			}

		}

		println("@java.lang.Override");
		println("public java.util.Set<dev.argon.esexpr.ESExprTag> tags() {");
		indent();
		writeTagsImpl();
		dedent();
		println("}");

		println("@java.lang.Override");
		print("public dev.argon.esexpr.ESExpr encode(");
		print(elem.getQualifiedName());
		printTypeArguments();
		println(" value) {");
		indent();
		writeEncodeImpl();
		dedent();
		println("}");

		println("@java.lang.Override");
		print("public ");
		print(elem.getQualifiedName());
		printTypeArguments();
		println(" decode(dev.argon.esexpr.ESExpr expr, dev.argon.esexpr.ESExprCodec.FailurePath path) throws dev.argon.esexpr.DecodeException {");
		indent();
		writeDecodeImpl();
		dedent();
		println("}");


		dedent();
		println("}");
	}

	protected void printTypeArguments() throws IOException {
		if(!elem.getTypeParameters().isEmpty()) {
			print("<");
			int i = 0;
			for(TypeParameterElement tp : elem.getTypeParameters()) {
				if(i > 0) {
					print(", ");
				}
				++i;

				print(tp.getSimpleName());
			}
			print(">");
		}
	}


	protected List<RecordComponentElement> getFields(TypeElement te) {
		return te.getEnclosedElements()
			.stream()
			.map(e -> e instanceof RecordComponentElement rce ? rce : null)
			.filter(Objects::nonNull)
			.toList();
	}

	private Optional<? extends AnnotationMirror> getKeywordAnn(RecordComponentElement rce) {
		return getAnnotation(rce.getAnnotationMirrors(), "dev.argon.esexpr.Keyword");
	}

	private Optional<TypeMirror> getOptional(RecordComponentElement field) {
		return getAnnotation(field.getAnnotationMirrors(), "dev.argon.esexpr.OptionalValue")
			.flatMap(ann -> getAnnotationArgument(ann, "value"))
			.flatMap(value -> value.getValue() instanceof TypeMirror t ? Optional.of(t) : Optional.empty());
	}

	private Optional<String> getDefaultValue(RecordComponentElement field) {
		return getAnnotation(field.getAnnotationMirrors(), "dev.argon.esexpr.DefaultValue")
			.flatMap(ann -> getAnnotationArgument(ann, "value"))
			.flatMap(value -> value.getValue() instanceof String s ? Optional.of(s) : Optional.empty());

	}

	private Optional<TypeMirror> getVarArg(RecordComponentElement field) {
		return getAnnotation(field.getAnnotationMirrors(), "dev.argon.esexpr.VarArg")
			.flatMap(ann -> getAnnotationArgument(ann, "value"))
			.flatMap(value -> value.getValue() instanceof TypeMirror t ? Optional.of(t) : Optional.empty());
	}

	private Optional<TypeMirror> getDict(RecordComponentElement field) {
		return getAnnotation(field.getAnnotationMirrors(), "dev.argon.esexpr.Dict")
			.flatMap(ann -> getAnnotationArgument(ann, "value"))
			.flatMap(value -> value.getValue() instanceof TypeMirror t ? Optional.of(t) : Optional.empty());
	}

	private String getKeywordName(RecordComponentElement rce, AnnotationMirror ann) {
		return getAnnotationArgument(ann, "value")
			.map(arg -> (String)arg.getValue())
			.filter(s -> !s.isEmpty())
			.orElseGet(() -> nameToKebabCase(rce.getSimpleName().toString()));
	}

	protected void writeEncodeFields(TypeElement te, String valueVarName, boolean useYield) throws IOException, AbortException {

		boolean hasVarArgs = false;
		boolean hasDict = false;
		var kwargNames = new HashSet<String>();

		println("var args = new java.util.ArrayList<dev.argon.esexpr.ESExpr>();");
		println("var kwargs = new java.util.HashMap<java.lang.String, dev.argon.esexpr.ESExpr>();");

		for(var field : getFields(te)) {
			var kwAnn = getKeywordAnn(field).orElse(null);
			if(kwAnn != null) {
				var kwName = getKeywordName(field, kwAnn);

				if(!kwargNames.add(kwName)) {
					throw new AbortException("Duplicate keyword argument: " + kwName, field);
				}

				if(hasDict) {
					throw new AbortException("Keyword arguments must precede dict arguments", field);
				}

				var optionalValueCodec = getOptional(field).orElse(null);
				if(optionalValueCodec != null) {
					println("{");
					indent();

					print("var kwValue = ");
					writeFieldCodec(optionalValueCodec, field);
					print(".encodeOptional(");
					print(valueVarName);
					print(".");
					print(field.getSimpleName());
					println("()).orElse(null);");


					println("if(kwValue != null) {");
					indent();

					print("kwargs.put(");
					printStringLiteral(kwName);
					print(", kwValue);");

					dedent();
					println("}");

					dedent();
					println("}");
					continue;
				}


				var defaultValueMethod = getDefaultValue(field).orElse(null);
				if(defaultValueMethod != null) {
					boolean isPrimitiveField = field.asType().getKind().isPrimitive();

					print("if(");
					if(!isPrimitiveField) {
						print("!");
					}
					print(valueVarName);
					print(".");
					print(field.getSimpleName());
					print("()");

					if(isPrimitiveField) {
						print(" != ");
					}
					else {
						print(".equals(");
					}


					print(elem.getQualifiedName());
					if(te != elem) {
						print(".");
						print(te.getSimpleName());
					}
					print(".");
					print(defaultValueMethod);
					print("()");

					if(!isPrimitiveField) {
						print(")");
					}

					print(") { ");
				}

				print("kwargs.put(");
				printStringLiteral(kwName);
				print(", ");
				printCodecExpr(field.asType(), field);
				print(".encode(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				print("()));");

				if(defaultValueMethod != null) {
					print(" }");
				}

				println();
				continue;
			}

			var varargValueCodec = getVarArg(field).orElse(null);
			if(varargValueCodec != null) {
				if(hasVarArgs) {
					throw new AbortException("Only a single vararg is allowed", field);
				}
				hasVarArgs = true;

				print("for(var arg : ");
				writeFieldCodec(varargValueCodec, field);
				print(".encodeVarArg(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("())) {");
				indent();

				println("args.add(arg);");

				dedent();
				println("}");
				continue;
			}

			var dictValueCodec = getDict(field).orElse(null);
			if(dictValueCodec != null) {
				if(hasDict) {
					throw new AbortException("Only a single dict argument is allowed", field);
				}
				hasDict = true;

				print("for(var pair : ");
				writeFieldCodec(dictValueCodec, field);
				print(".encodeDict(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("()).entrySet()) {");
				indent();

				println("kwargs.put(pair.getKey(), pair.getValue());");

				dedent();
				println("}");
			}
			else {
				if(hasVarArgs) {
					throw new AbortException("Positional arguments must precede varargs", field);
				}

				print("args.add(");
				printCodecExpr(field.asType(), field);
				print(".encode(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("()));");
			}
		}
		
		if(useYield) {
			print("yield");
		}
		else {
			print("return");
		}
		print(" new dev.argon.esexpr.ESExpr.Constructor(");
		printStringLiteral(getConstructorName(te));
		println(", args, kwargs);");
	}

	protected void writeDecodeFields(TypeElement te, boolean useYield) throws IOException, AbortException {
		int positionalIndex = 0;
		for(var field : getFields(te)) {
			var kwAnn = getKeywordAnn(field).orElse(null);
			if(kwAnn != null) {
				String keywordName = getKeywordName(field, kwAnn);
				print("var expr_");
				print(field.getSimpleName());
				print(" = kwargs.remove(");
				printStringLiteral(keywordName);
				println(");");

				var optionalValueCodec = getOptional(field).orElse(null);
				if(optionalValueCodec != null) {
					print(field.asType().toString());
					print(" field_");
					print(field.getSimpleName());
					print(" = ");
					writeFieldCodec(optionalValueCodec, field);
					print(".decodeOptional(expr_");
					print(field.getSimpleName());
					print(" == null ? java.util.Optional.empty() : java.util.Optional.of(expr_");
					print(field.getSimpleName());
					print("), path.append(");
					printStringLiteral(getConstructorName(te));
					print(", ");
					printStringLiteral(keywordName);
					println("));");
					continue;
				}

				var defaultValueMethod = getDefaultValue(field).orElse(null);
				if(defaultValueMethod != null) {
					print("var field_");
					print(field.getSimpleName());
					print(" = ");
					print("expr_");
					print(field.getSimpleName());
					print(" == null ? ");

					print(elem.getQualifiedName());
					if(te != elem) {
						print(".");
						print(te.getSimpleName());
					}
					print(".");
					print(defaultValueMethod);
					print("()");


					print(" : ");
					printCodecExpr(field.asType(), field);
					print(".decode(expr_");
					print(field.getSimpleName());print(", path.append(");
					printStringLiteral(getConstructorName(te));
					print(", ");
					printStringLiteral(keywordName);
					println("));");
				}
				else {
					print("if(expr_");
					print(field.getSimpleName());
					print(" == null) { throw new dev.argon.esexpr.DecodeException(\"Missing required keyword argument\", path.withConstructor(");
					printStringLiteral(getConstructorName(te));
					println(")); }");
					print("var field_");
					print(field.getSimpleName());
					print(" = ");
					printCodecExpr(field.asType(), field);
					print(".decode(expr_");
					print(field.getSimpleName());
					print(", path.append(");
					printStringLiteral(getConstructorName(te));
					print(", ");
					printStringLiteral(keywordName);
					println("));");
				}

				continue;
			}

			var varargValueCodec = getVarArg(field).orElse(null);
			if(varargValueCodec != null) {
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				writeFieldCodec(varargValueCodec, field);
				print(".decodeVarArg(args, i -> path.append(");
				printStringLiteral(getConstructorName(te));
				print(", ");
				print(Integer.toString(positionalIndex));
				println(" + i));");

				println("args.clear();");

				++positionalIndex;
				continue;
			}

			var dictValueCodec = getDict(field).orElse(null);
			if(dictValueCodec != null) {
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				writeFieldCodec(dictValueCodec, field);
				print(".decodeDict(kwargs, kw -> path.append(");
				printStringLiteral(getConstructorName(te));
				println(", kw));");

				println("kwargs.clear();");
			}
			else {
				print("if(args.isEmpty()) { throw new dev.argon.esexpr.DecodeException(\"Not enough arguments\", path.withConstructor(");
				printStringLiteral(getConstructorName(te));
				println(")); }");
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field);
				println(".decode(args.removeFirst(), path.append(");
				printStringLiteral(getConstructorName(te));
				print(", ");
				print(Integer.toString(positionalIndex));
				println("));");
			}
		}

		println("if(!args.isEmpty()) { throw new dev.argon.esexpr.DecodeException(\"Extra positional arguments were found.\", path.withConstructor(");
		printStringLiteral(getConstructorName(te));
		println(")); }");
		println("if(!kwargs.isEmpty()) { throw new dev.argon.esexpr.DecodeException(\"Extra keyword arguments were found.\", path.withConstructor(");
		printStringLiteral(getConstructorName(te));
		println(")); }");

		if(useYield) {
			print("yield");
		}
		else {
			print("return");
		}
		print(" new ");
		print(elem.getQualifiedName());
		if(te != elem) {
			print(".");
			print(te.getSimpleName());
		}
		printTypeArguments();
		print("(");

		int i = 0;
		for(var field : getFields(te)) {
			if(i > 0) {
				print(", ");
			}
			++i;

			print("field_");
			print(field.getSimpleName());
		}

		println(");");
	}

	private void writeFieldCodec(TypeMirror codecType, RecordComponentElement field) throws IOException, AbortException {
		DeclaredType fieldType = (DeclaredType)field.asType();

		print("new ");
		print(codecType.toString());
		if(!fieldType.getTypeArguments().isEmpty()) {
			print("<");
			int i = 0;
			for(var arg : fieldType.getTypeArguments()) {
				if(i > 0) {
					print(", ");
				}
				++i;

				print(arg.toString());
			}
			print(">");
		}
		{
			print("(");
			int i = 0;
			for(var arg : fieldType.getTypeArguments()) {
				if(i > 0) {
					print(", ");
				}
				++i;

				printCodecExpr(arg, field);
			}
			print(")");
		}
	}


	protected abstract void writeTagsImpl() throws IOException, AbortException;
	protected abstract void writeEncodeImpl() throws IOException, AbortException;
	protected abstract void writeDecodeImpl() throws IOException, AbortException;



}
