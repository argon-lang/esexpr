using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public abstract class GeneratorBaseValue;

public sealed class GeneratorDisposableValue : GeneratorBaseValue, IDisposable {
	public void Dispose() { }
}

public sealed record GeneratorReferenceBox<T>(T Value);

public static class GeneratorReferenceBox {
	[TypeClassInstance, ESExprTags(Constructors = ["reference-box"])]
	public static IESExprCodec<GeneratorReferenceBox<T>> Codec<T>() where T : class, IDisposable, new() =>
		new GeneratorEmptyBoxCodec<GeneratorReferenceBox<T>>("reference-box", () => new(new T()));
}

public sealed record GeneratorBaseBox<T>(T Value);

public static class GeneratorBaseBox {
	[TypeClassInstance, ESExprTags(Constructors = ["base-box"])]
	public static IESExprCodec<GeneratorBaseBox<T>> Codec<T>() where T : GeneratorBaseValue, new() =>
		new GeneratorEmptyBoxCodec<GeneratorBaseBox<T>>("base-box", () => new(new T()));
}

internal sealed class GeneratorEmptyBoxCodec<T>(string constructor, Func<T> create) : IESExprCodec<T> {
	public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Constructor(constructor)]);
	public bool IsEncodedEqual(T a, T b) => true;
	public Expr Encode(T value) => new Expr.Constructor(constructor, [], ImmutableDictionary<string, Expr>.Empty);
	public T Decode(Expr expr, DecodeFailurePath path) {
		if(expr is not Expr.Constructor(var name, var args, var kwargs) || name != constructor || args.Count != 0 || kwargs.Count != 0)
			throw new DecodeException("Expected an empty box", path);
		return create();
	}
}

public sealed record GeneratorConstrainedBox<T>(T Value);

public static class GeneratorConstrainedBox {
	[TypeClassInstance, ESExprTags(Constructors = ["constrained-box"])]
	public static IESExprCodec<GeneratorConstrainedBox<T>> Codec<T>(IESExprCodec<T> itemCodec) where T : unmanaged => new BoxCodec<T>(itemCodec);

	private sealed class BoxCodec<T>(IESExprCodec<T> itemCodec) : IESExprCodec<GeneratorConstrainedBox<T>> {
		public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Constructor("constrained-box")]);
		public bool IsEncodedEqual(GeneratorConstrainedBox<T> a, GeneratorConstrainedBox<T> b) => itemCodec.IsEncodedEqual(a.Value, b.Value);
		public Expr Encode(GeneratorConstrainedBox<T> value) => new Expr.Constructor("constrained-box", [itemCodec.Encode(value.Value)], ImmutableDictionary<string, Expr>.Empty);
		public GeneratorConstrainedBox<T> Decode(Expr expr, DecodeFailurePath path) {
			if(expr is not Expr.Constructor("constrained-box", var args, var kwargs) || args.Count != 1 || kwargs.Count != 0)
				throw new DecodeException("Expected one constrained-box argument", path);
			return new(itemCodec.Decode(args[0], path.Append("constrained-box", 0)));
		}
	}
}

public sealed record GeneratorPair<A, B>(A First, B Second);

public static class GeneratorPair {
	[TypeClassInstance, ESExprTags(Constructors = ["pair"])]
	public static IESExprCodec<GeneratorPair<T, T>> Codec<T>(IESExprCodec<T> itemCodec) where T : notnull => new PairCodec<T>(itemCodec);

	private sealed class PairCodec<T>(IESExprCodec<T> itemCodec) : IESExprCodec<GeneratorPair<T, T>> {
		public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Constructor("pair")]);
		public bool IsEncodedEqual(GeneratorPair<T, T> a, GeneratorPair<T, T> b) => itemCodec.IsEncodedEqual(a.First, b.First) && itemCodec.IsEncodedEqual(a.Second, b.Second);
		public Expr Encode(GeneratorPair<T, T> value) => new Expr.Constructor("pair", [itemCodec.Encode(value.First), itemCodec.Encode(value.Second)], ImmutableDictionary<string, Expr>.Empty);
		public GeneratorPair<T, T> Decode(Expr expr, DecodeFailurePath path) {
			if(expr is not Expr.Constructor("pair", var args, var kwargs) || args.Count != 2 || kwargs.Count != 0)
				throw new DecodeException("Expected two pair arguments", path);
			return new(itemCodec.Decode(args[0], path.Append("pair", 0)), itemCodec.Decode(args[1], path.Append("pair", 1)));
		}
	}
}

[ESExprCodec]
public sealed partial record GeneratorResolvedFields {
	public required GeneratorConstrainedBox<int> Box { get; init; }
	public required GeneratorPair<string, string> Pair { get; init; }
	public required MySimpleEnum Enum { get; init; }
	public required GeneratorWideFlags Flags { get; init; }
	public required GeneratorReferenceBox<GeneratorDisposableValue> Reference { get; init; }
	public required GeneratorBaseBox<GeneratorDisposableValue> Base { get; init; }
	[TypeClassInstance]
	public static partial IESExprCodec<GeneratorResolvedFields> Codec { get; }
}

public class GeneratorResolverFixtureTests : TestBase {
	[Test]
	public void ConstrainedAndRepeatedParameterFactoriesResolveAlongsideIntrinsicTags() {
		AssertCodecMatch(GeneratorResolvedFields.Codec,
			new Expr.Constructor("generator-resolved-fields", [
				new Expr.Constructor("constrained-box", [new Expr.Int(7)], ImmutableDictionary<string, Expr>.Empty),
				new Expr.Constructor("pair", [new Expr.Str("a"), new Expr.Str("b")], ImmutableDictionary<string, Expr>.Empty),
				new Expr.Str("my-custom-name"), new Expr.Int(0),
				new Expr.Constructor("reference-box", [], ImmutableDictionary<string, Expr>.Empty),
				new Expr.Constructor("base-box", [], ImmutableDictionary<string, Expr>.Empty),
			], ImmutableDictionary<string, Expr>.Empty),
			new GeneratorResolvedFields {
				Box = new(7), Pair = new("a", "b"), Enum = MySimpleEnum.CustomName,
				Flags = new GeneratorWideFlags { High = false, Mode = GeneratorWideFlags.Choice.None },
				Reference = new(new GeneratorDisposableValue()), Base = new(new GeneratorDisposableValue()),
			});
	}

	[Test]
	public void ConstrainedFactoriesConstructTheResolvedConcreteType() {
		var empty = ImmutableDictionary<string, Expr>.Empty;
		Assert.That(GeneratorReferenceBox.Codec<GeneratorDisposableValue>().Decode(new Expr.Constructor("reference-box", [], empty)).Value,
			Is.TypeOf<GeneratorDisposableValue>());
		Assert.That(GeneratorBaseBox.Codec<GeneratorDisposableValue>().Decode(new Expr.Constructor("base-box", [], empty)).Value,
			Is.TypeOf<GeneratorDisposableValue>());
	}
}
