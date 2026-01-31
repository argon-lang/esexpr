package dev.argon.esexpr.generator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.google.common.collect.ImmutableSet;
import dev.argon.esexpr.*;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

import static dev.argon.esexpr.generator.NameUtils.*;

abstract class GeneratorBase extends GeneratorBaseWriter {
	public GeneratorBase(PrintWriter writer, ProcessingEnvironment env, MetadataCache metadataCache, TypeElement elem) {
		super(writer);
		this.env = env;
		this.metadataCache = metadataCache;
		this.elem = elem;
	}

	protected final ProcessingEnvironment env;
	protected final MetadataCache metadataCache;
	protected final TypeElement elem;


	public static Function<PrintWriter, GeneratorBase> forElement(ProcessingEnvironment processingEnv, MetadataCache metadataCache, TypeElement typeElem) throws AbortException {
		switch(typeElem.getKind()) {
			case RECORD -> {
				if(typeElem.getAnnotation(Flags.class) != null) {
					return writer -> new FlagsCodecGenerator(writer, processingEnv, metadataCache, typeElem);
				}
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

	protected void printCodecExpr(TypeMirror t, Element associatedElement) throws IOException, AbortException {
		printCodecExpr(t, associatedElement, CodecOverride.CodecType.VALUE);
	}

	protected void printCodecExpr(TypeMirror t, Element associatedElement, CodecOverride.CodecType codecType) throws IOException, AbortException {
		var codecOverride = metadataCache.findOverrideCodec(t, associatedElement, codecType);

		List<? extends TypeMirror> typeArguments = t instanceof DeclaredType dt ? dt.getTypeArguments() : List.of();

		if(codecOverride != null) {
			switch(codecOverride) {
				case TypeElement typeElement -> {
					print("new ");
					print(typeElement.getQualifiedName());
					if(!typeArguments.isEmpty()) {
						print("<>");
					}
					print("(");
					int i = 0;
					for(var arg : typeArguments) {
						if(i > 0) {
							print(", ");
						}
						++i;

						printCodecExpr(arg, associatedElement);
					}
					print(")");
				}

				case VariableElement variableElement when variableElement.getEnclosingElement() instanceof TypeElement owningType -> {
					print(owningType.getQualifiedName());
					print(".");
					print(variableElement.getSimpleName());
				}

				case ExecutableElement executableElement when executableElement.getEnclosingElement() instanceof TypeElement owningType -> {
					print(owningType.getQualifiedName());
					print(".");
					print(executableElement.getSimpleName());
					print("(");
					int i = 0;
					for(var arg : typeArguments) {
						if(i > 0) {
							print(", ");
						}
						++i;

						printCodecExpr(arg, associatedElement);
					}
					print(")");
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



	private @Nullable String getPackage() {
		String name = elem.getQualifiedName().toString();
		int lastDot = name.lastIndexOf('.');
		if(lastDot < 0) {
			return null;
		}

		return name.substring(0, lastDot);
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
		println("@java.lang.SuppressWarnings(\"UnnecessaryParentheses\")");
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
		print("public boolean isEncodedEqual(");
		print(elem.getQualifiedName());
		printTypeArguments();
		print(" x, ");
		print(elem.getQualifiedName());
		printTypeArguments();
		println(" y) {");
		indent();
		writeEncodedEqualImpl();
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


	protected void writeEncodeFields(TypeElement te, String valueVarName, boolean useYield) throws IOException, AbortException {
		boolean hasDict = false;
		var kwargNames = new HashSet<String>();
		var prevOptionalPositionalTags = ESExprTagSet.of();

		println("var args = com.google.common.collect.ImmutableList.<dev.argon.esexpr.ESExpr>builder();");
		println("var kwargs = com.google.common.collect.ImmutableMap.<java.lang.String, dev.argon.esexpr.ESExpr>builder();");

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
					print("if(!");
					printCodecExpr(field.asType(), field, CodecOverride.CodecType.VALUE);
					print(".isEncodedEqual(");

					print(valueVarName);
					print(".");
					print(field.getSimpleName());
					print("(), (");

					print(defaultValue);

					print("))) ");
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

				println();
				continue;
			}

			if(isVararg(field)) {
				var elementType = metadataCache.findCodecElementType(field.asType(), field, CodecOverride.CodecType.VARARG);

				var fieldTags = metadataCache.lookupTags(elementType, field, env);
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

				printCodecExpr(field.asType(), field, CodecOverride.CodecType.DICT);
				print(".encodeDict(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("(), kwargs);");
				continue;
			}

			if(isOptional(field)) {
				var elementType = metadataCache.findCodecElementType(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);

				var fieldTags = metadataCache.lookupTags(elementType, field, env);
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
					var fieldTags = metadataCache.lookupTags(field.asType(), field, env);
					posTagCheck(field, prevOptionalPositionalTags, fieldTags);
				}
				prevOptionalPositionalTags = ESExprTagSet.of();

				var defaultValue = getDefaultValue(field).orElse(null);
				if(defaultValue != null) {
					print("if(!");
					printCodecExpr(field.asType(), field);
					print(".isEncodedEqual(");
					print(valueVarName);
					print(".");
					print(field.getSimpleName());
					print("(), ");
					print(defaultValue);
					print(")) ");
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
		println(", args.build(), kwargs.build());");
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

	protected void writeEncodedEqualFields(TypeElement te, String xName, String yName, boolean useYield) throws IOException, AbortException {
		for(var field : getFields(te)) {
			print("if(!");

			CodecOverride.CodecType codecType;

			if(isVararg(field)) {
				codecType = CodecOverride.CodecType.VARARG;
			}
			else if(isDict(field)) {
				codecType = CodecOverride.CodecType.DICT;
			}
			else if(isOptional(field)) {
				codecType = CodecOverride.CodecType.OPTIONAL_VALUE;
			}
			else {
				codecType = CodecOverride.CodecType.VALUE;
			}

			printCodecExpr(field.asType(), field, codecType);

			print(".isEncodedEqual(");
			print(xName);
			print(".");
			print(field.getSimpleName());
			print("(), ");
			print(yName);
			print(".");
			print(field.getSimpleName());
			println("())) {");
			indent();
			if(useYield) {
				print("yield");
			}
			else {
				print("return");
			}
			println(" false;");
			dedent();
			println("}");
		}

		if(useYield) {
			print("yield");
		}
		else {
			print("return");
		}
		println(" true;");
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
					print(") : ");
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


			if(isOptional(field)) {
				print("var path_");
				print(field.getSimpleName());
				print(" = path.append(");
				printStringLiteral(getConstructorName(te));
				println(", args0.size() - args.size());");

				var elementType = metadataCache.findCodecElementType(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);

				print("var fieldExpr_");
				print(field.getSimpleName());
				println(" = args.peekFirst();");


				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field, CodecOverride.CodecType.OPTIONAL_VALUE);
				print(".decodeOptional((fieldExpr_");
				print(field.getSimpleName());
				print(" != null && ");
				printCodecExpr(elementType, field, CodecOverride.CodecType.VALUE);
				print(".tags().contains(fieldExpr_");
				print(field.getSimpleName());
				print(".tag())) ? java.util.Optional.of(args.removeFirst()) : java.util.Optional.empty(), path_");
				print(field.getSimpleName());
				println(");");
			}
			else {
				var defaultValue = getDefaultValue(field).orElse(null);
				if(defaultValue != null) {
					print("var fieldExpr_");
					print(field.getSimpleName());
					println(" = args.peekFirst();");


					print("var field_");
					print(field.getSimpleName());
					print(" = (fieldExpr_");
					print(field.getSimpleName());
					print(" != null && ");
					printCodecExpr(field.asType(), field, CodecOverride.CodecType.VALUE);
					print(".tags().contains(fieldExpr_");
					print(field.getSimpleName());
					print(".tag())) ? ");
					printCodecExpr(field.asType(), field, CodecOverride.CodecType.VALUE);
					print(".decode(args.removeFirst(), path.append(");
					printStringLiteral(getConstructorName(te));
					println(", args0.size() - args.size())) : (");
					print(defaultValue);
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
		switch(metadataCache.lookupTags(elem.asType(), elem, env)) {
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
	protected abstract void writeEncodedEqualImpl() throws IOException, AbortException;
	protected abstract void writeEncodeImpl() throws IOException, AbortException;
	protected abstract void writeDecodeImpl() throws IOException, AbortException;



}
