package dev.argon.esexpr;

import dev.argon.esexpr.ESExprCodec.FailurePath;

/**
 * An exception thrown when decoding an ESExpr.
 */
public class DecodeException extends Exception {
	/**
	 * Create a decode exception.
	 * @param message The message.
	 * @param path The path where the error occurred.
	 */
	public DecodeException(String message, FailurePath path) {
		super(message);
		this.path = path;
	}

	/**
	 * The path in the object where the failure occurred.
	 */
	public final FailurePath path;
}
