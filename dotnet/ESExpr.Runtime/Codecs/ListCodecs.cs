using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;

namespace ESExpr.Runtime.Codecs;

public abstract class ListCodecBase<T, TList> : IESExprCodec<TList>
	where TList : IEnumerable<T> {
	
	
	private protected ListCodecBase(IESExprCodec<T> itemCodec) {
		this.itemCodec = itemCodec;
	}
	
	internal const string ListConstructor = "list";
	
	private readonly IESExprCodec<T> itemCodec;
	
	protected abstract TList CreateList(IEnumerable<T> items);
	protected abstract int GetCount(TList list);
	
	
	public ISet<ESExprTag> Tags { get; } = new HashSet<ESExprTag>([new ESExprTag.Constructor(ListConstructor)]);


	public bool IsEncodedEqual(TList a, TList b) =>
		GetCount(a) == GetCount(b) &&
			a.Zip(b, (aItem, bItem) => itemCodec.IsEncodedEqual(aItem, bItem)).All(x => x);

	public Expr Encode(TList value) {
		return new Expr.Constructor(
			ListConstructor,
			value.Select(itemCodec.Encode).ToImmutableList(),
			ImmutableDictionary<string, Expr>.Empty
		);
	}

	public TList Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Constructor(ListConstructor, var args, var kwargs)) {
			if(kwargs.Count != 0) {
				throw new DecodeException("Unexpected keyword arguments for list.", path.WithConstructor(ListConstructor));
			}

			return CreateList(args.Select((arg, i) => itemCodec.Decode(arg, path.Append(ListConstructor, i))));
		}
		else {
			throw new DecodeException("Expected a list constructor", path);
		}
	}
}

[ESExprOverrideCodec]
public class ListCodec<T> : ListCodecBase<T, List<T>> {
	public ListCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override List<T> CreateList(IEnumerable<T> items) => items.ToList();
	protected override int GetCount(List<T> list) => list.Count;
}

[ESExprOverrideCodec]
public class ImmutableListCodec<T> : ListCodecBase<T, ImmutableList<T>> {
	public ImmutableListCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}
	
	protected override ImmutableList<T> CreateList(IEnumerable<T> items) => items.ToImmutableList();
	protected override int GetCount(ImmutableList<T> list) => list.Count;
}

[ESExprOverrideCodec]
public class InterfaceListCodec<T> : ListCodecBase<T, IList<T>> {
	public InterfaceListCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IList<T> CreateList(IEnumerable<T> items) => items.ToList();
	protected override int GetCount(IList<T> list) => list.Count;
}

[ESExprOverrideCodec]
public class ReadOnlyListCodec<T> : ListCodecBase<T, IReadOnlyList<T>> {
	public ReadOnlyListCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IReadOnlyList<T> CreateList(IEnumerable<T> items) => items.ToImmutableList();
	protected override int GetCount(IReadOnlyList<T> list) => list.Count;
}

[ESExprOverrideCodec]
public class InterfaceImmutableListCodec<T> : ListCodecBase<T, IImmutableList<T>> {
	public InterfaceImmutableListCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}
	
	protected override IImmutableList<T> CreateList(IEnumerable<T> items) => items.ToImmutableList();
	protected override int GetCount(IImmutableList<T> list) => list.Count;
}

public abstract class ListVarargCodecBase<T, TList> : IVarargCodec<TList>
	where TList : IEnumerable<T> {
	
	
	private protected ListVarargCodecBase(IESExprCodec<T> itemCodec) {
		this.itemCodec = itemCodec;
	}
	
	private readonly IESExprCodec<T> itemCodec;
	
	protected abstract TList CreateList(IEnumerable<T> items);
	protected abstract int GetCount(TList list);
	
	
	public ISet<ESExprTag> ElementTags { get; } = new HashSet<ESExprTag>([new ESExprTag.Constructor(ListCodecBase<T, TList>.ListConstructor)]);


	public bool IsEncodedEqual(TList a, TList b) =>
		GetCount(a) == GetCount(b) &&
			a.Zip(b, (aItem, bItem) => itemCodec.IsEncodedEqual(aItem, bItem)).All(x => x);

	public IEnumerable<Expr> EncodeVararg(TList value) {
		return value.Select(itemCodec.Encode);
	}

	public TList DecodeVararg(ref SliceList<Expr> value, Func<int, DecodeFailurePath> pathBuilder) {
		var res = CreateList(
			value
				.TakeWhile(expr => itemCodec.Tags.Contains(expr.Tag))
				.Select((expr, i) => itemCodec.Decode(expr, pathBuilder(i)))
		);

		value = value.Slice(GetCount(res));

		return res;
	}

	private IEnumerable<T> DecodeItems(Queue<Expr> queue, Func<int, DecodeFailurePath> pathBuilder) {
		while(queue.TryPeek(out var item)) {
			if(!itemCodec.Tags.Contains(item.Tag)) {
				break;
			}
			
			queue.Dequeue();
			yield return itemCodec.Decode(item, pathBuilder(0));
		}
	}
}

[ESExprOverrideCodec]
public class ListVarargCodec<T> : ListVarargCodecBase<T, List<T>> {
	public ListVarargCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override List<T> CreateList(IEnumerable<T> items) => items.ToList();
	protected override int GetCount(List<T> list) => list.Count;
}

[ESExprOverrideCodec]
public class ImmutableListVarargCodec<T> : ListVarargCodecBase<T, ImmutableList<T>> {
	public ImmutableListVarargCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}
	
	protected override ImmutableList<T> CreateList(IEnumerable<T> items) => items.ToImmutableList();
	protected override int GetCount(ImmutableList<T> list) => list.Count;
}

[ESExprOverrideCodec]
public class InterfaceListVarargCodec<T> : ListVarargCodecBase<T, IList<T>> {
	public InterfaceListVarargCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IList<T> CreateList(IEnumerable<T> items) => items.ToList();
	protected override int GetCount(IList<T> list) => list.Count;
}

[ESExprOverrideCodec]
public class ReadOnlyListVarargCodec<T> : ListVarargCodecBase<T, IReadOnlyList<T>> {
	public ReadOnlyListVarargCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IReadOnlyList<T> CreateList(IEnumerable<T> items) => items.ToImmutableList();
	protected override int GetCount(IReadOnlyList<T> list) => list.Count;
}


