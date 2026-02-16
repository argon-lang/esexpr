package dev.argon.esexpr.generator.utils;

import org.jspecify.annotations.Nullable;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;

public final class AnnotationUtils {
	private AnnotationUtils() {}


	public static boolean hasAnnotation(List<? extends AnnotationMirror> annotations, String name) {
		return getAnnotation(annotations, name) != null;
	}

	public static @Nullable AnnotationMirror getAnnotation(List<? extends AnnotationMirror> annotations, String name) {
		return annotations.stream()
			.filter(ann -> ((TypeElement)ann.getAnnotationType().asElement()).getQualifiedName().toString().equals(name))
			.findFirst()
			.orElse(null);
	}

	public static @Nullable AnnotationValue getAnnotationArgument(AnnotationMirror ann, String name) {
		return ann.getElementValues()
			.entrySet()
			.stream()
			.filter(entry -> entry.getKey().getSimpleName().toString().equals(name))
			.findFirst()
			.map(Map.Entry::getValue)
			.orElse(null);
	}


}
