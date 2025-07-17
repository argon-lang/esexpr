using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using System.Text;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

public abstract record SourceModelType {
	private SourceModelType() { }


	protected abstract bool ContainsTypeParameter(string name);
	public abstract SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping);


	public static SourceModelType FromSymbol(ITypeSymbol t) {
		switch(t) {
			case ITypeParameterSymbol tp:
				return new TypeParameter(tp.Name);

			case INamedTypeSymbol named: {
				SourceModelType res = new NamedSymbol(GetNamespaceFromSymbol(named.ContainingNamespace), named.Name) {
					TypeArguments = named.TypeArguments.Select(FromSymbol).ToImmutableList(),
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

	private static ImmutableList<string> GetNamespaceFromSymbol(INamespaceSymbol? ns) {
		var parts = ImmutableList.CreateBuilder<string>();

		while(ns != null && !ns.IsGlobalNamespace) {
			parts.Insert(0, ns.Name);
			ns = ns.ContainingNamespace;
		}

		return parts.ToImmutable();
	}




	public record TypeParameter(string Name) : SourceModelType {
		protected override bool ContainsTypeParameter(string name) =>
			name == Name;

		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			if(!paramMapping.TryGetValue(Name, out var substitute)) {
				return this;
			}

			return substitute;
		}
	}

	public record Wildcard(string Name) : SourceModelType {
		protected override bool ContainsTypeParameter(string name) =>
			name == Name;

		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return this;
		}
	}

	public record NamedSymbol(ImmutableList<string> Namespace, string Name) : SourceModelType {
		public required ImmutableList<SourceModelType> TypeArguments { get; init; }
		public required bool IsEnum { get; init; }


		protected override bool ContainsTypeParameter(string name) =>
			TypeArguments.Any(tp => tp.ContainsTypeParameter(name));

		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return new NamedSymbol(Namespace, Name) {
				TypeArguments = TypeArguments.Select(tp => tp.Substitute(paramMapping)).ToImmutableList(),
				IsEnum = this.IsEnum,
			};
		}

		public override int GetHashCode() {
            int hash = 17;
            foreach (var ns in Namespace) {
                hash = hash * 31 + ns.GetHashCode();
            }
            hash = hash * 31 + Name.GetHashCode();
            foreach (var typeArg in TypeArguments) {
                hash = hash * 31 + typeArg.GetHashCode();
            }
            hash = hash * 31 + IsEnum.GetHashCode();
            return hash;
        }

		public virtual bool Equals(NamedSymbol? other) {
		    if (other is null) return false;
		    
		    return Namespace.SequenceEqual(other.Namespace) &&
		           Name == other.Name &&
		           TypeArguments.SequenceEqual(other.TypeArguments) &&
		           IsEnum == other.IsEnum;
		}

		public override string ToString() {
			var sb = new StringBuilder();
			sb.Append("NamedSymbol(");
			if(IsEnum) sb.Append("enum ");
			sb.Append(string.Join(".", Namespace));
			if(!Namespace.IsEmpty) {
				sb.Append(".");
			}
			sb.Append(Name);
			if(!TypeArguments.IsEmpty) {
				sb.Append("<");
				sb.Append(string.Join(", ", TypeArguments));
				sb.Append(">");
			}
			
			sb.Append(")");
			return sb.ToString();
		}
	}

	public record Nullable(SourceModelType Inner) : SourceModelType {
		protected override bool ContainsTypeParameter(string name) =>
			Inner.ContainsTypeParameter(name);

		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return new Nullable(Inner.Substitute(paramMapping));
		}
	}

	public record Array(SourceModelType Element) : SourceModelType {
		protected override bool ContainsTypeParameter(string name) =>
			Element.ContainsTypeParameter(name);

		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return new Array(Element.Substitute(paramMapping));
		}
	}

	public record Pointer(SourceModelType PointedAtType) : SourceModelType {
		protected override bool ContainsTypeParameter(string name) =>
			PointedAtType.ContainsTypeParameter(name);

		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return new Pointer(PointedAtType.Substitute(paramMapping));
		}
	}

}
