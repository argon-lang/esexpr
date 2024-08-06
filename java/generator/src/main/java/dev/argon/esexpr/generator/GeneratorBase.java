package dev.argon.esexpr.generator;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;

import org.apache.commons.text.StringEscapeUtils;

abstract class GeneratorBase {
	public GeneratorBase(PrintWriter writer, ProcessingEnvironment env, MetadataCache metadataCache, TypeElement elem) {
		this.writer = writer;
		this.env = env;
		this.metadataCache = metadataCache;
		this.elem = elem;
	}

	private final PrintWriter writer;
	protected final ProcessingEnvironment env;
	private final MetadataCache metadataCache;
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
		printCodecExpr(t, associatedElement, CodecOverride.CodecType.VALUE);
	}

	protected void printCodecExpr(TypeMirror t, Element associatedElement, CodecOverride.CodecType codecType) throws IOException, AbortException {
		var codecOverride = findOverrideCodec(t, associatedElement, codecType);

		List<? extends TypeMirror> typeArguments = t instanceof DeclaredType dt ? dt.getTypeArguments() : List.of();

		if(codecOverride != null) {
			switch(codecOverride) {
				case TypeElement typeElement -> {
					writer.print("new ");
					writer.print(typeElement.getQualifiedName());
					if(!typeArguments.isEmpty()) {
						writer.print("<>");
					}
					writer.print("(");
					int i = 0;
					for(var arg : typeArguments) {
						if(i > 0) {
							print(", ");
						}
						++i;

						printCodecExpr(arg, associatedElement);
					}
					writer.print(")");
				}

				case VariableElement variableElement when variableElement.getEnclosingElement() instanceof TypeElement owningType -> {
					writer.print(owningType.getQualifiedName());
					writer.print(".");
					writer.print(variableElement.getSimpleName());
				}

				case ExecutableElement executableElement when executableElement.getEnclosingElement() instanceof TypeElement owningType -> {
					writer.print(owningType.getQualifiedName());
					writer.print(".");
					writer.print(executableElement.getSimpleName());
					writer.print("(");
					int i = 0;
					for(var arg : typeArguments) {
						if(i > 0) {
							print(", ");
						}
						++i;

						printCodecExpr(arg, associatedElement);
					}
					writer.print(")");
				}

				default -> throw new AbortException("Unexpected override type", associatedElement);
			}
		}
		else if(t.getKind() == TypeKind.TYPEVAR) {
			print(nameToCamelCase(((TypeVariable)t).asElement().getSimpleName().toString()));
			print("Codec");
		}
		else if(t instanceof DeclaredType declType) {
			print(((TypeElement)declType.asElement()).getQualifiedName());
			print(".");
			print(switch(codecType) {
				case VALUE -> "codec";
				case OPTIONAL_VALUE -> "optionalValueCodec";
				case VARARG -> "varargCodec";
				case DICT -> "dictCodec";
			});
			print("(");

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
		else {
			throw new AbortException("Unexpected type for codec: " + t, associatedElement);
		}
	}

	private Element findOverrideCodec(TypeMirror t, Element associatedElement, CodecOverride.CodecType codecType) throws AbortException {
		for(var codecOverride : metadataCache.getCodecOverrides()) {
			if(codecOverride.codecType() != codecType) {
				continue;
			}

			if(!codecOverrideTypeMatches(t, codecOverride.t(), associatedElement)) {
				continue;
			}

			if(
				!codecOverride.requiredAnnotations().isEmpty() &&
					codecOverride.requiredAnnotations().stream()
					.noneMatch(annType -> hasAnnotationByType(t.getAnnotationMirrors(), annType))
			) {
				continue;
			}

			if(
				codecOverride.excludedAnnotations().stream()
					.anyMatch(annType -> hasAnnotationByType(t.getAnnotationMirrors(), annType))
			) {
				continue;
			}

			return codecOverride.overridingElement();
		}

		return null;
	}

	private boolean codecOverrideTypeMatches(TypeMirror typeForCodec, TypeMirror overrideType, Element associatedElement) throws AbortException {
		if(typeForCodec.getKind() != overrideType.getKind()) {
			return false;
		}

		if(typeForCodec.getKind().isPrimitive()) {
			return true;
		}

		if(typeForCodec.getKind() == TypeKind.ARRAY) {
			return codecOverrideTypeMatches(((ArrayType)typeForCodec).getComponentType(), ((ArrayType)overrideType).getComponentType(), associatedElement);
		}

		if(typeForCodec.getKind() == TypeKind.DECLARED) {
			return ((TypeElement)((DeclaredType)typeForCodec).asElement()).getQualifiedName().toString()
				.equals(((TypeElement)((DeclaredType)overrideType).asElement()).getQualifiedName().toString());
		}


		throw new AbortException("Unexpected type for codec: " + typeForCodec.toString(), associatedElement);
	}

	static boolean hasAnnotation(List<? extends AnnotationMirror> annotations, String name) {
		return getAnnotation(annotations, name).isPresent();
	}

	static boolean hasAnnotationByType(List<? extends AnnotationMirror> annotations, TypeMirror t) {
		if(t.getKind() != TypeKind.DECLARED) {
			return false;
		}

		String name = ((TypeElement)((DeclaredType)t).asElement()).getQualifiedName().toString();

		return hasAnnotation(annotations, name);
	}

	static Optional<? extends AnnotationMirror> getAnnotation(List<? extends AnnotationMirror> annotations, String name) {
		return annotations.stream()
			.filter(ann -> ((TypeElement)ann.getAnnotationType().asElement()).getQualifiedName().toString().equals(name))
			.findFirst();
	}

	static Optional<AnnotationValue> getAnnotationArgument(AnnotationMirror ann, String name) {
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
		print("class ");
		print(elem.getSimpleName());
		print("_CodecImpl");
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
			println("_CodecImpl();");
		}
		else {
			println("public ");
			print(elem.getSimpleName());
			print("_CodecImpl(");

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

	private boolean isOptional(RecordComponentElement field) {
		return hasAnnotation(field.getAnnotationMirrors(), "dev.argon.esexpr.OptionalValue");
	}

	private Optional<String> getDefaultValue(RecordComponentElement field) {
		return getAnnotation(field.getAnnotationMirrors(), "dev.argon.esexpr.DefaultValue")
			.flatMap(ann -> getAnnotationArgument(ann, "value"))
			.flatMap(value -> value.getValue() instanceof String s ? Optional.of(s) : Optional.empty());
	}

	private boolean isVararg(RecordComponentElement field) {
		return hasAnnotation(field.getAnnotationMirrors(), "dev.argon.esexpr.Vararg");
	}

	private boolean isDict(RecordComponentElement field) {
		return hasAnnotation(field.getAnnotationMirrors(), "dev.argon.esexpr.Dict");
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
		boolean hasOptionalPositional = false;
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

				if(isOptional(field)) {
					println("{");
					indent();

					print("var kwValue = ");
					printCodecExpr(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);
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



				var defaultValue = getDefaultValue(field).orElse(null);
				if(defaultValue != null) {
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

					if(isPrimitiveField) {
						print("(");
					}
					print(defaultValue);
					if(isPrimitiveField) {
						print(")");
					}

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

				if(defaultValue != null) {
					print(" }");
				}

				println();
				continue;
			}

			if(isVararg(field)) {
				if(hasVarArgs) {
					throw new AbortException("Only a single vararg is allowed", field);
				}
				hasVarArgs = true;

				print("for(var arg : ");
				printCodecExpr(field.asType(), field, CodecOverride.CodecType.VARARG);
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

			if(isDict(field)) {
				if(hasDict) {
					throw new AbortException("Only a single dict argument is allowed", field);
				}
				hasDict = true;

				print("for(var pair : ");
				printCodecExpr(field.asType(), field, CodecOverride.CodecType.DICT);
				print(".encodeDict(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("()).entrySet()) {");
				indent();

				println("kwargs.put(pair.getKey(), pair.getValue());");

				dedent();
				println("}");
				continue;
			}

			if(hasVarArgs) {
				throw new AbortException("Positional arguments must precede varargs", field);
			}

			if(isOptional(field)) {
				if(hasOptionalPositional) {
					throw new AbortException("Only a single optional positional argument is allowed", field);
				}

				hasOptionalPositional = true;

				printCodecExpr(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);
				print(".encodeOptional(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("()).ifPresent(arg -> args.add(arg));");
			}
			else {
				if(hasOptionalPositional) {
					throw new AbortException("Required positional arguments must precede optional positional arguments", field);
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

				if(isOptional(field)) {
					print(field.asType().toString());
					print(" field_");
					print(field.getSimpleName());
					print(" = ");
					printCodecExpr(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);
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

				var defaultValue = getDefaultValue(field).orElse(null);
				if(defaultValue != null) {
					print("var field_");
					print(field.getSimpleName());
					print(" = ");
					print("expr_");
					print(field.getSimpleName());
					print(" == null ? ");

					print("(");
					print(defaultValue);
					print(")");


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

			if(isVararg(field)) {
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field, CodecOverride.CodecType.VARARG);
				print(".decodeVarArg(args, i -> path.append(");
				printStringLiteral(getConstructorName(te));
				print(", ");
				print(Integer.toString(positionalIndex));
				println(" + i));");

				println("args.clear();");

				++positionalIndex;
				continue;
			}

			if(isDict(field)) {
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field, CodecOverride.CodecType.DICT);
				print(".decodeDict(kwargs, kw -> path.append(");
				printStringLiteral(getConstructorName(te));
				println(", kw));");

				println("kwargs.clear();");
				continue;
			}

			if(isOptional(field)) {
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);
				print(".decodeOptional(args.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(args.removeFirst()), path.append(");
				printStringLiteral(getConstructorName(te));
				print(", ");
				print(Integer.toString(positionalIndex));
				println("));");
			}
			else {
				print("if(args.isEmpty()) { throw new dev.argon.esexpr.DecodeException(\"Not enough arguments\", path.withConstructor(");
				printStringLiteral(getConstructorName(te));
				println(")); }");
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field);
				print(".decode(args.removeFirst(), path.append(");
				printStringLiteral(getConstructorName(te));
				print(", ");
				print(Integer.toString(positionalIndex));
				println("));");
			}
			++positionalIndex;
		}

		print("if(!args.isEmpty()) { throw new dev.argon.esexpr.DecodeException(\"Extra positional arguments were found.\", path.withConstructor(");
		printStringLiteral(getConstructorName(te));
		println(")); }");
		print("if(!kwargs.isEmpty()) { throw new dev.argon.esexpr.DecodeException(\"Extra keyword arguments were found.\", path.withConstructor(");
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


	protected abstract void writeTagsImpl() throws IOException, AbortException;
	protected abstract void writeEncodeImpl() throws IOException, AbortException;
	protected abstract void writeDecodeImpl() throws IOException, AbortException;



}
