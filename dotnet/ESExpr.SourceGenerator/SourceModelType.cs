using System;
using System.Collections.Immutable;
using System.Linq;
using System.Text;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

internal closed record SourceModelType {
	private SourceModelType() { }


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
		NamedSymbolParent parent;
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




	public record TypeParameter(string Name) : SourceModelType;

	public union NamedSymbolParent(NamespaceSymbolParent, NamedSymbol) : IEquatable<NamedSymbolParent> {
		public bool IsEmpty => this switch {
			NamespaceSymbolParent ns => ns.Namespace.IsEmpty,
			NamedSymbol => false,
		};

		public bool Equals(NamedSymbolParent other) => Equals(Value, other.Value);
		public override bool Equals(object? obj) => obj is NamedSymbolParent other && Equals(other);
		public override int GetHashCode() => Value?.GetHashCode() ?? 0;
		public override string ToString() => Value?.ToString() ?? "";
	}

	public sealed record NamespaceSymbolParent(ImmutableList<string> Namespace) {

		public override int GetHashCode() {
			int hash = 17;
			foreach(var ns in Namespace) {
				hash = hash * 31 + ns.GetHashCode();
			}

			return hash;
		}

		public bool Equals(NamespaceSymbolParent? other) =>
			other is not null && Namespace.SequenceEqual(other.Namespace);

		public override string ToString() {
			return string.Join(".", Namespace);
		}
	}

	public sealed record NamedSymbol(NamedSymbolParent Parent, string Name) : SourceModelType {
		public required ImmutableList<SourceModelType> TypeArguments { get; init; }
		public required bool IsEnum { get; init; }


		public override int GetHashCode() {
			int hash = 17;
			hash = hash * 31 + Parent.GetHashCode();
			hash = hash * 31 + Name.GetHashCode();
			foreach(var typeArg in TypeArguments) {
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

		public override string ToString() {
			var sb = new StringBuilder();
			sb.Append("NamedSymbol(");
			if(IsEnum) sb.Append("enum ");
			sb.Append(Parent);
			if(!Parent.IsEmpty) {
				sb.Append('.');
			}
			sb.Append(Name);
			if(!TypeArguments.IsEmpty) {
				sb.Append('<');
				sb.Append(string.Join(", ", TypeArguments));
				sb.Append('>');
			}

			sb.Append(')');
			return sb.ToString();
		}
	}

	public record Nullable(SourceModelType Inner) : SourceModelType;

	public record Array(SourceModelType Element) : SourceModelType;

	public record Pointer(SourceModelType PointedAtType) : SourceModelType;
}
