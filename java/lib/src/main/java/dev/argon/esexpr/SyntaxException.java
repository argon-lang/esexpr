package dev.argon.esexpr;

/**
 * An exception indicating that a serialized ESExpr is invalid.
 */
public class SyntaxException extends Exception {
	/**
	 * Create a SyntaxException.
	 */
	public SyntaxException() {}

	public SyntaxException(String message) {
		super(message);
	}

	public SyntaxException(Throwable cause) {
		super(cause);
	}

	public SyntaxException(String message, Throwable cause) {
		super(message, cause);
	}
}
