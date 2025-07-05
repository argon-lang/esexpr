package dev.argon.esexpr.generator;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;

import com.google.common.collect.ImmutableSet;
import dev.argon.esexpr.*;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

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


	public static Function<PrintWriter, GeneratorBase> forElement(ProcessingEnvironment processingEnv, MetadataCache metadataCache, TypeElement typeElem) throws AbortException {
		switch(typeElem.getKind()) {
			case RECORD -> {
				return writer -> new RecordCodecGenerator(writer, processingEnv, metadataCache, typeElem);
			}
			case INTERFACE -> {
				if(typeElem.getModifiers().contains(Modifier.SEALED)) {
					return writer -> new EnumCodecGenerator(writer, processingEnv, metadataCache, typeElem);
				}
			}
			case ENUM -> {
				return writer -> new SimpleEnumCodecGenerator(writer, processingEnv, metadataCache, typeElem);
			}
			default -> {}
		}

		throw new AbortException("ESExprCodeGen must be used with a record, sealed interface (of records), or an enum.");
	}


	public final void generate() throws IOException, AbortException {
		validateAnnotations();
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

	private TypeMirror findCodecElementType(
		TypeMirror t,
		Element associatedElement,
		CodecOverride.CodecType codecType
	) throws AbortException {
		var codecOverride = findOverrideCodec(t, associatedElement, codecType);
		if(codecOverride == null) {
			throw new AbortException("Could not find " + codecType + " for " + t, associatedElement);
		}

		List<? extends TypeMirror> typeArguments = t instanceof DeclaredType dt
			? dt.getTypeArguments()
			: List.of();

		var t2 = switch(codecOverride) {
			case TypeElement te ->
				env.getTypeUtils().getDeclaredType(te, typeArguments.toArray(TypeMirror[]::new));

			case VariableElement ve ->
				ve.asType();

			case ExecutableElement ee ->
				substitute(ee.getReturnType(), ee.getTypeParameters(), typeArguments);

			default -> throw new AbortException("Unexpected override type", associatedElement);
		};

		var elemType = findElementType(t2, codecType);
		if(elemType == null) {
			throw new AbortException("Could not find element type for " + codecType.codecClass() + " type " + t, associatedElement);
		}

		return elemType;
	}

	private @Nullable TypeMirror findElementType(
		TypeMirror t,
		CodecOverride.CodecType codecType
	) {
		if(!(t instanceof DeclaredType dt)) {
			return null;
		}

		var te = (TypeElement)dt.asElement();

		var typeParams = te.getTypeParameters();
		var typeArgs = dt.getTypeArguments();
		
		if(te.getQualifiedName().toString().equals(codecType.codecClass())) {
			return substitute(dt.getTypeArguments().get(1), typeParams, typeArgs);
		}
		else {
			var superClass = te.getSuperclass();
			if(superClass.getKind() != TypeKind.NONE) {
				var res = findElementType(substitute(superClass, typeParams, typeArgs), codecType);
				if(res != null) {
					return res;
				}
			}

			for(var iface : te.getInterfaces()) {
				var res = findElementType(substitute(iface, typeParams, typeArgs), codecType);
				if(res != null) {
					return res;
				}
			}

			return null;
		}
	}
	
	private TypeMirror substitute(TypeMirror t, List<? extends TypeParameterElement> typeParams, List<? extends TypeMirror> typeArgs) {
		return switch(t) {
			case TypeVariable tv -> {
				var tpe = (TypeParameterElement)tv.asElement();
				for (int i = 0; i < typeParams.size(); i++) {
					if (typeParams.get(i).equals(tpe)) {
						yield typeArgs.get(i);
					}
				}
				yield t;
			}

			case DeclaredType dt -> {
				var args = dt.getTypeArguments().stream()
					.map(arg -> substitute(arg, typeParams, typeArgs))
					.toList();

				yield env.getTypeUtils().getDeclaredType(
					(TypeElement) dt.asElement(),
					args.toArray(TypeMirror[]::new)
				);
			}

			case ArrayType at ->
				env.getTypeUtils().getArrayType(
					substitute(at.getComponentType(), typeParams, typeArgs)
				);

			default -> t;
		};
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
		var ctor = elem.getAnnotation(Constructor.class);
		if(ctor != null) {
			return ctor.value();
		}

		return nameToKebabCase(elem.getSimpleName().toString());
	}

	protected String getConstructorNameSimpleEnum(VariableElement elem) {
		var ctor = elem.getAnnotation(Constructor.class);
		if(ctor != null) {
			return ctor.value();
		}

		return enumConstNameToKebabCase(elem.getSimpleName().toString());
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
		println("public dev.argon.esexpr.ESExprTagSet tags() {");
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

	private Optional<Keyword> getKeywordAnn(RecordComponentElement rce) {
		return Optional.ofNullable(rce.getAnnotation(Keyword.class));
	}

	private boolean isOptional(RecordComponentElement field) {
		return field.getAnnotation(OptionalValue.class) != null;
	}

	private Optional<String> getDefaultValue(RecordComponentElement field) {
		var defaultValue = field.getAnnotation(DefaultValue.class);
		if(defaultValue == null) {
			return Optional.empty();
		}

		return Optional.of(defaultValue.value());
	}

	private boolean isVararg(RecordComponentElement field) {
		return field.getAnnotation(Vararg.class) != null;
	}

	private boolean isDict(RecordComponentElement field) {
		return field.getAnnotation(Dict.class) != null;
	}

	private String getKeywordName(RecordComponentElement rce, Keyword keyword) {
		if(keyword.value().isEmpty()) {
			return nameToKebabCase(rce.getSimpleName().toString());
		}
		else {
			return keyword.value();
		}
	}

	protected void writeEncodeFields(TypeElement te, String valueVarName, boolean useYield) throws IOException, AbortException {
		boolean hasDict = false;
		var kwargNames = new HashSet<String>();
		var prevOptionalPositionalTags = ESExprTagSet.of();

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
					throw new AbortException("Keyword arguments cannot be used with dict arguments", field);
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
				var elementType = findCodecElementType(field.asType(), field, CodecOverride.CodecType.VARARG);

				var fieldTags = lookupTags(elementType, field);
				posTagCheck(field, prevOptionalPositionalTags, fieldTags);
				prevOptionalPositionalTags = prevOptionalPositionalTags.union(fieldTags);

				printCodecExpr(field.asType(), field, CodecOverride.CodecType.VARARG);
				print(".encodeVararg(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("(), args);");
				continue;
			}

			if(isDict(field)) {
				if(hasDict) {
					throw new AbortException("Only a single dict argument is allowed", field);
				}
				hasDict = true;

				if(!kwargNames.isEmpty()) {
					throw new AbortException("Keyword arguments cannot be used with dict arguments", field);
				}

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

			if(isOptional(field)) {
				var elementType = findCodecElementType(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);

				var fieldTags = lookupTags(elementType, field);
				posTagCheck(field, prevOptionalPositionalTags, fieldTags);
				prevOptionalPositionalTags = prevOptionalPositionalTags.union(fieldTags);

				printCodecExpr(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);
				print(".encodeOptional(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("()).ifPresent(arg -> args.add(arg));");
			}
			else {
				if(!prevOptionalPositionalTags.isEmpty()) {
					var fieldTags = lookupTags(field.asType(), field);
					posTagCheck(field, prevOptionalPositionalTags, fieldTags);
				}
				prevOptionalPositionalTags = ESExprTagSet.of();

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

	private void posTagCheck(RecordComponentElement rce, ESExprTagSet prevTags, ESExprTagSet fieldTags) throws AbortException {
		if(prevTags.isEmpty()) {
			return;
		}

		if(fieldTags.isAll()) {
			throw new AbortException("Field '" + rce.getSimpleName() + "' cannot follow optional positional arguments with all tags", rce);
		}

		if(!prevTags.isDisjoint(fieldTags)) {
			throw new AbortException("Field '" + rce.getSimpleName() + "' must have distinct tags from immediately preceding optional positional arguments", rce);
		}
	}

	protected void writeDecodeFields(TypeElement te, boolean useYield) throws IOException, AbortException {
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
				print(".decodeVararg(args, path.appenderWithOffset(");
				printStringLiteral(getConstructorName(te));
				print(", args0.size() - args.size()));");
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

			print("var path_");
			print(field.getSimpleName());
			print(" = path.append(");
			printStringLiteral(getConstructorName(te));
			println(", args0.size() - args.size());");

			if(isOptional(field)) {
				var elementType = findCodecElementType(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);

				print("var fieldExpr_");
				print(field.getSimpleName());
				println(" = args.peekFirst();");


				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);
				print(".decodeOptional((fieldExpr_");
				print(field.getSimpleName());
				print("!= null && ");
				printCodecExpr(elementType, field, CodecOverride.CodecType.VALUE);
				print(".tags().contains(fieldExpr_");
				print(field.getSimpleName());
				print(".tag())) ? java.util.Optional.of(args.removeFirst()) : java.util.Optional.empty(), path_");
				print(field.getSimpleName());
				println(");");
			}
			else {
				print("if(args.isEmpty()) { throw new dev.argon.esexpr.DecodeException(\"Not enough arguments\", path.withConstructor(");
				printStringLiteral(getConstructorName(te));
				println(")); }");
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field, CodecOverride.CodecType.VALUE);
				print(".decode(args.removeFirst(), path.append(");
				printStringLiteral(getConstructorName(te));
				println(", args0.size() - args.size()));");
			}
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


	private void writeTagsImpl() throws IOException, AbortException {
		switch(lookupTags(elem.asType(), elem)) {
			case ESExprTagSet.All() -> {
				println("return new dev.argon.esexpr.ESExprTagSet.All();");
			}
			case ESExprTagSet.Tags(var tags) -> {
				print("return dev.argon.esexpr.ESExprTagSet.of(");

				boolean isFirst = true;
				for(var tag : tags) {
					if(!isFirst) {
						print(", ");
					}
					isFirst = false;

					switch(tag) {
						case ESExprTag.Constructor(var name) -> {
							print("new dev.argon.esexpr.ESExprTag.Constructor(");
							printStringLiteral(name);
							print(")");
						}
						case ESExprTag.Scalar scalar -> {
							print("dev.argon.esexpr.ESExprTag.");
							print(scalar.name());
						}
					}
				}

				println(");");
			}
		}

	}

	protected abstract void validateAnnotations() throws AbortException;
	protected abstract ESExprTagSet getTags(Element associatedElement) throws AbortException;
	protected abstract void writeEncodeImpl() throws IOException, AbortException;
	protected abstract void writeDecodeImpl() throws IOException, AbortException;


	protected ESExprTagSet lookupTags(TypeMirror t, Element associatedElement) throws AbortException {
		var overrideCodec = findOverrideCodec(t, associatedElement, CodecOverride.CodecType.VALUE);
		if(overrideCodec != null) {
			return tagsFromAnnotation(t, overrideCodec, associatedElement);
		}
		
		if(t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te && te.getAnnotation(ESExprCodecGen.class) != null) {
			return GeneratorBase.forElement(env, metadataCache, te).apply(writer).getTags(associatedElement);
		}

		throw new AbortException("Could not determine tags of type " + t, associatedElement);
	}


	private ESExprTagSet tagsFromAnnotation(TypeMirror t, Element codecElement, Element associatedElement) throws AbortException {
		var tags = codecElement.getAnnotation(ESExprCodecTags.class);
		if(tags == null) {
			throw new AbortException("ESExprOverrideCodec must be annotated with ESExprCodecTags", associatedElement);
		}


		Map<String, TypeMirror> typeParamMap = new HashMap<>();
		if(t instanceof DeclaredType dt) {
			var typeElement = (TypeElement) dt.asElement();
			var typeArgs = dt.getTypeArguments();
			for(int i = 0; i < typeElement.getTypeParameters().size(); i++) {
				typeParamMap.put(typeElement.getTypeParameters().get(i).getSimpleName().toString(), typeArgs.get(i));
			}
		}

		if(tags.all()) {
			return new ESExprTagSet.All();
		}

		var ts = ImmutableSet.<ESExprTag>builder();

		for(var tp : tags.unionWithTypeParameters()) {
			var tpt = typeParamMap.get(tp);
			if(tpt == null) {
				throw new AbortException("Could not determine tags for type parameter " + tp);
			}

			switch(lookupTags(tpt, associatedElement)) {
				case ESExprTagSet.Tags(var tpTags) -> ts.addAll(tpTags);
				case ESExprTagSet.All all -> { return all; }
			}
		}

		for(var ctorName : tags.constructors()) {
			ts.add(new ESExprTag.Constructor(ctorName));
		}

		ts.addAll(Arrays.asList(tags.scalar()));

		return new ESExprTagSet.Tags(ts.build());
	}

}
