package dev.argon.esexpr.generator;

import dev.argon.esexpr.ESExprCodecTags;
import dev.argon.esexpr.ESExprEnableCodecOverrides;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static dev.argon.esexpr.generator.AnnotationUtils.getAnnotationArgument;

record CodecOverride(
	Element overridingElement,
	TypeMirror t,
	CodecType codecType,
	List<TypeMirror> requiredAnnotations,
	List<TypeMirror> excludedAnnotations
) {
	enum CodecType {
		VALUE("dev.argon.esexpr.ESExprCodec"),
		OPTIONAL_VALUE("dev.argon.esexpr.OptionalValueCodec"),
		VARARG("dev.argon.esexpr.VarargCodec"),
		DICT("dev.argon.esexpr.DictCodec"),
		;

		CodecType(String codecClass) {
			this.codecClass = codecClass;
		}

		private final String codecClass;

		public String codecClass() {
			return codecClass;
		}
	}


	public static List<CodecOverride> scan(ProcessingEnvironment env) throws AbortException {
		var modules = env.getElementUtils().getAllModuleElements();
		if(modules.isEmpty()) {
			throw new AbortException("No modules were found. Please ensure that all codec mappings are on the module path.");
		}

		var unnamedModule = env.getElementUtils().getModuleElement("");
		if(unnamedModule != null) {
			if(scanModule(unnamedModule).findFirst().isPresent()) {
				throw new AbortException("Codec overrride was found on the unnamed module. Please ensure that all codec mappings are on the module path.");
			}
		}


		var overrides = modules
			.stream()
			.filter(moduleElement -> !moduleElement.isUnnamed())
			.flatMap(CodecOverride::scanModule)
			.toList();

		if(overrides.isEmpty()) {
			throw new AbortException("No overrides were found. Please ensure that all codec mappings are on the module path.");
		}

		return overrides;
	}

	private static Stream<CodecOverride> scanModule(ModuleElement moduleElement) {
		return moduleElement.getEnclosedElements()
			.stream()
			.map(element -> (PackageElement)element)
			.filter(packageElement -> packageElement.getAnnotation(ESExprEnableCodecOverrides.class) != null)
			.flatMap(packageElement -> packageElement.getEnclosedElements().stream())
			.flatMap(CodecOverride::scanElement);
	}

	private static Stream<CodecOverride> scanElement(Element elem) {
		if(!(elem instanceof TypeElement || elem.getKind() == ElementKind.METHOD || elem.getKind() == ElementKind.FIELD)) {
			return Stream.empty();
		}

		return Stream.concat(
			Stream.concat(
				elem.getAnnotationMirrors()
					.stream()
					.filter(ann -> ((TypeElement)ann.getAnnotationType().asElement()).getQualifiedName().toString().equals("dev.argon.esexpr.ESExprCodecOverrideList"))
					.flatMap(ann -> {
						var arg = getAnnotationArgument(ann, "value");
						if(arg == null) {
							throw new RuntimeException("Could not get annotation argument for ESExprCodecOverrideList");
						}

						return ((List<?>)arg.getValue()).stream();
					})
					.map(ann -> (AnnotationMirror)((AnnotationValue)ann).getValue()),

				elem.getAnnotationMirrors()
					.stream()
					.filter(ann -> ((TypeElement)ann.getAnnotationType().asElement()).getQualifiedName().toString().equals("dev.argon.esexpr.ESExprOverrideCodec"))
			)
				.map(annObj -> {
					var ann = (AnnotationMirror)((AnnotationValue)annObj).getValue();

					var valueType = getAnnotationArgument(ann, "value");
					if(valueType == null) {
						throw new RuntimeException("Could not get annotation argument for ESExprOverrideCodec value");
					}

					return new CodecOverride(
						elem,
						(TypeMirror)valueType.getValue(),
						castToCodecType(getAnnotationArgument(ann, "codecType")),
						castToTypeMirrorArray(getAnnotationArgument(ann, "requiredAnnotations")),
						castToTypeMirrorArray(getAnnotationArgument(ann, "excludedAnnotations"))
					);
				}),

			elem.getEnclosedElements()
				.stream()
				.filter(child -> child.getModifiers().contains(Modifier.PUBLIC) && child.getModifiers().contains(Modifier.STATIC))
				.flatMap(CodecOverride::scanElement)
		);
	}

	private static CodecType castToCodecType(@Nullable AnnotationValue value) {
		if(value == null) {
			return CodecType.VALUE;
		}

		return CodecType.valueOf(((VariableElement)value.getValue()).getSimpleName().toString());
	}

	private static List<TypeMirror> castToTypeMirrorArray(@Nullable AnnotationValue value) {
		if(value == null) {
			return List.of();
		}

		return ((List<?>)value.getValue())
			.stream()
			.map(item -> (TypeMirror)((AnnotationValue)item).getValue())
			.toList();
	}

}
