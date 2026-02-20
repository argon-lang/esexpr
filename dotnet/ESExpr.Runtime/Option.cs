using System;
using System.Collections.Generic;
using System.Diagnostics.CodeAnalysis;

namespace ESExpr.Runtime;

public struct Option<T> : IEquatable<Option<T>> {
	public Option(T value) {
		hasValue = true;
		this.value = value;
	}

	public static Option<T> Empty => default;

	private bool hasValue;
	private T value;

	public bool IsSome => hasValue;

	public bool TryGetValue([MaybeNullWhen(false)] out T value) {
		value = hasValue ? this.value : default;
		return hasValue;
	}

	public bool Equals(Option<T> other) {
		if(hasValue && other.hasValue) {
			return EqualityComparer<T>.Default.Equals(value, other.value);
		}
		else {
			return true;
		}
	}

	public override bool Equals([NotNullWhen(true)] object? obj) =>
		obj is Option<T> other && Equals(other);

	public override int GetHashCode() {
		if(hasValue) {
			return value?.GetHashCode() ?? 0;
		}
		else {
			return 0;
		}
	}

	public static bool operator ==(Option<T> left, Option<T> right) => left.Equals(right);

	public static bool operator !=(Option<T> left, Option<T> right) => !(left == right);

	public override string ToString() {
		if(hasValue) {
			return $"Some({value})";
		}
		else {
			return "None";
		}
	}
}

public static class Option {
	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Null ], UnionWithTypeParameters = [ nameof(T) ])]
	public static IESExprCodec<Option<T>> Codec<T>(IESExprCodec<T> elementCodec) =>
		new OptionCodec<T>(elementCodec);
	
	private sealed class OptionCodec<T> : IESExprCodec<Option<T>> {
		public OptionCodec(IESExprCodec<T> elementCodec) {
			this.elementCodec = elementCodec;
		}

		private readonly IESExprCodec<T> elementCodec;

		public ESExprTagSet Tags => elementCodec.Tags.Add(new ESExprTag.Null());

		public bool IsEncodedEqual(Option<T> a, Option<T> b) {
			if(!a.TryGetValue(out var aValue)) {
				return !b.IsSome;
			}

			if(!b.TryGetValue(out var bValue)) {
				return false;
			}
			
			return elementCodec.IsEncodedEqual(aValue, bValue);
		}

		public Expr Encode(Option<T> value) {
			if(value.TryGetValue(out var v)) {
				var element = elementCodec.Encode(v);
				if(element is Expr.Null(var level)) {
					return new Expr.Null(level + 1);
				}
				else {
					return element;
				}
			}
			else {
				return new Expr.Null(0);
			}
		}

		public Option<T> Decode(Expr expr, DecodeFailurePath path) {
			if(expr is Expr.Null(var level)) {
				if(level > 0) {
					return new Option<T>(elementCodec.Decode(new Expr.Null(level - 1), path));
				}
				else {
					return Option<T>.Empty;
				}
			}
			else {
				return new Option<T>(elementCodec.Decode(expr, path));
			}
		}
	}
	
	
	
	[TypeClassInstance]
	public static IOptionalValueCodec<Option<T>, T> OptionOptionalValueCodec<T>(IESExprCodec<T> itemCodec) =>
		new OptionalValueCodec<T>(itemCodec);
	
	
	private sealed class OptionalValueCodec<T> : IOptionalValueCodec<Option<T>, T> {
		public OptionalValueCodec(IESExprCodec<T> elementCodec) {
			this.elementCodec = elementCodec;
		}

		private readonly IESExprCodec<T> elementCodec;

		public ESExprTagSet ElementTags => elementCodec.Tags;

		public bool IsEncodedEqual(Option<T> a, Option<T> b) {
			if(!a.TryGetValue(out var aValue)) {
				return !b.IsSome;
			}

			if(!b.TryGetValue(out var bValue)) {
				return false;
			}
			
			return elementCodec.IsEncodedEqual(aValue, bValue);
		}

		public Expr? EncodeOptional(Option<T> value) {
			if(value.TryGetValue(out var v)) {
				return elementCodec.Encode(v);
			}
			else {
				return null;
			}
		}

		public Option<T> DecodeOptional(Expr? value, DecodeFailurePath path) {
			if(value == null) {
				return Option<T>.Empty;
			}
			else {
				return new Option<T>(elementCodec.Decode(value, path));
			}
		}
	}
}
