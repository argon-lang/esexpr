/**
 * Annotation processor that generates ESExprCodecs for types annotated with ESExprCodeGen.
 */
@SuppressWarnings("module")
module dev.argon.esexpr.generator {
	requires transitive java.compiler;
	requires org.apache.commons.text;
	requires transitive dev.argon.esexpr;
	requires org.jspecify;

	exports dev.argon.esexpr.generator;

	exports dev.argon.esexpr.generator.typeclass to dev.argon.esexpr.generator.tests;

	provides javax.annotation.processing.Processor with dev.argon.esexpr.generator.ESExprGeneratorProcessor;
}
