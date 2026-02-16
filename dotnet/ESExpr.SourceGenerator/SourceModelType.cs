using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using System.Text;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

internal abstract record SourceModelType {
	private SourceModelType() { }


	protected abstract bool ContainsTypeParameter(string name);
	public abstract SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping);


	public static SourceModelType FromSymbol(ITypeSymbol t) {
		switch(t) {
			case ITypeParameterSymbol tp:
				return new TypeParameter(tp.Name);

			case INamedTypeSymbol named: {
				SourceModelType res = FromNamedSymbol(named);
				
				if(named.NullableAnnotation == NullableAnnotation.Annotated) {
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

	private static NamedSymbol FromNamedSymbol(INamedTypeSymbol named) {
		INamedSymbolParent parent;
		if(named.ContainingType is { } containingType) {
			parent = FromNamedSymbol(containingType);
		}
		else {
			parent = new NamespaceSymbolParent(GetNamespaceFromSymbol(named.ContainingNamespace));
		}
				
		var res = new NamedSymbol(parent, named.Name) {
			TypeArguments = named.TypeArguments.Select(FromSymbol).ToImmutableList(),
			IsEnum = named.TypeKind == TypeKind.Enum,
		};

		return res;
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

	public interface INamedSymbolParent : IEquatable<INamedSymbolParent?> {
		bool IsEmpty { get; }
		INamedSymbolParent Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping);
	}

	public sealed record NamespaceSymbolParent(ImmutableList<string> Namespace) : INamedSymbolParent {
		public bool ContainsTypeParameter(string name) => false;
		

		public INamedSymbolParent Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) =>
			this;
		
		public bool IsEmpty => Namespace.IsEmpty;

		public override int GetHashCode() {
			int hash = 17;
			foreach (var ns in Namespace) {
				hash = hash * 31 + ns.GetHashCode();
			}
			
			return hash;
		}

		public bool Equals(NamespaceSymbolParent? other) =>
			other is not null && Namespace.SequenceEqual(other.Namespace);

		public bool Equals(INamedSymbolParent? other) =>
			Equals(other as NamespaceSymbolParent);

		public override string ToString() {
			return string.Join(".", Namespace);
		}
	}

	public sealed record NamedSymbol(INamedSymbolParent Parent, string Name) : SourceModelType, INamedSymbolParent {
		public required ImmutableList<SourceModelType> TypeArguments { get; init; }
		public required bool IsEnum { get; init; }


		protected override bool ContainsTypeParameter(string name) =>
			TypeArguments.Any(tp => tp.ContainsTypeParameter(name));

		public override SourceModelType Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) =>
			SubstituteImpl(paramMapping);

		INamedSymbolParent INamedSymbolParent.Substitute(IReadOnlyDictionary<string, SourceModelType> paramMapping) =>
			SubstituteImpl(paramMapping);

		bool INamedSymbolParent.IsEmpty => false;

		private NamedSymbol SubstituteImpl(IReadOnlyDictionary<string, SourceModelType> paramMapping) {
			return new NamedSymbol(Parent.Substitute(paramMapping), Name) {
				TypeArguments = TypeArguments.Select(tp => tp.Substitute(paramMapping)).ToImmutableList(),
				IsEnum = this.IsEnum,
			};
		}

		public override int GetHashCode() {
            int hash = 17;
            hash = hash * 31 + Parent.GetHashCode();
            hash = hash * 31 + Name.GetHashCode();
            foreach (var typeArg in TypeArguments) {
                hash = hash * 31 + typeArg.GetHashCode();
            }
            hash = hash * 31 + IsEnum.GetHashCode();
            return hash;
        }

		public bool Equals(NamedSymbol? other) {
		    if(other is null) return false;
		    
		    return Parent.Equals(other.Parent) &&
		           Name == other.Name &&
		           TypeArguments.SequenceEqual(other.TypeArguments) &&
		           IsEnum == other.IsEnum;
		}

		public bool Equals(INamedSymbolParent? other) =>
			Equals(other as NamedSymbol);

		public override string ToString() {
			var sb = new StringBuilder();
			sb.Append("NamedSymbol(");
			if(IsEnum) sb.Append("enum ");
			sb.Append(Parent);
			if(!Parent.IsEmpty) {
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
