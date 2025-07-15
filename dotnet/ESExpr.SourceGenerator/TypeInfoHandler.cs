using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Runtime.InteropServices.ComTypes;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static ESExpr.SourceGenerator.GenUtils;
using static ESExpr.SourceGenerator.NameUtils;

namespace ESExpr.SourceGenerator;

internal class TypeInfoHandler {
	private TypeInfoHandler(
		VDictionary<SourceModelType, OverrideInfo> codecOverrides,
		VDictionary<SourceModelType, TypeTags> intrinsicTags
	) {
		this.codecOverrides = codecOverrides;
		this.intrinsicTags = intrinsicTags;
	}

	private readonly VDictionary<SourceModelType, OverrideInfo> codecOverrides;
	private readonly VDictionary<SourceModelType, TypeTags> intrinsicTags;

	private record OverrideInfo {
		public required TypeTags Tags { get; init; }
		
		public required VList<SourceModelType> Overrides { get; init; }
	}
	
	public record TypeTags(ESExprTagSet tags, VList<SourceModelType> unionWithTypes);

	public static TypeInfoHandler Load(Compilation compilation) {
		var overrides = LoadOverrides(compilation);
		var intrinsicTags = LoadAllIntrinsicTypeTags(compilation);
		
		return new TypeInfoHandler(overrides, intrinsicTags);
	}

	private static VDictionary<SourceModelType, OverrideInfo> LoadOverrides(Compilation compilation) =>
		VDictionary.From(
			AllAssemblies(compilation)
				.SelectMany(ScanAssemblyForCodecOverrides)
				.Select(GetOverrideInfo)
		);

	private static KeyValuePair<SourceModelType, OverrideInfo> GetOverrideInfo(INamedTypeSymbol codecOverride) {
		var baseTypes = VList.From(GetAllBaseInterfaces(codecOverride));
		
		var overrideInfo = new OverrideInfo {
			Tags = LoadESExprTags(codecOverride),
			Overrides = baseTypes,
		};

		return new(SourceModelType.FromSymbol(codecOverride), overrideInfo);
	}



	private static VDictionary<SourceModelType, TypeTags> LoadAllIntrinsicTypeTags(Compilation compilation) {
		var tags = new Dictionary<SourceModelType, TypeTags>();

		foreach(var t in AllAssemblies(compilation).SelectMany(GetAllTypes)) {
			var modelType = SourceModelType.FromSymbol(t);
			if(tags.ContainsKey(modelType)) {
				continue;
			}

			var tt = LoadExistingTypeTags(t);
			if(tt is not null) {
				tags.Add(modelType, tt);
			}
		}

		foreach(var t in GetAllTypes(compilation.Assembly)) {
			var modelType = SourceModelType.FromSymbol(t);
			if(tags.ContainsKey(modelType)) {
				continue;
			}

			var tt = LoadDerivedTypeTags(t);
			if(tt is not null) {
				tags.Add(modelType, tt);
			}
		}
		
		return VDictionary.From(tags);
	}
			

	private static TypeTags? LoadExistingTypeTags(INamedTypeSymbol t) {
		var codecType = t.GetTypeMembers().FirstOrDefault(m => m.Name == "Codec");
		if(codecType is null) {
			return null;
		}
		
		bool isCodec = GetAllBaseInterfaces(codecType).Any(i => i is SourceModelType.NamedSymbol named &&
			named.Namespace == VList.Of("ESExpr", "Runtime") &&
			named.Name == "IESExprCodec"
		);
				
		if(!isCodec) {
			return null;
		}
		
		return LoadESExprTags(codecType);
	}

	private static TypeTags? LoadDerivedTypeTags(INamedTypeSymbol t) {
		if(!HasAttribute(t, "ESExpr.Runtime.ESExprCodecAttribute")) {
			return null;
		}

		if(t.IsRecord && t.IsSealed) {
			var constructorName = GetConstructorName(t);
			return new TypeTags(
				ESExprTagSet.Create([new ESExprTag.Constructor(constructorName)]),
				VList<SourceModelType>.Empty
			);
		}
		else if(t.IsRecord && t.IsAbstract) {
			var tags = new HashSet<ESExprTag>();
			var unionWithTypes = new List<SourceModelType>();

			var cases = t.GetTypeMembers()
				.Where(caseRec =>
					caseRec.IsRecord &&
					caseRec.DeclaredAccessibility == Accessibility.Public
				);

			foreach(var c in cases) {
				if(HasAttribute(c, "ESExpr.Runtime.InlineValueAttribute")) {
					var fields = c.GetMembers().OfType<IFieldSymbol>().ToList();
					
					if(fields.Count != 1) {
						throw new AbortGenerationException(
							Diagnostic.Create(
								Errors.InvalidInlineValue,
								null,
								new object[] { }
							)
						);
					}
					
					unionWithTypes.Add(SourceModelType.FromSymbol(fields[0].Type));
				}
				else {
					tags.Add(new ESExprTag.Constructor(GetConstructorName(c)));
				}
			}
			
			return new TypeTags(
				ESExprTagSet.Create(tags),
				VList.From(unionWithTypes)
			);
		}
		else {
			return null;
		}
	}
	
