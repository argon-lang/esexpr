using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

public abstract record SourceModelType {
	private SourceModelType() { }


	public abstract SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping);


	public static SourceModelType FromSymbol(ITypeSymbol t) {
		switch(t) {
			case ITypeParameterSymbol tp:
				return new TypeParameter(tp.Name);

			case INamedTypeSymbol named: {
				SourceModelType res = new NamedSymbol(GetNamespaceFromSymbol(named.ContainingNamespace), named.Name) {
					TypeArguments = VList.From(named.TypeArguments.Select(FromSymbol)),
					IsEnum = named.TypeKind == TypeKind.Enum,
				};

				if(t.NullableAnnotation == NullableAnnotation.Annotated) {
					res = new Nullable(res);
				}

				return res;
			}

			case IArrayTypeSymbol arr:
				return new Array(FromSymbol(arr.ElementType));

			case IPointerTypeSymbol ptr:
				return new Pointer(FromSymbol(ptr.PointedAtType));

			case IFunctionPointerTypeSymbol:
				throw new NotSupportedException();

			default:
				throw new Exception("Unexpected type symbol");
		}
	}

	private static VList<string> GetNamespaceFromSymbol(INamespaceSymbol? ns) {
		var parts = new List<string>();

		while(ns != null && !ns.IsGlobalNamespace) {
			parts.Insert(0, ns.Name);
			ns = ns.ContainingNamespace;
		}

		return VList.From(parts);
	}




	public record TypeParameter(string Name) : SourceModelType {
		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			if(!paramMapping.TryGetValue(Name, out var substitute)) {
				return this;
			}

			return substitute;
		}
	}

	public record NamedSymbol(VList<string> Namespace, string Name) : SourceModelType {
		public required VList<SourceModelType> TypeArguments { get; init; }
		public required bool IsEnum { get; init; }

		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return new NamedSymbol(Namespace, Name) {
				TypeArguments = VList.From(TypeArguments.Select(tp => tp.Substitute(paramMapping))),
				IsEnum = this.IsEnum,
			};
		}
	}

	public record Nullable(SourceModelType Inner) : SourceModelType {
		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return new Nullable(Inner.Substitute(paramMapping));
		}
	}

	public record Array(SourceModelType Element) : SourceModelType {
		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return new Array(Element.Substitute(paramMapping));
		}
	}

	public record Pointer(SourceModelType PointedAtType) : SourceModelType {
		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return new Pointer(PointedAtType.Substitute(paramMapping));
		}
	}

}
