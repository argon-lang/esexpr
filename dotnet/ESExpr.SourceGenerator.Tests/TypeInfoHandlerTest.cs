using System.Diagnostics;
using System.Reflection;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;

namespace ESExpr.SourceGenerator.Tests;

public class TypeInfoHandlerTest {
	
        [Test]
        public void SimpleGeneratorTest()
        {
            // Create the 'input' compilation that the generator will act on
            Compilation inputCompilation = CreateCompilation(@"
namespace MyCode
{
    public class Program
    {
        public static void Main(string[] args)
        {
        }
    }
}
");

            var generator = new ESExprCodecSourceGenerator();

            // Create the driver that will control the generation, passing in our generator
            GeneratorDriver driver = CSharpGeneratorDriver.Create(generator);

            // Run the generation pass
            // (Note: the generator driver itself is immutable, and all calls return an updated version of the driver that you should use for subsequent calls)
            driver = driver.RunGeneratorsAndUpdateCompilation(inputCompilation, out var outputCompilation, out var diagnostics);


            var typeInfoHandler = TypeInfoHandler.Load(outputCompilation);
            
            var intType = new SourceModelType.NamedSymbol(new SourceModelType.NamespaceSymbolParent(["System"]), "Int32") {
	            TypeArguments = [],
	            IsEnum = false,
            };
            
            var intCodecType = new SourceModelType.NamedSymbol(new SourceModelType.NamespaceSymbolParent(["ESExpr", "Runtime"]), "IESExprCodec") {
	            TypeArguments = [intType],
	            IsEnum = false,
            };
            
            Assert.That(
	            typeInfoHandler.GetOverriddenCodec(intCodecType),
		        Is.Not.Null
		    );

        }

        private static Compilation CreateCompilation(string source)
            => CSharpCompilation.Create("compilation",
	            [CSharpSyntaxTree.ParseText(source)],
	            [
		            MetadataReference.CreateFromFile(typeof(Binder).GetTypeInfo().Assembly.Location),
		            MetadataReference.CreateFromFile(typeof(ESExpr.Runtime.Expr).GetTypeInfo().Assembly.Location),
	            ],
                new CSharpCompilationOptions(OutputKind.ConsoleApplication));
}
