using System.Collections.Generic;
using System.Collections.Immutable;
using ESExpr.Runtime;
using ESExpr.Runtime.Codecs;
using NUnit.Framework;

namespace ESExpr.Tests;

public class OptionCodecTests : TestBase {
	[Test]
	public void SomeCodecTest() {
		var codec = Option.Codec(IESExprCodec.IntCodec);
		var option = new Option<int>(1);
		var expr = new Expr.Int(1);
		
		var encoded = codec.Encode(option);
		Assert.That(encoded, Is.EqualTo(expr));
		
		var decoded = codec.Decode(expr, new DecodeFailurePath.Current());
		Assert.That(decoded, Is.EqualTo(option));
	}
	
	[Test]
	public void NoneCodecTest() {
		var codec = Option.Codec(IESExprCodec.IntCodec);
		var option = Option<int>.Empty;
		var expr = new Expr.Null(0);
		
		var encoded = codec.Encode(option);
		Assert.That(encoded, Is.EqualTo(expr));
		
		var decoded = codec.Decode(expr, new DecodeFailurePath.Current());
		Assert.That(decoded, Is.EqualTo(option));
	}
}
