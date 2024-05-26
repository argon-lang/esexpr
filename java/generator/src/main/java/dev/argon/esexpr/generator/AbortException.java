package dev.argon.esexpr.generator;

import javax.lang.model.element.Element;

class AbortException extends Exception {
	public AbortException(String message) {
		super(message);
		this.element = null;
	}
	public AbortException(String message, Element element) {
		super(message);
		this.element = element;
	}

	public final Element element;
}
