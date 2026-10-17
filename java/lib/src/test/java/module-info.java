@org.jspecify.annotations.NullMarked module dev.argon.esexpr.tests {
	requires static org.jspecify;
	requires dev.argon.esexpr;
	requires com.google.common;
	requires org.eclipse.collections.api;
	requires org.eclipse.collections.impl;
	requires org.junit.jupiter.api;
	requires org.junit.jupiter.params;
	requires net.jqwik.api;
	requires org.apache.commons.io;
	requires tools.jackson.core;
	requires tools.jackson.databind;
	requires static com.google.errorprone.annotations;

	exports dev.argon.esexpr.tests;
}
