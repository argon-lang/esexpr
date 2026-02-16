using System.Collections.Generic;
using System.Collections.Immutable;
using ESExpr.Runtime;
using ESExpr.Runtime.Codecs;
using NUnit.Framework;

namespace ESExpr.Tests;

public class SetMapCodecTests : TestBase {
	[Test]
	public void HashSetCodecTest() {
		var codec = IESExprCodec.SetCodec(IESExprCodec.IntCodec);
		var set = new HashSet<int> { 1, 2, 3 };
		var expr = new Expr.Constructor("set", [new Expr.Int(1), new Expr.Int(2), new Expr.Int(3)], ImmutableDictionary<string, Expr>.Empty);
		
		var encoded = codec.Encode(set);
		Assert.That(encoded, Is.InstanceOf<Expr.Constructor>());
		var constr = (Expr.Constructor)encoded;
		Assert.That(constr.constructor, Is.EqualTo("set"));
		Assert.That(constr.args, Has.Count.EqualTo(3));
		Assert.That(constr.args, Contains.Item(new Expr.Int(1)));
		Assert.That(constr.args, Contains.Item(new Expr.Int(2)));
		Assert.That(constr.args, Contains.Item(new Expr.Int(3)));

		var decoded = codec.Decode(expr, new DecodeFailurePath.Current());
		Assert.That(decoded, Is.EquivalentTo(set));
	}

	[Test]
	public void DictionaryCodecTest() {
		var codec = IESExprCodec.MapCodec(IESExprCodec.IntCodec, IESExprCodec.StrCodec);
		var map = new Dictionary<int, string> { { 1, "one" }, { 2, "two" } };
		
		var encoded = codec.Encode(map);
		Assert.That(encoded, Is.InstanceOf<Expr.Constructor>());
		var constr = (Expr.Constructor)encoded;
		Assert.That(constr.constructor, Is.EqualTo("map"));
		Assert.That(constr.args, Has.Count.EqualTo(4));
		
		// MapCodec encodes as [key1, value1, key2, value2, ...]
		var decoded = codec.Decode(encoded, new DecodeFailurePath.Current());
		Assert.That(decoded, Is.EquivalentTo(map));
	}

	[Test]
	public void ImmutableHashSetCodecTest() {
		var codec = IESExprCodec.ImmutableHashSetCodec(IESExprCodec.IntCodec);
		var set = ImmutableHashSet.Create(1, 2);
		var encoded = codec.Encode(set);
		var decoded = codec.Decode(encoded, new DecodeFailurePath.Current());
		Assert.That(decoded, Is.EquivalentTo(set));
	}

	[Test]
	public void ImmutableMapCodecTest() {
		var codec = IESExprCodec.ImmutableMapCodec(IESExprCodec.IntCodec, IESExprCodec.StrCodec);
		var map = ImmutableDictionary.CreateRange(new[] { 
			new KeyValuePair<int, string>(1, "one"),
			new KeyValuePair<int, string>(2, "two")
		});
		var encoded = codec.Encode(map);
		var decoded = codec.Decode(encoded, new DecodeFailurePath.Current());
		Assert.That(decoded, Is.EquivalentTo(map));
	}
}
