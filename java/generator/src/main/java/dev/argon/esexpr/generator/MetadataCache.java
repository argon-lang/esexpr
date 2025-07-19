package dev.argon.esexpr.generator;

import com.google.common.collect.ImmutableSet;
import dev.argon.esexpr.*;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.*;

import static dev.argon.esexpr.generator.AnnotationUtils.hasAnnotation;
import static dev.argon.esexpr.generator.AnnotationUtils.hasAnnotationByType;
import static dev.argon.esexpr.generator.NameUtils.getConstructorName;

class MetadataCache {
	public MetadataCache(ProcessingEnvironment env) {
		this.env = env;
	}

	private final ProcessingEnvironment env;
	private @Nullable List<CodecOverride> codecOverrides = null;

	public List<CodecOverride> getCodecOverrides() throws AbortException {
		if(codecOverrides == null) {
			codecOverrides = CodecOverride.scan(env);
		}

		return codecOverrides;
	}


	public @Nullable Element findOverrideCodec(TypeMirror t, Element associatedElement, CodecOverride.CodecType codecType) throws AbortException {
		for(var codecOverride : getCodecOverrides()) {
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


	public TypeMirror findCodecElementType(
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

	public ESExprTagSet lookupTags(TypeMirror t, Element associatedElement, ProcessingEnvironment env) throws AbortException {
		return lookupTagsImpl(t, associatedElement, env, new HashSet<>());
	}

	private ESExprTagSet lookupTagsImpl(TypeMirror t, Element associatedElement, ProcessingEnvironment env, Set<String> seenTypes) throws AbortException {
		var overrideCodec = findOverrideCodec(t, associatedElement, CodecOverride.CodecType.VALUE);
		if(overrideCodec != null) {
			return tagsFromAnnotation(t, overrideCodec, associatedElement, env, seenTypes);
		}

		if(t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te && te.getAnnotation(ESExprCodecGen.class) != null) {
			var fullName = te.getQualifiedName().toString();
			if(seenTypes.contains(fullName)) {
				throw new AbortException("Circular tags detected for type " + t, associatedElement);
			}

			seenTypes.add(fullName);

			switch(te.getKind()) {
				case RECORD -> {
					return ESExprTagSet.of(new ESExprTag.Constructor(getConstructorName(te)));
				}
				case INTERFACE -> {
					if(te.getModifiers().contains(Modifier.SEALED)) {
						var tags = ImmutableSet.<ESExprTag>builder();

						for(var c : EnumCodecGenerator.getEnumCases(te, env)) {
							if(c.getAnnotation(InlineValue.class) != null) {
								var field = c.getEnclosedElements()
									.stream()
									.map(e -> e instanceof RecordComponentElement rce ? rce : null)
									.findFirst()
									.orElseThrow();

								switch(lookupTagsImpl(field.asType(), associatedElement, env, seenTypes)) {
									case ESExprTagSet.All all -> {
										return all;
									}
									case ESExprTagSet.Tags(var fieldTags) ->
										tags.addAll(fieldTags);
								}
							}
							else {
								tags.add(new ESExprTag.Constructor(getConstructorName(c)));
							}
						}

						return new ESExprTagSet.Tags(tags.build());
					}
				}
				case ENUM -> {
					return ESExprTagSet.of(ESExprTag.STR);
				}
				default -> {}
			}
		}

		throw new AbortException("Could not determine tags of type " + t, associatedElement);
	}


	private ESExprTagSet tagsFromAnnotation(TypeMirror t, Element codecElement, Element associatedElement, ProcessingEnvironment env, Set<String> seenTypes) throws AbortException {
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

			switch(lookupTagsImpl(tpt, associatedElement, env, seenTypes)) {
				case ESExprTagSet.Tags tpTags -> ts.addAll(tpTags.tags());
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
