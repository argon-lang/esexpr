/**
 * ESExpr runtime library.
 */
module dev.argon.esexpr {
	requires transitive com.google.common;
	requires transitive org.eclipse.collections.api;
	requires transitive org.eclipse.collections.impl;
    requires static org.jspecify;
    exports dev.argon.esexpr;
}
