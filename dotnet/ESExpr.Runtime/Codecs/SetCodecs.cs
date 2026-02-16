using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;

namespace ESExpr.Runtime.Codecs;

public abstract class SetCodecBase<T, TSet> : IESExprCodec<TSet>
	where TSet : IReadOnlyCollection<T> {
	
	private protected SetCodecBase(IESExprCodec<T> itemCodec) {
		this.itemCodec = itemCodec;
	}
	
	internal const string SetConstructor = "set";
	
	private readonly IESExprCodec<T> itemCodec;
	
	protected abstract TSet CreateSet(IEnumerable<T> items);
	
	public ESExprTagSet Tags { get; } = ESExprTagSet.Create([new ESExprTag.Constructor(SetConstructor)]);

	public abstract bool IsEncodedEqual(TSet a, TSet b);

	public Expr Encode(TSet value) {
		return new Expr.Constructor(
			SetConstructor,
			value.Select(itemCodec.Encode).ToImmutableList(),
			ImmutableDictionary<string, Expr>.Empty
		);
	}

	public TSet Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Constructor(SetConstructor, var args, var kwargs)) {
			if(kwargs.Count != 0) {
				throw new DecodeException("Unexpected keyword arguments for set.", path.WithConstructor(SetConstructor));
			}

			return CreateSet(args.Select((arg, i) => itemCodec.Decode(arg, path.Append(SetConstructor, i))));
		}
		else {
			throw new DecodeException("Expected a set constructor", path);
		}
	}
}

internal class SetCodec<T> : SetCodecBase<T, HashSet<T>> {
	public SetCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	public override bool IsEncodedEqual(HashSet<T> a, HashSet<T> b) =>
		a.SetEquals(b);

	protected override HashSet<T> CreateSet(IEnumerable<T> items) => items.ToHashSet();
}

internal class ImmutableHashSetCodec<T> : SetCodecBase<T, ImmutableHashSet<T>> {
	public ImmutableHashSetCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	public override bool IsEncodedEqual(ImmutableHashSet<T> a, ImmutableHashSet<T> b) =>
		a.SetEquals(b);
	
	protected override ImmutableHashSet<T> CreateSet(IEnumerable<T> items) => items.ToImmutableHashSet();
}

internal class ReadOnlySetCodec<T> : SetCodecBase<T, IReadOnlySet<T>> {
	public ReadOnlySetCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	public override bool IsEncodedEqual(IReadOnlySet<T> a, IReadOnlySet<T> b) =>
		a.SetEquals(b);

	protected override IReadOnlySet<T> CreateSet(IEnumerable<T> items) => items.ToImmutableHashSet();
}

internal class InterfaceImmutableSetCodec<T> : SetCodecBase<T, IImmutableSet<T>> {
	public InterfaceImmutableSetCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	public override bool IsEncodedEqual(IImmutableSet<T> a, IImmutableSet<T> b) =>
		a.SetEquals(b);
	
	protected override IImmutableSet<T> CreateSet(IEnumerable<T> items) => items.ToImmutableHashSet();
}
