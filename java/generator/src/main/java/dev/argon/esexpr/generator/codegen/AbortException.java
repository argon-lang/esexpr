package dev.argon.esexpr.generator.codegen;

import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Element;

public class AbortException extends Exception {
	public AbortException(String message) {
		super(message);
		this.element = null;
	}
	public AbortException(String message, Element element) {
		super(message);
		this.element = element;
	}

	public final @Nullable Element element;
}