	private static IEnumerable<IAssemblySymbol> AllAssemblies(Compilation compilation) =>
		compilation.References
			.Select(compilation.GetAssemblyOrModuleSymbol)
			.OfType<IAssemblySymbol>()
			.Where(AssemblyUsesESExpr)
			.Concat([compilation.Assembly]);

	private static IEnumerable<INamedTypeSymbol> ScanAssemblyForCodecOverrides(IAssemblySymbol assemblySymbol) =>
		GetAllTypes(assemblySymbol.GlobalNamespace)
			.Where(t => HasAttribute(t, "ESExpr.Runtime.ESExprOverrideCodecAttribute"));
	
	private static bool AssemblyUsesESExpr(IAssemblySymbol assemblySymbol) {
		if(assemblySymbol.Name == "ESExpr.Runtime") {
			return true;
		}

		return assemblySymbol.Modules
			.SelectMany(m => m.ReferencedAssemblies)
			.Any(a => a.Name == "ESExpr.Runtime");
	}


	private static IEnumerable<INamedTypeSymbol> GetAllTypes(IAssemblySymbol assemblySymbol) =>
		GetAllTypes(assemblySymbol.GlobalNamespace);
	
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
	
	private static TypeTags LoadESExprTags(INamedTypeSymbol codecType) {
		ESExprTagSet tags = ESExprTagSet.Empty;
		VList<SourceModelType> unionWithTypes = VList<SourceModelType>.Empty;

		var tagsAttr = GetAttribute(codecType, "ESExpr.Runtime.ESExprTagsAttribute");
		if(tagsAttr is not null) {
			TypedConstant? GetAttribute(string name) =>
				tagsAttr.NamedArguments
					.Where(kvp => kvp.Key == name)
					.Select(kvp => new TypedConstant?(kvp.Value))
					.FirstOrDefault();
			
			if(GetAttribute("Scalar") is {} scalars) {
				foreach(var scalar in scalars.Values) {
					if(scalar.Value is not int intValue) {
						throw new Exception("Expected int enum value for scalar tag");
					}

					var enumValue = (ESExprTag.ScalarType)intValue;

					tags = tags.Add(enumValue switch {
						ESExprTag.ScalarType.Bool => new ESExprTag.Bool(),
						ESExprTag.ScalarType.Int => new ESExprTag.Int(),
						ESExprTag.ScalarType.Str => new ESExprTag.Str(),
						ESExprTag.ScalarType.Float16 => new ESExprTag.Float16(),
						ESExprTag.ScalarType.Float32 => new ESExprTag.Float32(),
						ESExprTag.ScalarType.Float64 => new ESExprTag.Float64(),
						ESExprTag.ScalarType.Array8 => new ESExprTag.Array8(),
						ESExprTag.ScalarType.Array16 => new ESExprTag.Array16(),
						ESExprTag.ScalarType.Array32 => new ESExprTag.Array32(),
						ESExprTag.ScalarType.Array64 => new ESExprTag.Array64(),
						ESExprTag.ScalarType.Array128 => new ESExprTag.Array128(),
						ESExprTag.ScalarType.Null => new ESExprTag.Null(),
						_ => throw new Exception("Unknown enum value: " + (int)enumValue),
					});
				}
			}

			if(GetAttribute("Constructors") is {} constructors) {
				foreach(var constructor in constructors.Values) {
					if(constructor.Value is not string s) {
						throw new Exception("Expected string value for constructor tag");
					}

					tags = tags.Add(new ESExprTag.Constructor(s));
				}
			}

			if(GetAttribute("All") is {} all) {
				if(all.Value is not bool b) {
					throw new Exception("Expected bool value for all tag");
				}

				if(b) {
					tags = ESExprTagSet.All;
				}
			}

			if(GetAttribute("UnionWithTypeParameters") is {} tps) {
				unionWithTypes = VList.From<SourceModelType>(
					tps.Values.Select(tp => {
						if(tp.Value is not string tpName) {
							throw new Exception("Expected string value for constructor tag");
						}

						return new SourceModelType.TypeParameter(tpName);
					})
				);
			}
		}
		
		return new TypeTags(tags, unionWithTypes);
	}



