package dev.argon.esexpr.generator.codegen;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;

import dev.argon.esexpr.*;
import dev.argon.esexpr.generator.lookup.CodecType;
import dev.argon.esexpr.generator.lookup.Lookup;
import dev.argon.esexpr.generator.lookup.MetadataCache;
import dev.argon.esexpr.generator.typeclass.TypeClassLocalScope;
import dev.argon.esexpr.generator.typeclass.TypeClassResult;
import org.jspecify.annotations.Nullable;

import static dev.argon.esexpr.generator.utils.NameUtils.*;

public abstract class GeneratorBase extends GeneratorBaseWriter {
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
		printCodecExpr(t, associatedElement, CodecType.VALUE);
	}

	protected void printCodecExpr(TypeMirror t, Element associatedElement, CodecType codecType) throws IOException, AbortException {
		var typeClassResult = new Lookup(env, metadataCache, codecLocalScope(), associatedElement).getCodec(t, codecType);
		if(typeClassResult == null) {
			throw new AbortException("Could not find codec (" + codecType.codecClass() + ") for type " + t, associatedElement);
		}
		printTypeClassExpr(typeClassResult);
	}

	private void printTypeClassExpr(TypeClassResult typeClassResult) throws IOException {
		switch(typeClassResult) {
			case TypeClassResult.Field(var field) -> {
				var owningType = (TypeElement)field.getEnclosingElement();
				print(owningType.getQualifiedName());
				print(".");
				print(field.getSimpleName());
			}
			case TypeClassResult.Local(var local) -> {
				print("this.instance_");
				print(local.getSimpleName());
			}
			case TypeClassResult.Method(var method, var arguments) -> {
				var owningType = (TypeElement)method.getEnclosingElement();
				print(owningType.getQualifiedName());
				print(".");
				print(method.getSimpleName());
				print("(");

				boolean needsComma = false;
				for(var arg : arguments) {
					if(needsComma) {
						print(", ");
					}
					else {
						needsComma = true;
					}

					printTypeClassExpr(arg);
				}

				print(")");
			}
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

		var typeParams = codecTypeParameters();
		if(!typeParams.isEmpty()) {
			print("<");
			int i = 0;
			for(TypeParameterElement tp : typeParams) {
				if(i > 0) {
					print(", ");
				}
				++i;

				print("T_");
				print(tp.getSimpleName());
			}
			print(">");
		}
		print(" implements dev.argon.esexpr.ESExprCodec<");
		print(elem.getQualifiedName());
		printTypeArguments();
		println("> {");
		indent();

		var instanceParams = codecInstanceParameters();
		if(instanceParams.isEmpty()) {
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
			for(var param : instanceParams) {
				if(i > 0) {
					print(", ");
				}
				++i;

				printInstanceParamType(param.asType());
				print(" instance_");
				print(param.getSimpleName());
			}

			println(") {");
			indent();
			

			for(var param : instanceParams) {
				print("this.instance_");
				print(param.getSimpleName());
				print(" = instance_");
				print(param.getSimpleName());
				println(";");
			}

			dedent();
			println("}");

			for(var param : instanceParams) {
				print("private final ");
				printInstanceParamType(param.asType());
				print(" instance_");
				print(param.getSimpleName());
				println(";");
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

				print("T_");
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
					printCodecExpr(field.asType(), field, CodecType.OPTIONAL_VALUE);
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
					printCodecExpr(field.asType(), field, CodecType.VALUE);
					print(".isEncodedEqual(");

					printBoxUnsignedPrimitive(field.asType(), () -> {
						print(valueVarName);
						print(".");
						print(field.getSimpleName());
						print("()");
					});
					print(", ");

					printBoxUnsignedPrimitive(field.asType(), () -> {
						print("(");

						print(defaultValue);

						print(")");

					});
					print(")) ");
				}


				print("kwargs.put(");
				printStringLiteral(kwName);
				print(", ");
				printCodecExpr(field.asType(), field);
				print(".encode(");
				printBoxUnsignedPrimitive(field.asType(), () -> {
					print(valueVarName);
					print(".");
					print(field.getSimpleName());
					print("()");
				});
				print("));");

				println();
				continue;
			}

			var lookup = new Lookup(env, metadataCache, codecLocalScope(), field);


			if(isVararg(field)) {
				var elementType = lookup.findCodecElementType(field.asType(), CodecType.VARARG);

				var fieldTags = lookup.lookupTags(elementType);
				posTagCheck(field, prevOptionalPositionalTags, fieldTags);
				prevOptionalPositionalTags = prevOptionalPositionalTags.union(fieldTags);

				printCodecExpr(field.asType(), field, CodecType.VARARG);
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

				printCodecExpr(field.asType(), field, CodecType.DICT);
				print(".encodeDict(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("(), kwargs);");
				continue;
			}

			if(isOptional(field)) {
				var elementType = lookup.findCodecElementType(field.asType(), CodecType.OPTIONAL_VALUE);

				var fieldTags = lookup.lookupTags(elementType);
				posTagCheck(field, prevOptionalPositionalTags, fieldTags);
				prevOptionalPositionalTags = prevOptionalPositionalTags.union(fieldTags);

				printCodecExpr(field.asType(), field, CodecType.OPTIONAL_VALUE);
				print(".encodeOptional(");
				print(valueVarName);
				print(".");
				print(field.getSimpleName());
				println("()).ifPresent(arg -> args.add(arg));");
			}
			else {

				var defaultValue = getDefaultValue(field).orElse(null);
				if(defaultValue != null) {
					var fieldTags = lookup.lookupTags(field.asType());
					posTagCheck(field, prevOptionalPositionalTags, fieldTags);
					prevOptionalPositionalTags = prevOptionalPositionalTags.union(fieldTags);


					print("if(!");
					printCodecExpr(field.asType(), field);
					print(".isEncodedEqual(");
					printBoxUnsignedPrimitive(field.asType(), () -> {
						print(valueVarName);
						print(".");
						print(field.getSimpleName());
						print("()");
					});
					print(", ");

					printBoxUnsignedPrimitive(field.asType(), () -> {
						print("(");
						print(defaultValue);
						print(")");
					});
					print(")) ");
				}
				else {
					if(!prevOptionalPositionalTags.isEmpty()) {
						var fieldTags = lookup.lookupTags(field.asType());
						posTagCheck(field, prevOptionalPositionalTags, fieldTags);
					}
					prevOptionalPositionalTags = ESExprTagSet.of();
				}

				print("args.add(");
				printCodecExpr(field.asType(), field);
				print(".encode(");
				printBoxUnsignedPrimitive(field.asType(), () -> {
					print(valueVarName);
					print(".");
					print(field.getSimpleName());
					print("()");
				});
				println("));");
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

			CodecType codecType;

			if(isVararg(field)) {
				codecType = CodecType.VARARG;
			}
			else if(isDict(field)) {
				codecType = CodecType.DICT;
			}
			else if(isOptional(field)) {
				codecType = CodecType.OPTIONAL_VALUE;
			}
			else {
				codecType = CodecType.VALUE;
			}

			printCodecExpr(field.asType(), field, codecType);

			print(".isEncodedEqual(");
			printBoxUnsignedPrimitive(field.asType(), () -> {
				print(xName);
				print(".");
				print(field.getSimpleName());
				print("()");
			});
			print(", ");
			printBoxUnsignedPrimitive(field.asType(), () -> {
				print(yName);
				print(".");
				print(field.getSimpleName());
				print("()");
			});
			println(")) {");
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
					printCodecExpr(field.asType(), field, CodecType.OPTIONAL_VALUE);
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
					print("))");
					printUnboxUnsignedPrimitive(field.asType());
					println(";");
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
					print("))");
					printUnboxUnsignedPrimitive(field.asType());
					println(";");
				}

				continue;
			}

			if(isVararg(field)) {
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field, CodecType.VARARG);
				print(".decodeVararg(args, path.appenderWithOffset(");
				printStringLiteral(getConstructorName(te));
				print(", args0.size() - args.size()));");
				continue;
			}

			if(isDict(field)) {
				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field, CodecType.DICT);
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

				var lookup = new Lookup(env, metadataCache, codecLocalScope(), field);
				var elementType = lookup.findCodecElementType(field.asType(), CodecType.OPTIONAL_VALUE);

				print("var fieldExpr_");
				print(field.getSimpleName());
				println(" = args.peekFirst();");


				print("var field_");
				print(field.getSimpleName());
				print(" = ");
				printCodecExpr(field.asType(), field, CodecType.OPTIONAL_VALUE);
				print(".decodeOptional((fieldExpr_");
				print(field.getSimpleName());
				print(" != null && ");
				printCodecExpr(elementType, field, CodecType.VALUE);
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
					printCodecExpr(field.asType(), field, CodecType.VALUE);
					print(".tags().contains(fieldExpr_");
					print(field.getSimpleName());
					print(".tag())) ? ");
					printCodecExpr(field.asType(), field, CodecType.VALUE);
					print(".decode(args.removeFirst(), path.append(");
					printStringLiteral(getConstructorName(te));
					print(", args0.size() - args.size()))");
					printUnboxUnsignedPrimitive(field.asType());
					print(" : (");
					print(defaultValue);
					print(");");
					println(";");
				}
				else {
					print("if(args.isEmpty()) { throw new dev.argon.esexpr.DecodeException(\"Not enough arguments\", path.withConstructor(");
					printStringLiteral(getConstructorName(te));
					println(")); }");
					print("var field_");
					print(field.getSimpleName());
					print(" = ");
					printCodecExpr(field.asType(), field, CodecType.VALUE);
					print(".decode(args.removeFirst(), path.append(");
					printStringLiteral(getConstructorName(te));
					print(", args0.size() - args.size()))");
					printUnboxUnsignedPrimitive(field.asType());
					println(";");
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
		if(!te.equals(elem)) {
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

	private void printUnboxUnsignedPrimitive(TypeMirror t) throws IOException {
		boolean isUnsigned = t.getAnnotationMirrors().stream().anyMatch(ann -> ann.getAnnotationType().toString().equals("dev.argon.esexpr.Unsigned"));
		if(!isUnsigned) {
			return;
		}

		switch(t.getKind()) {
			case BYTE -> print(".byteValue()");
			case SHORT -> print(".shortValue()");
			case INT -> print(".intValue()");
			case LONG -> print(".longValue()");
			default -> {}
		}
	}

	@FunctionalInterface
	interface PrintCallback {
		void run() throws IOException;
	}

	private void printBoxUnsignedPrimitive(TypeMirror t, PrintCallback callback) throws IOException {
		boolean isUnsigned = t.getAnnotationMirrors().stream().anyMatch(ann -> ann.getAnnotationType().toString().equals("dev.argon.esexpr.Unsigned"));
		if(isUnsigned) {
			switch(t.getKind()) {
				case BYTE -> print("dev.argon.esexpr.UnsignedByte.fromByteBits(");
				case SHORT -> print("dev.argon.esexpr.UnsignedShort.fromShortBits(");
				case INT -> print("com.google.common.primitives.UnsignedInteger.fromIntBits(");
				case LONG -> print("com.google.common.primitives.UnsignedLong.fromLongBits(");
				default -> {}
			}
		}
		callback.run();

		if(isUnsigned) {
			switch(t.getKind()) {
				case BYTE, SHORT, INT, LONG -> print(")");
				default -> {}
			}
		}
	}


	private void writeTagsImpl() throws IOException, AbortException {
		var lookup = new Lookup(env, metadataCache, codecLocalScope(), elem);

		switch(lookup.lookupTags(elem.asType())) {
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

	private @Nullable Element codecInstanceCache;
	private @Nullable TypeClassLocalScope codecLocalScopeCache;

	private Element codecInstance() throws AbortException {
		if(codecInstanceCache != null) {
			return codecInstanceCache;
		}

		codecInstanceCache = elem.getEnclosedElements().stream()
			.filter(e ->
				(e.getKind() == ElementKind.METHOD &&
					e.getAnnotation(TypeClassInstance.class) != null &&
					((ExecutableElement)e).getReturnType() instanceof DeclaredType dt1 &&
					((TypeElement)dt1.asElement()).getQualifiedName().toString().equals("dev.argon.esexpr.ESExprCodec")
				) ||
					(e.getKind() == ElementKind.FIELD &&
						e.getAnnotation(TypeClassInstance.class) != null &&
						((VariableElement)e).asType() instanceof DeclaredType dt2 &&
						((TypeElement)dt2.asElement()).getQualifiedName().toString().equals("dev.argon.esexpr.ESExprCodec")
					)
			)
			.findFirst()
			.orElseThrow(() -> new AbortException("Could not find ESExprCodec TypeClassInstance method", elem));

		return codecInstanceCache;
	}

	protected final TypeClassLocalScope codecLocalScope() throws AbortException {
		if(codecLocalScopeCache != null) {
			return codecLocalScopeCache;
		}

		var codecInstance = codecInstance();
		if(codecInstance instanceof ExecutableElement method) {
			codecLocalScopeCache = new TypeClassLocalScope(List.copyOf(method.getParameters()));
		}
		else {
			codecLocalScopeCache = new TypeClassLocalScope(List.of());
		}

		return codecLocalScopeCache;
	}

	protected final List<? extends TypeParameterElement> codecTypeParameters() throws AbortException {
		return codecInstance() instanceof ExecutableElement method
			? method.getTypeParameters()
			: List.of();
	}

	protected final List<? extends VariableElement> codecInstanceParameters() throws AbortException {
		return codecInstance() instanceof ExecutableElement method
			? method.getParameters()
			: List.of();
	}

	protected final void printInstanceParamType(TypeMirror t) throws IOException {
		if(t instanceof DeclaredType dt) {
			var elem = (TypeElement)dt.asElement();
			var parentType = dt.getEnclosingType();

			if(parentType.getKind() == TypeKind.NONE) {
				print(elem.getQualifiedName());
			}
			else {
				printInstanceParamType(parentType);
				print(".");
				print(elem.getSimpleName());
			}

			var typeArgs = dt.getTypeArguments();
			if(!typeArgs.isEmpty()) {
				print("<");
				for(int i = 0; i < typeArgs.size(); ++i) {
					if(i > 0) {
						print(", ");
					}
					printInstanceParamType(typeArgs.get(i));
				}
				print(">");
			}
		}
		else if(t instanceof ArrayType at) {
			printInstanceParamType(at.getComponentType());
			print("[]");
		}
		else if(t instanceof TypeVariable tv) {
			print("T_" + tv.asElement().getSimpleName());
		}
		else {
			print(t.toString());
		}
	}



	protected abstract void validateAnnotations() throws AbortException;
	protected abstract void writeEncodedEqualImpl() throws IOException, AbortException;
	protected abstract void writeEncodeImpl() throws IOException, AbortException;
	protected abstract void writeDecodeImpl() throws IOException, AbortException;



}
