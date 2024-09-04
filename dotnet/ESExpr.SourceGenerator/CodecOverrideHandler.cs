using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis;
using static ESExpr.SourceGenerator.GenUtils;

namespace ESExpr.SourceGenerator;

public class CodecOverrideHandler {
	private CodecOverrideHandler(Compilation compilation, IReadOnlyList<ITypeSymbol> codecOverrides) {
		this.compilation = compilation;
		this.codecOverrides = codecOverrides;
	}

	private readonly Compilation compilation;
	private readonly IReadOnlyList<ITypeSymbol> codecOverrides;

	public static CodecOverrideHandler Load(GeneratorExecutionContext context) {
		var codecOverrides = new List<ITypeSymbol>();
		
		foreach(var asm in 
		        context.Compilation.References
			        .Select(asm => context.Compilation.GetAssemblyOrModuleSymbol(asm))
			        .OfType<IAssemblySymbol>()
		       ) {

			if(HasAttribute(asm, "ESExpr.Runtime.ESExprEnableCodecOverridesAttribute")) {
				ScanAssemblyForCodecOverrides(context, asm, codecOverrides);	
			}
		}
		
		ScanAssemblyForCodecOverrides(context, context.Compilation.Assembly, codecOverrides);
		
		return new CodecOverrideHandler(context.Compilation, codecOverrides);
	}

	private static void ScanAssemblyForCodecOverrides(
		GeneratorExecutionContext context,
		IAssemblySymbol assemblySymbol,
		IList<ITypeSymbol> overrides
	) {
		foreach(var t in GetAllTypes(assemblySymbol.GlobalNamespace)) {
			if(!HasAttribute(t, "ESExpr.Runtime.ESExprOverrideCodecAttribute")) {
				continue;
			}

			// var originalType = mappedTypeAttr.ConstructorArguments.Length > 0 ? mappedTypeAttr.ConstructorArguments[0].Value as ITypeSymbol : null;
			// if(originalType == null) {
			// 	context.ReportDiagnostic(Diagnostic.Create(
			// 		Errors.InvalidMappedType,
			// 		mappedTypeAttr.ApplicationSyntaxReference?.GetSyntax().GetLocation(),
			// 		t.ToDisplayString()
			// 	));
			// 	continue;
			// }
			
			overrides.Add(t);
		}
	}
	
	
	private static IEnumerable<INamedTypeSymbol> GetAllTypes(INamespaceOrTypeSymbol parentSymbol) {
		foreach(var type in parentSymbol.GetTypeMembers()) {
			yield return type;

			foreach(var nestedType in GetAllTypes(type)) {
				yield return nestedType;
			}
		}

		if(parentSymbol is INamespaceSymbol namespaceSymbol) {
			foreach(var ns in namespaceSymbol.GetNamespaceMembers()) {
				foreach(var type in GetAllTypes(ns)) {
					yield return type;
				}
			}
		}
	}
	
	

	public ITypeSymbol? GetOverriddenCodec(ITypeSymbol codecType) {
		foreach(var codecOverride in codecOverrides) {
			foreach(var overrideInterface in GetAllInterfaces(codecOverride)) {
				var paramMapping = CodecMatches(codecType, overrideInterface);
				if(paramMapping != null) {
					return SubstituteTypeParameters(codecOverride, paramMapping);
				}
			}
		}

		return null;
	}

	private IEnumerable<INamedTypeSymbol> GetAllInterfaces(ITypeSymbol t) {
		if(t.BaseType != null) {
			foreach(var baseIface in GetAllInterfaces(t.BaseType)) {
				yield return baseIface;
			}
		}
		
		foreach(var iface in t.Interfaces) {
			yield return iface;

			foreach(var baseIface in GetAllInterfaces(iface)) {
				yield return baseIface;
			}
		}
	}

	private Dictionary<string, ITypeSymbol>? CodecMatches(ITypeSymbol actual, ITypeSymbol expected) {
		var paramMapping = new Dictionary<string, ITypeSymbol>();

		bool Unify(ITypeSymbol actual, ITypeSymbol expected) {
			switch(expected) {
				case ITypeParameterSymbol expectedTP:
				{
					if(paramMapping.TryGetValue(expectedTP.Name, out var matched)) {
						return Unify( actual, matched);
					}
					else {
						paramMapping.Add(expectedTP.Name, actual);
						return true;
					}
				}

				case INamedTypeSymbol expectedNamed:
				{
					if(actual is not INamedTypeSymbol actualNamed) {
						return false;
					}

					if(!SymbolEqualityComparer.IncludeNullability.Equals(
						   actualNamed.ConstructedFrom,
						   expectedNamed.ConstructedFrom
					)) {
						return false;
					}

					if(actualNamed.TypeArguments.Length != expectedNamed.TypeArguments.Length) {
						return false;
					}
					
					return actualNamed.TypeArguments.Zip(expectedNamed.TypeArguments, Unify).All(t => t);
				}
				
				case IArrayTypeSymbol expectedArray:
					if(actual is not IArrayTypeSymbol actualArray) {
						return false;
					}
					
					return Unify(actualArray.ElementType, expectedArray.ElementType);
				
				case IPointerTypeSymbol expectedPointer:
					if(actual is not IPointerTypeSymbol actualPointer) {
						return false;
					}
					
					return Unify(actualPointer.PointedAtType, expectedPointer.PointedAtType);
				
				case IFunctionPointerTypeSymbol:
					throw new NotSupportedException();
				
				default:
					throw new Exception("Unexpected type symbol");
			}
		}

		if(Unify(actual, expected)) {
			return paramMapping;
		}
		else {
			return null;
		}
	}
	
	private ITypeSymbol SubstituteTypeParameters(ITypeSymbol typeSymbol, Dictionary<string, ITypeSymbol> paramMapping) {
        switch (typeSymbol) {
	        case INamedTypeSymbol namedTypeSymbol:
	        {
		        var typeParameters = namedTypeSymbol.TypeParameters;
		        if(typeParameters.Length == 0) {
			        return namedTypeSymbol;
		        }

		        return namedTypeSymbol.Construct(typeParameters.Select(typeParameter => paramMapping.TryGetValue(typeParameter.Name, out var substitution)
			        ? substitution
			        : typeParameter).ToArray());
	        }

            case IArrayTypeSymbol arrayTypeSymbol:
                var elementType = SubstituteTypeParameters(arrayTypeSymbol.ElementType, paramMapping);
                return compilation.CreateArrayTypeSymbol(elementType);

            case IPointerTypeSymbol pointerTypeSymbol:
                var pointedAtType = SubstituteTypeParameters(pointerTypeSymbol.PointedAtType, paramMapping);
                return compilation.CreatePointerTypeSymbol(pointedAtType);

            case ITypeParameterSymbol typeParameterSymbol:
                return paramMapping.TryGetValue(typeParameterSymbol.Name, out var substitution) 
                    ? substitution 
                    : typeParameterSymbol;
				
            case IFunctionPointerTypeSymbol:
	            throw new NotSupportedException();

            default:
                return typeSymbol;
        }
    }
}
