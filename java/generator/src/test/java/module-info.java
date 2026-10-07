module dev.argon.esexpr.generator.tests {
	requires java.compiler;
	requires dev.argon.esexpr;
	requires com.google.common;
	requires dev.argon.esexpr.generator;
	requires org.junit.jupiter.api;
	requires org.jspecify;

	exports dev.argon.esexpr.generator.tests;
	exports dev.argon.esexpr.generator.gen;
	exports dev.argon.esexpr.generator.gen.tests;
	exports dev.argon.esexpr.generator.typeclass.tests;
}
