using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis;
using static ESExpr.SourceGenerator.GenUtils;

namespace ESExpr.SourceGenerator;

public class CodecOverrideHandler {
	private CodecOverrideHandler(VDictionary<SourceModelType, VList<SourceModelType>> codecOverrides) {
		this.codecOverrides = codecOverrides;
	}

	private readonly VDictionary<SourceModelType, VList<SourceModelType>> codecOverrides;

	public static CodecOverrideHandler Load(Compilation compilation) {
		var codecOverrides = new List<INamedTypeSymbol>();
		
		foreach(var asm in 
		        compilation.References
			        .Select(compilation.GetAssemblyOrModuleSymbol)
			        .OfType<IAssemblySymbol>()
		       ) {
			ScanAssemblyForCodecOverrides(asm, codecOverrides);
		}
		
		ScanAssemblyForCodecOverrides(compilation.Assembly, codecOverrides);
		
		var overrideDict = new Dictionary<SourceModelType, VList<SourceModelType>>();

		foreach(var codecOverride in codecOverrides) {
			var baseTypes = VList.From(GetAllBaseInterfaces(codecOverride).Select(SourceModelType.FromSymbol));
			overrideDict.Add(SourceModelType.FromSymbol(codecOverride), baseTypes);
		}
		
		return new CodecOverrideHandler(VDictionary.From(overrideDict));
	}

	private static void ScanAssemblyForCodecOverrides(
		IAssemblySymbol assemblySymbol,
		List<INamedTypeSymbol> overrides
	) {
		if(!HasAttribute(assemblySymbol, "ESExpr.Runtime.ESExprEnableCodecOverridesAttribute")) {
			return;
		}
		
		foreach(var t in GetAllTypes(assemblySymbol.GlobalNamespace)) {
			if(!HasAttribute(t, "ESExpr.Runtime.ESExprOverrideCodecAttribute")) {
				continue;
			}
			
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
	
	

	public SourceModelType? GetOverriddenCodec(SourceModelType codecType) {
		foreach(var codecOverridePair in codecOverrides) {
			foreach(var codecOverride in codecOverridePair.Value) {
				var paramMapping = CodecMatches(codecType, codecOverride);
				if(paramMapping is null) {
					continue;
				}

				return codecOverridePair.Key.Substitute(paramMapping);
			} 
		}

		return null;
	}

	private static IEnumerable<INamedTypeSymbol> GetAllBaseInterfaces(INamedTypeSymbol t) {
		if(t.BaseType != null) {
			foreach(var baseIface in GetAllBaseInterfaces(t.BaseType)) {
				yield return baseIface;
			}
		}
		
		foreach(var iface in t.Interfaces) {
			yield return iface;

			foreach(var baseIface in GetAllBaseInterfaces(iface)) {
				yield return baseIface;
			}
		}
	}

	private Dictionary<string, SourceModelType>? CodecMatches(SourceModelType actual, SourceModelType expected) {
		var paramMapping = new Dictionary<string, SourceModelType>();

		bool Unify(SourceModelType actual, SourceModelType expected) {
			switch(expected) {
				case SourceModelType.TypeParameter expectedTP:
				{
					if(paramMapping.TryGetValue(expectedTP.Name, out var matched)) {
						return Unify( actual, matched);
					}
					else {
						paramMapping.Add(expectedTP.Name, actual);
						return true;
					}
				}

				case SourceModelType.NamedSymbol expectedNamed:
				{
					if(actual is not SourceModelType.NamedSymbol actualNamed) {
						return false;
					}

					if(actualNamed.Namespace != expectedNamed.Namespace || actualNamed.Name != expectedNamed.Name) {
						return false;
					}

					if(actualNamed.TypeArguments.Count != expectedNamed.TypeArguments.Count) {
						return false;
					}
					
					return actualNamed.TypeArguments.Zip(expectedNamed.TypeArguments, Unify).All(t => t);
				}
				
				case SourceModelType.Array expectedArray:
					if(actual is not SourceModelType.Array actualArray) {
						return false;
					}
					
					return Unify(actualArray.Element, expectedArray.Element);
				
				case SourceModelType.Pointer expectedPointer:
					if(actual is not SourceModelType.Pointer actualPointer) {
						return false;
					}
					
					return Unify(actualPointer.PointedAtType, expectedPointer.PointedAtType);
				
				case SourceModelType.Nullable expectedNullable:
					if(actual is not SourceModelType.Nullable actualNullable) {
						return false;
					}
					
					return Unify(actualNullable.Inner, expectedNullable.Inner);
					
				
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
}
