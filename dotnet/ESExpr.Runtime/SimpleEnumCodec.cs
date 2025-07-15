using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using System.Reflection;
using System.Text.RegularExpressions;
using static ESExpr.Runtime.NameUtils;

namespace ESExpr.Runtime;

public partial class SimpleEnumCodec<T> : IESExprCodec<T>
	where T : struct, Enum {
	public static SimpleEnumCodec<T> Instance { get; } = new SimpleEnumCodec<T>();

	private SimpleEnumCodec() {
		if(typeof(T).GetCustomAttribute<ESExprCodecAttribute>() == null) {
			throw new InvalidOperationException($"Type {typeof(T)} does not have the {nameof(ESExprCodecAttribute)} attribute");
		}

		strLookup = Enum.GetValues<T>()
			.ToImmutableDictionary(
				value => value,
				GetConstructorName
			);

		valueLookup = strLookup.ToImmutableDictionary(
			kvp => kvp.Value,
			kvp => kvp.Key
		);
	}

	private readonly ImmutableDictionary<string, T> valueLookup;
	private readonly ImmutableDictionary<T, string> strLookup;


	public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Str()]);

	public bool IsEncodedEqual(T a, T b) => EqualityComparer<T>.Default.Equals(a, b);

	public Expr Encode(T value) {
		if(strLookup.TryGetValue(value, out var s)) {
			return new Expr.Str(s);
		}
		else {
			throw new InvalidOperationException($"Unknown enum value: {value}");
		}
	}

	public T Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Str(var s)) {
			if(valueLookup.TryGetValue(s, out var value)) {
				return value;
			}
			else {
				throw new DecodeException("Invalid simple enum value", path);
			}
		}
		else {
			throw new DecodeException("Simple enum value must be a string", path);
		}
	}





}
