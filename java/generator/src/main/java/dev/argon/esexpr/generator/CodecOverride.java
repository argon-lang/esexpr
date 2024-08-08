package dev.argon.esexpr.generator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

record CodecOverride(Element overridingElement, TypeMirror t, CodecType codecType, List<TypeMirror> requiredAnnotations, List<TypeMirror> excludedAnnotations) {
	enum CodecType {
		VALUE,
		OPTIONAL_VALUE,
		VARARG,
		DICT,
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
			.filter(packageElement -> GeneratorBase.hasAnnotation(packageElement.getAnnotationMirrors(), "dev.argon.esexpr.ESExprEnableCodecOverrides"))
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
					.flatMap(ann -> ((List<?>)GeneratorBase.getAnnotationArgument(ann, "value").get().getValue()).stream())
					.map(ann -> (AnnotationMirror)((AnnotationValue)ann).getValue()),

				elem.getAnnotationMirrors()
					.stream()
					.filter(ann -> ((TypeElement)ann.getAnnotationType().asElement()).getQualifiedName().toString().equals("dev.argon.esexpr.ESExprOverrideCodec"))
			)
				.map(annObj -> {
					var ann = (AnnotationMirror)((AnnotationValue)annObj).getValue();


					return new CodecOverride(
						elem,
						(TypeMirror)GeneratorBase.getAnnotationArgument(ann, "value").get().getValue(),
						castToCodecType(GeneratorBase.getAnnotationArgument(ann, "codecType")),
						castToTypeMirrorArray(GeneratorBase.getAnnotationArgument(ann, "requiredAnnotations")),
						castToTypeMirrorArray(GeneratorBase.getAnnotationArgument(ann, "excludedAnnotations"))
					);
				}),

			elem.getEnclosedElements()
				.stream()
				.filter(child -> child.getModifiers().contains(Modifier.PUBLIC) && child.getModifiers().contains(Modifier.STATIC))
				.flatMap(CodecOverride::scanElement)
		);
	}

	private static CodecType castToCodecType(Optional<AnnotationValue> value) {
		return value
			.map(v -> CodecType.valueOf(((VariableElement)v.getValue()).getSimpleName().toString()))
			.orElse(CodecType.VALUE);
	}

	private static List<TypeMirror> castToTypeMirrorArray(Optional<AnnotationValue> value) {
		return value
			.map(v -> (List<?>)v.getValue())
			.orElse(List.of())
			.stream()
			.map(item -> (TypeMirror)((AnnotationValue)item).getValue())
			.toList();
	}

}
