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

	public sealed class Codec : IESExprCodec<Option<T>> {
		public Codec(IESExprCodec<T> elementCodec) {
			this.elementCodec = elementCodec;
		}
		
		private readonly IESExprCodec<T> elementCodec;

		public ISet<ESExprTag> Tags {
			get {
				var tags = new HashSet<ESExprTag>(elementCodec.Tags);
				tags.Add(new ESExprTag.Null());
				return tags;
			}
		}

		public Expr Encode(Option<T> value) {
			if(value.TryGetValue(out var v)) {
				return elementCodec.Encode(v);
			}
			else {
				return new Expr.Null();
			}
		}

		public Option<T> Decode(Expr expr, DecodeFailurePath path) {
			if(expr is Expr.Null) {
				return Empty;
			}
			else {
				return new Option<T>(elementCodec.Decode(expr, path));
			}
		}
	}

	public sealed class OptionalValueCodec : IOptionalValueCodec<Option<T>> {
		public OptionalValueCodec(IESExprCodec<T> elementCodec) {
			this.elementCodec = elementCodec;
		}
		
		private readonly IESExprCodec<T> elementCodec;

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
				return Empty;
			}
			else {
				return new Option<T>(elementCodec.Decode(value, path));
			}
		}
	}
}
