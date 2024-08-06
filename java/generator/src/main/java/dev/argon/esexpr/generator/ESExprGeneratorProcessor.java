package dev.argon.esexpr.generator;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.function.Function;

/**
 * Annotation processor for ESExprCodecGen.
 * Generates ESExprCodecs.
 */
@SupportedAnnotationTypes({"dev.argon.esexpr.ESExprCodecGen"})
public class ESExprGeneratorProcessor extends AbstractProcessor {

	/**
	 * Create the generator.
	 */
	public ESExprGeneratorProcessor() {
	}

    private ProcessingEnvironment processingEnv;
	private MetadataCache metadataCache;


	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
    public void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
		this.metadataCache = new MetadataCache(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var messager = processingEnv.getMessager();

        for(TypeElement annotation : annotations) {
            if(!annotation.getQualifiedName().toString().equals("dev.argon.esexpr.ESExprCodecGen")) {
                continue;
            }

            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            
            for(Element elem : annotatedElements) {
				var typeElem = (TypeElement)elem;

				Function<PrintWriter, GeneratorBase> generatorFactory = switch(typeElem.getKind()) {
					case RECORD -> writer -> new RecordCodecGenerator(writer, processingEnv, metadataCache, typeElem);
					case INTERFACE -> {
						if(typeElem.getModifiers().contains(Modifier.SEALED))
							yield writer -> new EnumCodecGenerator(writer, processingEnv, metadataCache, typeElem);
						else
							yield null;
					}
					case ENUM -> writer -> new SimpleEnumCodecGenerator(writer, processingEnv, metadataCache, typeElem);
					default -> null;
				};

				try {
					if(generatorFactory == null) {
						throw new AbortException("ESExprCodeGen must be used with a record, sealed interface (of records), or an enum.");
					}

					var sw = new StringWriter();
					try(var pw = new PrintWriter(sw)) {
						var gen = generatorFactory.apply(pw);
						gen.generate();
					}

					var codecClassName = typeElem.getQualifiedName().toString() + "_CodecImpl";

					try(var w = processingEnv.getFiler().createSourceFile(codecClassName).openWriter()) {
						w.write(sw.toString());
					}	
				}
				catch(IOException ex) {
					throw new RuntimeException(ex);
				}
				catch(AbortException ex) {
					messager.printError(ex.getMessage(), ex.element == null ? elem : ex.element);
				}
            }
        }
    
        return true;
    }

}
