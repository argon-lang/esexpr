package dev.argon.esexpr.generator.lookup;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import dev.argon.esexpr.*;
import dev.argon.esexpr.generator.codegen.EnumCodecGenerator;
import dev.argon.esexpr.generator.codegen.AbortException;
import dev.argon.esexpr.generator.typeclass.TypeClassLocalScope;
import dev.argon.esexpr.generator.typeclass.TypeClassResolver;
import dev.argon.esexpr.generator.typeclass.TypeClassResult;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;

import static dev.argon.esexpr.generator.utils.NameUtils.getConstructorName;

public class Lookup {
	public Lookup(ProcessingEnvironment env, MetadataCache cache, TypeClassLocalScope localScope, Element associatedElement) {
		this.env = env;
		this.cache = cache;
		this.localScope = localScope;
		this.associatedElement = associatedElement;
	}

	private final ProcessingEnvironment env;
	private final MetadataCache cache;
	private final TypeClassLocalScope localScope;
	private final Element associatedElement;

	public TypeClassResult getCodec(TypeMirror t, CodecType codecType) throws AbortException {
		var resolver = new TypeClassResolver(env.getTypeUtils(), localScope);
		return cache.resolveTypeClass(resolver, getCodecType(t, codecType));
	}

	public TypeMirror findCodecElementType(
		TypeMirror t,
		CodecType codecType
	) throws AbortException {
		if(codecType == CodecType.VALUE) {
			throw new IllegalArgumentException("Cannot find codec element type for VALUE codec");
		}

		var resolver = new TypeClassResolver(env.getTypeUtils(), localScope);

		var expectedCodecType = getCodecType(t, codecType);

		var codecResult = cache.resolveTypeClass(resolver, expectedCodecType);
		if(codecResult == null) {
			throw new AbortException("Could not find " + codecType + " for " + t, associatedElement);
		}

		var fullCodecType = resolver.getTypeClassType(codecResult);

		return ((DeclaredType)fullCodecType).getTypeArguments().get(1);
	}

	public ESExprTagSet lookupTags(TypeMirror t) throws AbortException {
		return lookupTagsImpl(t, new HashSet<>());
	}

	private TypeMirror getCodecType(TypeMirror t, CodecType codecType) throws AbortException {
		var codecElement = env.getElementUtils().getTypeElement(codecType.codecClass());
		if(codecElement == null) {
			throw new AbortException("Could not find " + codecType.codecClass(), associatedElement);
		}

		if(codecType == CodecType.VALUE) {
			return env.getTypeUtils().getDeclaredType(codecElement, box(t));
		}
		else {
			return env.getTypeUtils().getDeclaredType(
				codecElement,
				box(t),
				env.getTypeUtils().getWildcardType(null, null)
			);
		}
	}

	private TypeMirror box(TypeMirror t) throws AbortException {
		boolean isUnsigned = t.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals(Unsigned.class.getCanonicalName()));

		if(t.getKind().isPrimitive()) {
			if(isUnsigned) {
				var unboxedClass = switch(t.getKind()) {
					case BYTE -> UnsignedByte.class;
					case SHORT -> UnsignedShort.class;
					case INT -> UnsignedInteger.class;
					case LONG -> UnsignedLong.class;
					default -> throw new AbortException("Unsupported unsigned type: " + t, associatedElement);
				};

				var te = env.getElementUtils().getTypeElement(unboxedClass.getCanonicalName());
				if(te == null) {
					throw new AbortException("Could not find unsigned type element: " + unboxedClass, associatedElement);
				}

				return env.getTypeUtils().getDeclaredType(te);
			}
			else {
				return env.getTypeUtils().boxedClass((PrimitiveType)t).asType();
			}
		}
		else {
			if(isUnsigned) {
				throw new AbortException("Unsupported unsigned type: " + t, associatedElement);
			}

			return t;
		}
	}


	private ESExprTagSet lookupTagsImpl(TypeMirror t, Set<String> seenTypes) throws AbortException {
		ESExprTagSet tags = null;

		if(t.getKind() == TypeKind.TYPEVAR) {
			tags = new ESExprTagSet.All();
		}
		else if(t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te && te.getAnnotation(ESExprCodecGen.class) != null) {
			tags = tagsDerived(t, te, seenTypes);
		}
		else {
			tags = tagsFromTypeClassInstance(t, seenTypes);
		}

		if(tags != null) {
			return tags;
		}

		throw new AbortException("Could not determine tags of type " + t, associatedElement);
	}

	private @Nullable ESExprTagSet tagsDerived(TypeMirror t, TypeElement te, Set<String> seenTypes) throws AbortException {
		var fullName = te.getQualifiedName().toString();
		if(seenTypes.contains(fullName)) {
			throw new AbortException("Circular tags detected for type " + t, associatedElement);
		}

		seenTypes.add(fullName);

		return switch(te.getKind()) {
			case RECORD -> {
				if(te.getAnnotation(Flags.class) != null) {
					yield ESExprTagSet.of(ESExprTag.INT);
				}
				else {
					yield ESExprTagSet.of(new ESExprTag.Constructor(getConstructorName(te)));
				}
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

							switch(lookupTagsImpl(field.asType(), seenTypes)) {
								case ESExprTagSet.All all -> {
									yield all;
								}
								case ESExprTagSet.Tags(var fieldTags) ->
									tags.addAll(fieldTags);
							}
						}
						else {
							tags.add(new ESExprTag.Constructor(getConstructorName(c)));
						}
					}

					yield new ESExprTagSet.Tags(tags.build());
				}
				else {
					yield null;
				}
			}
			case ENUM -> ESExprTagSet.of(ESExprTag.STR);
			default -> null;
		};
	}


	private ESExprTagSet tagsFromTypeClassInstance(TypeMirror t, Set<String> seenTypes) throws AbortException {
		var resolver = new TypeClassResolver(env.getTypeUtils(), localScope);

		var expectedCodecType = getCodecType(t, CodecType.VALUE);

		var codecResult = resolver.resolve(expectedCodecType);
		if(codecResult == null) {
			throw new AbortException("Could not find codec for " + t, associatedElement);
		}

		var codecElement = switch(codecResult) {
			case TypeClassResult.Field field -> field.field();
			case TypeClassResult.Local _ -> null;
			case TypeClassResult.Method method -> method.method();
		};

		if(codecElement == null) {
			return new ESExprTagSet.All();
		}

		return tagsFromAnnotation(t, codecElement, seenTypes);
	}


	private ESExprTagSet tagsFromAnnotation(TypeMirror t, Element codecElement, Set<String> seenTypes) throws AbortException {
		var tags = codecElement.getAnnotation(ESExprCodecTags.class);
		if(tags == null) {
			throw new AbortException("TypeClassInstances of ESExprCodec must be annotated with ESExprCodecTags, but " + codecElement.getEnclosingElement() + "." + codecElement + " is not", associatedElement);
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

			switch(lookupTagsImpl(tpt, seenTypes)) {
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
