using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using System.Reflection;
using System.Text.RegularExpressions;

namespace ESExpr.Runtime;

public partial class SimpleEnumCodec<T> : IESExprCodec<T>
	where T : struct, Enum
{
	public static SimpleEnumCodec<T> Instance { get; } = new SimpleEnumCodec<T>();
	
	private SimpleEnumCodec() {
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

	
	public ISet<ESExprTag> Tags => (HashSet<ESExprTag>) [new ESExprTag.Str()];

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


	private static string GetConstructorName(T value) {
		var name = Enum.GetName(value);
		if(name == null) {
			throw new Exception("Could not get enum value name");
		}

		if(typeof(T).GetField(name)?.GetCustomAttribute<ConstructorAttribute>() is { } ctor) {
			return ctor.Name;
		}
		else {
			return NameToKebabCase(name);
		}
	}

	[GeneratedRegex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])_(?=[0-9])", RegexOptions.CultureInvariant)]
	private static partial Regex NameSeparatorRegex();

	private static string NameToKebabCase(string name) =>
		string.Join(
			"-",
			NameSeparatorRegex()
				.Split(name)
				.Select(s => s.ToLowerInvariant())
		);
	
	
}
