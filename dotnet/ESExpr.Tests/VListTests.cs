using System.Collections.Immutable;
using ESExpr.Runtime;
using NUnit.Framework;

namespace ESExpr.Tests;

public class VListTests {
    [Test]
    public void VListBasicTest() {
        var list = VList<int>.Empty.Add(1).Add(2).Add(3);
        Assert.That(list.Count, Is.EqualTo(3));
        Assert.That(list[0], Is.EqualTo(1));
        Assert.That(list[1], Is.EqualTo(2));
        Assert.That(list[2], Is.EqualTo(3));

        var list2 = list.Remove(2, null);
        Assert.That(list2.Count, Is.EqualTo(2));
        Assert.That(list2[0], Is.EqualTo(1));
        Assert.That(list2[1], Is.EqualTo(3));
        
        Assert.That(list.Count, Is.EqualTo(3)); // list should be immutable
    }

    [Test]
    public void VListEqualityTest() {
        var list1 = VList<int>.Empty.Add(1).Add(2);
        var list2 = VList<int>.Empty.Add(1).Add(2);
        var list3 = VList<int>.Empty.Add(1).Add(3);

        Assert.That(list1, Is.EqualTo(list2));
        Assert.That(list1, Is.Not.EqualTo(list3));
        Assert.That(list1.GetHashCode(), Is.EqualTo(list2.GetHashCode()));
    }

    [Test]
    public void VListComparisonTest() {
        var list1 = VList<int>.Empty.Add(1).Add(2);
        var list2 = VList<int>.Empty.Add(1).Add(2);
        var list3 = VList<int>.Empty.Add(1).Add(3);
        var list4 = VList<int>.Empty.Add(1);

        Assert.That(list1.CompareTo(list2), Is.EqualTo(0));
        Assert.That(list1.CompareTo(list3), Is.LessThan(0));
        Assert.That(list3.CompareTo(list1), Is.GreaterThan(0));
        Assert.That(list1.CompareTo(list4), Is.GreaterThan(0));
        Assert.That(list4.CompareTo(list1), Is.LessThan(0));
        
        Assert.That(list1 < list3, Is.True);
        Assert.That(list3 > list1, Is.True);
    }

    [Test]
    public void VListCodecTest() {
        var codec = IESExprCodec.VListCodec(IESExprCodec.IntCodec);
        var list = VList<int>.Empty.Add(1).Add(2).Add(3);
        
        var encoded = codec.Encode(list);
        var decoded = codec.Decode(encoded, new DecodeFailurePath.Current());
        
        Assert.That(decoded, Is.EqualTo(list));
    }

    [Test]
    public void VListInterfaceImplementationTest() {
        VList<int> list = VList<int>.Empty.Add(1);
        IImmutableList<int> immutableList = list;
        
        var list2 = immutableList.Add(2);
        Assert.That(list2, Is.InstanceOf<VList<int>>());
        Assert.That(list2.Count, Is.EqualTo(2));
        Assert.That(list2[0], Is.EqualTo(1));
        Assert.That(list2[1], Is.EqualTo(2));
    }

    [Test]
    public void VListCollectionExpressionTest() {
        VList<int> list = [1, 2, 3];
        Assert.That(list.Count, Is.EqualTo(3));
        Assert.That(list[0], Is.EqualTo(1));
        Assert.That(list[1], Is.EqualTo(2));
        Assert.That(list[2], Is.EqualTo(3));
    }
}
