using System;
using System.Collections.Generic;
using System.Collections.Immutable;
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
		ImmutableDictionary<SourceModelType, OverrideInfo> codecOverrides,
		ImmutableDictionary<SourceModelType, IntrinsicTypeInfo> intrinsicTypes
	) {
		this.codecOverrides = codecOverrides;
		this.intrinsicTypes = intrinsicTypes;
	}

	private readonly ImmutableDictionary<SourceModelType, OverrideInfo> codecOverrides;
	private readonly ImmutableDictionary<SourceModelType, IntrinsicTypeInfo> intrinsicTypes;

	private record OverrideInfo {
		public required TypeTags? Tags { get; init; }
		
		public required ImmutableList<SourceModelType> Overrides { get; init; }
		
		public override string ToString() => $"OverrideInfo(Tags: {Tags}, Overrides: [{string.Join(", ", Overrides)}])";
		
	}
	
	public record TypeTags(ESExprTagSet tags, ImmutableList<SourceModelType> unionWithTypes);

	// Used to indicate that a codec was found, but may not have type tags. 
	private record CodecTypeTags(TypeTags? Tags);

	private record IntrinsicTypeInfo(TypeTags? tags, ImmutableList<SourceModelType> codecTypes);

	public IEnumerable<SourceModelType> IntrinsicTypes => intrinsicTypes.Keys;
	public IEnumerable<SourceModelType> OverrideTypes => codecOverrides.Keys;

	public static TypeInfoHandler Load(Compilation compilation) {
		var overrides = LoadOverrides(compilation);
		var intrinsicTypes = LoadAllIntrinsicTypes(compilation);
		
		return new TypeInfoHandler(overrides, intrinsicTypes);
	}

	private static ImmutableDictionary<SourceModelType, OverrideInfo> LoadOverrides(Compilation compilation) =>
		AllAssemblies(compilation)
			.SelectMany(ScanAssemblyForCodecOverrides)
			.Select(GetOverrideInfo)
			.ToImmutableDictionary();

	private static KeyValuePair<SourceModelType, OverrideInfo> GetOverrideInfo(INamedTypeSymbol codecOverride) {
		var baseTypes = GetAllBaseInterfaces(codecOverride).ToImmutableList();
		
		var overrideInfo = new OverrideInfo {
			Tags = LoadESExprTags(codecOverride),
			Overrides = baseTypes,
		};

		return new(SourceModelType.FromSymbol(codecOverride), overrideInfo);
	}



	private static ImmutableDictionary<SourceModelType, IntrinsicTypeInfo> LoadAllIntrinsicTypes(Compilation compilation) {
		var tags = ImmutableDictionary.CreateBuilder<SourceModelType, IntrinsicTypeInfo>();

		foreach(var t in AllAssemblies(compilation).SelectMany(GetAllTypes)) {
			var modelType = SourceModelType.FromSymbol(t);
			if(tags.ContainsKey(modelType)) {
				continue;
			}

			var tt = LoadExistingTypeInfo(t);
			if(tt is not null) {
				tags.Add(modelType, tt);
			}
		}

		foreach(var t in GetAllTypes(compilation.Assembly)) {
			var modelType = SourceModelType.FromSymbol(t);
			if(tags.ContainsKey(modelType)) {
				continue;
			}

			var tt = LoadExistingTypeInfo(t) ?? LoadDerivedTypeInfo(t);
			if(tt is not null) {
				tags.Add(modelType, tt);
			}
		}
		
		return tags.ToImmutable();
	}
			

	private static IntrinsicTypeInfo? LoadExistingTypeInfo(INamedTypeSymbol t) {
		INamedTypeSymbol? GetCodecType(string codecInterface, string codecImplType) {
			var codecType = t.GetTypeMembers().FirstOrDefault(m => m.Name == codecImplType);
			if(codecType is null) {
				return null;
			}
		
			bool isCodec = GetAllBaseInterfaces(codecType).Any(i => i is SourceModelType.NamedSymbol named &&
				named.Namespace.SequenceEqual(["ESExpr", "Runtime"]) &&
				named.Name == codecInterface
			);
				
			return !isCodec ? null : codecType;
		}

		var codecType = GetCodecType("IESExprCodec", "Codec");
		var dictCodecType = GetCodecType("IDictCodec", "DictCodec");
		var varargCodecType = GetCodecType("IVarargCodec", "VarargCodec");
		var optionalValueCodecType = GetCodecType("IOptionalValueCodec", "OptionalValueCodec");

		if(codecType is null && dictCodecType is null && varargCodecType is null && optionalValueCodecType is null) {
			return null;
		}

		IEnumerable<INamedTypeSymbol?> codecTypes = [codecType, dictCodecType, varargCodecType, optionalValueCodecType];
		
		var tags = codecType is not null ? LoadESExprTags(codecType) : null;
		var baseTypes = codecTypes
			.SelectMany(t => t is not null ? GetAllBaseInterfaces(t) : [])
			.ToImmutableList();

		return new IntrinsicTypeInfo(tags, baseTypes);
	}

	private static IntrinsicTypeInfo? LoadDerivedTypeInfo(INamedTypeSymbol t) {
		if(!HasAttribute(t, "ESExpr.Runtime.ESExprCodecAttribute")) {
			return null;
		}

		TypeTags typeTags;
		if(t.IsRecord && t.IsSealed) {
			var constructorName = GetConstructorName(t);
			typeTags = new TypeTags(
				ESExprTagSet.Create([new ESExprTag.Constructor(constructorName)]),
				[]
			);
		}
		else if(t.IsRecord && t.IsAbstract) {
			var tags = new HashSet<ESExprTag>();
			var unionWithTypes = ImmutableList.CreateBuilder<SourceModelType>();

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
			
			typeTags = new TypeTags(
				ESExprTagSet.Create(tags),
				unionWithTypes.ToImmutable()
			);
		}
		else {
			return null;
		}
		
		return new IntrinsicTypeInfo(typeTags, [
			new SourceModelType.NamedSymbol(
				["ESExpr", "Runtime"],
				"IESExprCodec"
			) {
				TypeArguments = [SourceModelType.FromSymbol(t)],
				IsEnum = false,
			},
		]);
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
	
	private static TypeTags? LoadESExprTags(INamedTypeSymbol codecType) {
		ESExprTagSet tags = ESExprTagSet.Empty;
		ImmutableList<SourceModelType> unionWithTypes = [];

		var tagsAttr = GetAttribute(codecType, "ESExpr.Runtime.ESExprTagsAttribute");
		if(tagsAttr is null) {
			return null;
		}
		
		TypedConstant? GetAttributeField(string name) =>
			tagsAttr.NamedArguments
				.Where(kvp => kvp.Key == name)
				.Select(kvp => new TypedConstant?(kvp.Value))
				.FirstOrDefault();
		
		if(GetAttributeField("Scalar") is {} scalars) {
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

		if(GetAttributeField("Constructors") is {} constructors) {
			foreach(var constructor in constructors.Values) {
				if(constructor.Value is not string s) {
					throw new Exception("Expected string value for constructor tag");
				}

				tags = tags.Add(new ESExprTag.Constructor(s));
			}
		}

		if(GetAttributeField("All") is {} all) {
			if(all.Value is not bool b) {
				throw new Exception("Expected bool value for all tag");
			}

			if(b) {
				tags = ESExprTagSet.All;
			}
		}

		if(GetAttributeField("UnionWithTypeParameters") is {} tps) {
			unionWithTypes = tps.Values
				.Select(tp => {
					if(tp.Value is not string tpName) {
						throw new Exception("Expected string value for constructor tag");
					}

					return new SourceModelType.TypeParameter(tpName);
				})
				.ToImmutableList<SourceModelType>();
		}
		
		return new TypeTags(tags, unionWithTypes);
	}

	public SourceModelType? GetOverriddenCodec(SourceModelType codecType) =>
		GetOverriddenCodecWith(codecType, (codecType, _, _, state) => codecType.Substitute(state.ParamMapping));

	public SourceModelType? GetElementType(
		SourceModelType codecType,
		SourceModelType.Wildcard wildcard
	) {
		{
			var elementType = GetOverriddenCodecWith(codecType, (_, _, _, state) =>
				state.WildcardMapping.TryGetValue(wildcard.Name, out var elementType)
					? elementType.Substitute(state.ParamMapping)
					: null
			);

			if(elementType is not null) {
				return elementType;
			}
		}

		foreach(var intrinsicTypePair in intrinsicTypes) {
			foreach(var codecInterface in intrinsicTypePair.Value.codecTypes) {
				var state = CodecMatches(codecType, codecInterface);
				if(state is null) {
					continue;
				}
				
				if(!state.WildcardMapping.TryGetValue(wildcard.Name, out var elementType)) {
					continue;
				}
				
				return elementType.Substitute(state.ParamMapping);
			}
		}
		
		return null;
	}


	public TypeTags? GetTags(SourceModelType t) {
		var codecTags = GetOverriddenTags(
			new SourceModelType.NamedSymbol(["ESExpr", "Runtime"], "IESExprCodec") {
				TypeArguments = [t],
				IsEnum = false,
			}
		);

		if(codecTags is not null) {
			return codecTags.Tags;
		}

		if(t is SourceModelType.TypeParameter) {
			return new TypeTags(ESExprTagSet.All, []);
		}
		
		return GetIntrinsicTags(t);
	}

	private CodecTypeTags? GetOverriddenTags(SourceModelType codecType) =>
		GetOverriddenCodecWith(codecType, (_, overrideInfo, _, state) => LookupTags(overrideInfo, state.ParamMapping));

	private CodecTypeTags LookupTags(OverrideInfo overrideInfo, IReadOnlyDictionary<string, SourceModelType> paramMapping) {
		if(overrideInfo.Tags is null) {
			return new CodecTypeTags(null);
		}
		
		return new CodecTypeTags(
			new TypeTags(
				overrideInfo.Tags.tags,
				overrideInfo.Tags.unionWithTypes
					.Select(tp => tp.Substitute(paramMapping))
					.ToImmutableList()
			)
		);
	}
	
	private TypeTags? GetIntrinsicTags(SourceModelType t) {
		if(t is SourceModelType.NamedSymbol { IsEnum: true }) {
			return new TypeTags(ESExprTagSet.Create([new ESExprTag.Bool()]), []);
		}
		
		intrinsicTypes.TryGetValue(t, out var typeInfo);
		return typeInfo?.tags;
	}

	private T? GetOverriddenCodecWith<T>(SourceModelType codecType, Func<SourceModelType, OverrideInfo, SourceModelType, UnifyState, T> handle)
		where T : class? {
		foreach(var codecOverridePair in codecOverrides) {
			foreach(var codecOverride in codecOverridePair.Value.Overrides) {
				var state = CodecMatches(codecType, codecOverride);
				if(state is null) {
					continue;
				}

				return handle(codecOverridePair.Key, codecOverridePair.Value, codecOverride, state);
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

	private class UnifyState {
		public required ImmutableDictionary<string, SourceModelType> ParamMapping { get; set; }
		public required ImmutableDictionary<string, SourceModelType> WildcardMapping { get; set; }
	}

	private UnifyState? CodecMatches(SourceModelType actual, SourceModelType expected) {
		bool Unify(SourceModelType actual, SourceModelType expected, UnifyState state) {
			if(actual is SourceModelType.Wildcard actualWC) {
				if(state.WildcardMapping.TryGetValue(actualWC.Name, out var matched)) {
					return Unify(matched, expected, state);
				}
				else {
					state.WildcardMapping = state.WildcardMapping.Add(actualWC.Name, expected);
					return true;
				}
			}
			
			switch(expected) {
				case SourceModelType.TypeParameter expectedTP: {
					if(state.ParamMapping.TryGetValue(expectedTP.Name, out var matched)) {
						return Unify(actual, matched, state);
					}
					else {
						state.ParamMapping = state.ParamMapping.Add(expectedTP.Name, actual);
						return true;
					}
				}
				
				case SourceModelType.Wildcard expectedWC: {
					if(state.ParamMapping.TryGetValue(expectedWC.Name, out var matched)) {
						return Unify(actual, matched, state);
					}
					else {
						state.ParamMapping = state.ParamMapping.Add(expectedWC.Name, actual);
						return true;
					}
				}

				case SourceModelType.NamedSymbol expectedNamed: {
					if(actual is not SourceModelType.NamedSymbol actualNamed) {
						return false;
					}

					if(!actualNamed.Namespace.SequenceEqual(expectedNamed.Namespace) || actualNamed.Name != expectedNamed.Name) {
						return false;
					}

					if(actualNamed.TypeArguments.Count != expectedNamed.TypeArguments.Count) {
						return false;
					}

					var state2 = new UnifyState {
						ParamMapping = state.ParamMapping,
						WildcardMapping = state.WildcardMapping,
					};

					bool res = actualNamed.TypeArguments.Zip(expectedNamed.TypeArguments, (a, b) => Unify(a, b, state2)).All(t => t);
					if(res) {
						state.ParamMapping = state2.ParamMapping;
						state.WildcardMapping = state2.WildcardMapping;
					}
					
					return res;
				}

				case SourceModelType.Array expectedArray:
					if(actual is not SourceModelType.Array actualArray) {
						return false;
					}

					return Unify(actualArray.Element, expectedArray.Element, state);

				case SourceModelType.Pointer expectedPointer:
					if(actual is not SourceModelType.Pointer actualPointer) {
						return false;
					}

					return Unify(actualPointer.PointedAtType, expectedPointer.PointedAtType, state);

				case SourceModelType.Nullable expectedNullable:
					if(actual is not SourceModelType.Nullable actualNullable) {
						return false;
					}

					return Unify(actualNullable.Inner, expectedNullable.Inner, state);

				default:
					throw new Exception("Unexpected type symbol");
			}
		}

		{
			var state = new UnifyState {
				ParamMapping = ImmutableDictionary<string, SourceModelType>.Empty,
				WildcardMapping = ImmutableDictionary<string, SourceModelType>.Empty,
			};

			if(Unify(actual, expected, state)) {
				return state;
			}
			else {
				return null;
			}
		}
	}
}
