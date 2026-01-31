using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;

namespace ESExpr.Runtime.Codecs;

public abstract class MapCodecBase<K, V, TMap> : IESExprCodec<TMap>
	where TMap : IReadOnlyDictionary<K, V> {
	
	private protected MapCodecBase(IESExprCodec<K> keyCodec, IESExprCodec<V> valueCodec) {
		this.keyCodec = keyCodec;
		this.valueCodec = valueCodec;
	}
	
	internal const string MapConstructor = "map";
	
	private readonly IESExprCodec<K> keyCodec;
	private readonly IESExprCodec<V> valueCodec;
	
	protected abstract TMap CreateMap(IEnumerable<KeyValuePair<K, V>> items);
	
	public ESExprTagSet Tags { get; } = ESExprTagSet.Create([new ESExprTag.Constructor(MapConstructor)]);

	public bool IsEncodedEqual(TMap a, TMap b) =>
		a.Count == b.Count &&
			a.All(aKvp =>
				b.TryGetValue(aKvp.Key, out var bValue) &&
					valueCodec.IsEncodedEqual(aKvp.Value, bValue)
			);

	public Expr Encode(TMap value) {
		return new Expr.Constructor(
			MapConstructor,
			value.SelectMany(kvp => new[] { keyCodec.Encode(kvp.Key), valueCodec.Encode(kvp.Value) }).ToImmutableList(),
			ImmutableDictionary<string, Expr>.Empty
		);
	}

	public TMap Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Constructor(MapConstructor, var args, var kwargs)) {
			if(kwargs.Count != 0) {
				throw new DecodeException("Unexpected keyword arguments for map.", path.WithConstructor(MapConstructor));
			}

			if(args.Count % 2 != 0) {
				throw new DecodeException("Expected an even number of arguments for map.", path.WithConstructor(MapConstructor));
			}

			var items = new List<KeyValuePair<K, V>>(args.Count / 2);
			for(int i = 0; i < args.Count; i += 2) {
				items.Add(new KeyValuePair<K, V>(
					keyCodec.Decode(args[i], path.Append(MapConstructor, i)),
					valueCodec.Decode(args[i + 1], path.Append(MapConstructor, i + 1))
				));
			}

			return CreateMap(items);
		}
		else {
			throw new DecodeException("Expected a map constructor", path);
		}
	}
}

[ESExprOverrideCodec]
[ESExprTags(UnionWithTypeParameters = [ MapConstructor ])]
public class MapCodec<K, V> : MapCodecBase<K, V, Dictionary<K, V>> where K : notnull {
	public MapCodec(IESExprCodec<K> keyCodec, IESExprCodec<V> valueCodec) : base(keyCodec, valueCodec) {
	}

	protected override Dictionary<K, V> CreateMap(IEnumerable<KeyValuePair<K, V>> items) => items.ToDictionary(kvp => kvp.Key, kvp => kvp.Value);
}

[ESExprOverrideCodec]
[ESExprTags(Constructors = [ MapConstructor ])]
public class ImmutableMapCodec<K, V> : MapCodecBase<K, V, ImmutableDictionary<K, V>> where K : notnull {
	public ImmutableMapCodec(IESExprCodec<K> keyCodec, IESExprCodec<V> valueCodec) : base(keyCodec, valueCodec) {
	}
	
	protected override ImmutableDictionary<K, V> CreateMap(IEnumerable<KeyValuePair<K, V>> items) => items.ToImmutableDictionary();
}

[ESExprOverrideCodec]
[ESExprTags(Constructors = [ MapConstructor ])]
public class ReadOnlyMapCodec<K, V> : MapCodecBase<K, V, IReadOnlyDictionary<K, V>> where K : notnull {
	public ReadOnlyMapCodec(IESExprCodec<K> keyCodec, IESExprCodec<V> valueCodec) : base(keyCodec, valueCodec) {
	}

	protected override IReadOnlyDictionary<K, V> CreateMap(IEnumerable<KeyValuePair<K, V>> items) => items.ToImmutableDictionary();
}

[ESExprOverrideCodec]
[ESExprTags(Constructors = [ MapConstructor ])]
public class InterfaceImmutableMapCodec<K, V> : MapCodecBase<K, V, IImmutableDictionary<K, V>> where K : notnull {
	public InterfaceImmutableMapCodec(IESExprCodec<K> keyCodec, IESExprCodec<V> valueCodec) : base(keyCodec, valueCodec) {
	}
	
	protected override IImmutableDictionary<K, V> CreateMap(IEnumerable<KeyValuePair<K, V>> items) => items.ToImmutableDictionary();
}
