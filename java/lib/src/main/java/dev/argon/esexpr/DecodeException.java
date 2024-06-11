package dev.argon.esexpr;

import dev.argon.esexpr.ESExprCodec.FailurePath;

public class DecodeException extends Exception {
	public DecodeException(String message, FailurePath path) {
		super(message);
		this.path = path;
	}

	public final FailurePath path;
}
