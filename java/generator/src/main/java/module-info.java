/**
 * Annotation processor that generates ESExprCodecs for types annotated with ESExprCodeGen.
 */
module dev.argon.esexpr.generator {
	requires transitive java.compiler;
	requires org.apache.commons.text; requires dev.argon.esexpr;
	requires org.jspecify;

	exports dev.argon.esexpr.generator;

	provides javax.annotation.processing.Processor with dev.argon.esexpr.generator.ESExprGeneratorProcessor;
}
