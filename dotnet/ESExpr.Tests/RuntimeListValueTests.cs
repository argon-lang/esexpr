using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class RuntimeListValueTests {
	[Test]
	public void VListUpdatesPreserveValueSemantics() {
		VList<int> original = [1, 2, 1];
		Assert.That(default(VList<int>).Count, Is.Zero);
		Assert.That(original.IndexOf(1), Is.Zero);
		Assert.That(original.IndexOf(1, 1, 2, null), Is.EqualTo(2));
		Assert.That(original.LastIndexOf(1), Is.EqualTo(2));
		Assert.That(original.LastIndexOf(1, 1, 2, null), Is.Zero);
		Assert.That(original.Insert(1, 3), Is.EqualTo(new[] { 1, 3, 2, 1 }));
		Assert.That(original.InsertRange(1, new[] { 3, 4 }), Is.EqualTo(new[] { 1, 3, 4, 2, 1 }));
		Assert.That(original.Remove(1), Is.EqualTo(new[] { 2, 1 }));
		Assert.That(original.RemoveAll(x => x == 1), Is.EqualTo(new[] { 2 }));
		Assert.That(original.RemoveAt(1), Is.EqualTo(new[] { 1, 1 }));
		Assert.That(original.RemoveRange(new[] { 1, 2 }), Is.EqualTo(new[] { 1 }));
		Assert.That(original.RemoveRange(1, 2), Is.EqualTo(new[] { 1 }));
		Assert.That(original.Replace(2, 3), Is.EqualTo(new[] { 1, 3, 1 }));
		Assert.That(original.SetItem(1, 3), Is.EqualTo(new[] { 1, 3, 1 }));
		Assert.That(original.AddRange(new[] { 3 }), Is.EqualTo(new[] { 1, 2, 1, 3 }));
		Assert.That(original.Clear().Count, Is.Zero);
		Assert.That(original, Is.EqualTo(new[] { 1, 2, 1 }));
		Assert.That(original.Equals((object)"other"), Is.False);
		Assert.That(original == VList.Create<int>(new[] { 1, 2, 1 }), Is.True);
		Assert.That(original != VList<int>.Empty, Is.True);
		Assert.That(original <= original.Add(3), Is.True);
		Assert.That(original >= VList<int>.Empty, Is.True);
		Assert.That(VList.Codec(IESExprCodec.IntCodec).Decode(VList.Codec(IESExprCodec.IntCodec).Encode(original)), Is.EqualTo(original));
		Assert.That(VList.VListCodec(IESExprCodec.IntCodec).EncodeVararg(original), Is.EqualTo(new Expr[] { new Expr.Int(1), new Expr.Int(2), new Expr.Int(1) }));
	}

	[Test]
	public void VListImmutableInterfaceUpdatesReturnIndependentLists() {
		IImmutableList<int> original = new VList<int>(ImmutableList.Create(1, 2, 1));
		Assert.That(original.AddRange(new[] { 3 }), Is.EqualTo(new[] { 1, 2, 1, 3 }));
		Assert.That(original.Insert(1, 3), Is.EqualTo(new[] { 1, 3, 2, 1 }));
		Assert.That(original.InsertRange(1, new[] { 3 }), Is.EqualTo(new[] { 1, 3, 2, 1 }));
		Assert.That(original.Remove(1, null), Is.EqualTo(new[] { 2, 1 }));
		Assert.That(original.RemoveAll(x => x == 1), Is.EqualTo(new[] { 2 }));
		Assert.That(original.RemoveAt(1), Is.EqualTo(new[] { 1, 1 }));
		Assert.That(original.RemoveRange(new[] { 1, 2 }, null), Is.EqualTo(new[] { 1 }));
		Assert.That(original.RemoveRange(1, 2), Is.EqualTo(new[] { 1 }));
		Assert.That(original.Replace(2, 3, null), Is.EqualTo(new[] { 1, 3, 1 }));
		Assert.That(original.SetItem(1, 3), Is.EqualTo(new[] { 1, 3, 1 }));
		Assert.That(original.Clear().Count, Is.Zero);
		Assert.That(original, Is.EqualTo(new[] { 1, 2, 1 }));
	}

	[Test]
	public void SimpleEnumRejectsUnknownValuesAndWrongTypes() {
		var codec = IESExprCodec.EnumCodec<MySimpleEnum>();
		var path = new DecodeFailurePath.Constructor("outer");
		Assert.That(codec.Tags.Contains(new ESExprTag.Str()), Is.True);
		Assert.That(codec.IsEncodedEqual(MySimpleEnum.CustomName, MySimpleEnum.OtherNameHere), Is.False);
		Assert.Throws<InvalidOperationException>(() => codec.Encode((MySimpleEnum)999));
		foreach(var expr in new Expr[] { new Expr.Str("unknown"), new Expr.Int(1) })
			Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(expr, path))!.Path, Is.EqualTo(path));
	}
}
