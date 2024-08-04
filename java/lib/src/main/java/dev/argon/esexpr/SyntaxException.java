package dev.argon.esexpr;

/**
 * An exception indicating that a serialized ESExpr is invalid.
 */
public class SyntaxException extends Exception {
	/**
	 * Create a SyntaxException.
	 */
	public SyntaxException() {}

	/**
	 * Create a SyntaxException.
	 * @param message The error message.
	 */
	public SyntaxException(String message) {
		super(message);
	}

	/**
	 * Create a SyntaxException.
	 * @param cause The underlying error.
	 */
	public SyntaxException(Throwable cause) {
		super(cause);
	}

	/**
	 * Create a SyntaxException.
	 * @param message The error message.
	 * @param cause The underlying error.
	 */
	public SyntaxException(String message, Throwable cause) {
		super(message, cause);
	}
}
