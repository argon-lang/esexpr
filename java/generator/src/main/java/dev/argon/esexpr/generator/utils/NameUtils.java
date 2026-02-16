package dev.argon.esexpr.generator.utils;

import dev.argon.esexpr.Constructor;
import dev.argon.esexpr.Keyword;

import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class NameUtils {


	private static final String NAME_SPLIT_PATTERN = "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])_(?=[0-9])";
	private static String nameToKebabCase(String name) {
		return Arrays.stream(name.split(NAME_SPLIT_PATTERN))
			.map(s -> s.toLowerCase(Locale.ROOT))
			.collect(Collectors.joining("-"));
	}

	public static String nameToCamelCase(String name) {
		var parts = name.split(NAME_SPLIT_PATTERN);
		parts[0] = parts[0].toLowerCase(Locale.ROOT);
		return String.join("", parts);
	}

	private static String enumConstNameToKebabCase(String name) {
		return Arrays.stream(name.split("_"))
			.map(s -> s.toLowerCase(Locale.ROOT))
			.collect(Collectors.joining("-"));
	}


	public static String getConstructorName(TypeElement elem) {
		var ctor = elem.getAnnotation(Constructor.class);
		if(ctor != null) {
			return ctor.value();
		}

		return nameToKebabCase(elem.getSimpleName().toString());
	}

	public static String getConstructorName(VariableElement elem) {
		var ctor = elem.getAnnotation(Constructor.class);
		if(ctor != null) {
			return ctor.value();
		}

		return enumConstNameToKebabCase(elem.getSimpleName().toString());
	}

	public static String getKeywordName(RecordComponentElement rce, Keyword keyword) {
		if(keyword.value().isEmpty()) {
			return nameToKebabCase(rce.getSimpleName().toString());
		}
		else {
			return keyword.value();
		}
	}

}