	public SourceModelType? GetOverriddenCodec(SourceModelType codecType) =>
		GetOverriddenCodecWith(codecType, (codecType, _, _, paramMapping) => codecType.Substitute(paramMapping));

	public SourceModelType? GetElementType(SourceModelType codecType) =>
		GetOverriddenCodecWith(codecType, (_, _, codecInterfaceType, paramMapping) =>
			codecInterfaceType is SourceModelType.NamedSymbol named &&
				named.TypeArguments.Count == 1 
			? named.TypeArguments[0].Substitute(paramMapping)
			: null
		);
	
	
	public TypeTags? GetTags(SourceModelType t) =>
		GetOverriddenTags(
			new SourceModelType.NamedSymbol(VList.Of("ESExpr", "Runtime"), "IESExprCodec") {
				TypeArguments = VList.Of(t),
				IsEnum = false,
			}
		) ??
			GetIntrinsicTags(t);

	private TypeTags? GetOverriddenTags(SourceModelType codecType) =>
		GetOverriddenCodecWith(codecType, (_, overrideInfo, _, paramMapping) => LookupTags(overrideInfo, paramMapping));

	private TypeTags LookupTags(OverrideInfo overrideInfo, Dictionary<string, SourceModelType> paramMapping) {
		return new TypeTags(
			overrideInfo.Tags.tags,
			VList.From(overrideInfo.Tags.unionWithTypes.Select(tp => tp.Substitute(paramMapping)))
		);
	}
	
	private TypeTags? GetIntrinsicTags(SourceModelType t) {
		if(t is SourceModelType.NamedSymbol { IsEnum: true }) {
			return new TypeTags(ESExprTagSet.Create([new ESExprTag.Bool()]), VList<SourceModelType>.Empty);
		}
		
		intrinsicTags.TryGetValue(t, out var tags);
		return tags;
	}

	private T? GetOverriddenCodecWith<T>(SourceModelType codecType, Func<SourceModelType, OverrideInfo, SourceModelType, Dictionary<string, SourceModelType>, T> handle)
		where T : class? {
		foreach(var codecOverridePair in codecOverrides) {
			foreach(var codecOverride in codecOverridePair.Value.Overrides) {
				var paramMapping = CodecMatches(codecType, codecOverride);
				if(paramMapping is null) {
					continue;
				}

				return handle(codecOverridePair.Key, codecOverridePair.Value, codecOverride, paramMapping);
			}
		}

		return null;
	}

	private static IEnumerable<SourceModelType> GetAllBaseInterfaces(INamedTypeSymbol t) {
		var paramMapping = t.TypeParameters.ToDictionary(tp => tp.Name, tp => {
			SourceModelType tpt = new SourceModelType.TypeParameter(tp.Name);
			return tpt;
		});
		
		if(t.BaseType != null) {
			foreach(var baseIface in GetAllBaseInterfaces(t.BaseType)) {
				yield return baseIface.Substitute(paramMapping);
			}
		}

		foreach(var iface in t.Interfaces) {
			yield return SourceModelType.FromSymbol(iface).Substitute(paramMapping);

			foreach(var baseIface in GetAllBaseInterfaces(iface)) {
				yield return baseIface.Substitute(paramMapping);
			}
		}
	}

	private Dictionary<string, SourceModelType>? CodecMatches(SourceModelType actual, SourceModelType expected) {
		var paramMapping = new Dictionary<string, SourceModelType>();

		bool Unify(SourceModelType actual, SourceModelType expected) {
			switch(expected) {
				case SourceModelType.TypeParameter expectedTP: {
					if(paramMapping.TryGetValue(expectedTP.Name, out var matched)) {
						return Unify(actual, matched);
					}
					else {
						paramMapping.Add(expectedTP.Name, actual);
						return true;
					}
				}

				case SourceModelType.NamedSymbol expectedNamed: {
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
