using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class FlagsTests : TestBase {
	[Test]
	public void TwoFlagsTest() {
		AssertCodecMatch(
			TwoFlags.Codec,
			new Expr.Int(0),
			new TwoFlags {
				A = false,
				B = false,
			}
		);
		AssertCodecMatch(
			TwoFlags.Codec,
			new Expr.Int(1),
			new TwoFlags {
				A = true,
				B = false,
			}
		);
		AssertCodecMatch(
			TwoFlags.Codec,
			new Expr.Int(2),
			new TwoFlags {
				A = false,
				B = true,
			}
		);
		AssertCodecMatch(
			TwoFlags.Codec,
			new Expr.Int(3),
			new TwoFlags {
				A = true,
				B = true,
			}
		);
	}

	[Test]
	public void FlagWithEnumTest() {
		var codec = FlagWithEnum.Codec;
		
		Assert.Throws<DecodeException>(() => ((IESExprCodec<FlagWithEnum>)codec).Decode(new Expr.Int(0)));
		Assert.Throws<DecodeException>(() => ((IESExprCodec<FlagWithEnum>)codec).Decode(new Expr.Int(1)));

		AssertCodecMatch(
			codec,
			new Expr.Int(2),
			new FlagWithEnum {
				A = false,
				B = FlagWithEnum.MyEnum.X,
			}
		);
		AssertCodecMatch(
			codec,
			new Expr.Int(3),
			new FlagWithEnum {
				A = true,
				B = FlagWithEnum.MyEnum.X,
			}
		);
		AssertCodecMatch(
			codec,
			new Expr.Int(4),
			new FlagWithEnum {
				A = false,
				B = FlagWithEnum.MyEnum.Y,
			}
		);
		AssertCodecMatch(
			codec,
			new Expr.Int(5),
			new FlagWithEnum {
				A = true,
				B = FlagWithEnum.MyEnum.Y,
			}
		);
		AssertCodecMatch(
			codec,
			new Expr.Int(6),
			new FlagWithEnum {
				A = false,
				B = FlagWithEnum.MyEnum.Z,
			}
		);
		AssertCodecMatch(
			codec,
			new Expr.Int(7),
			new FlagWithEnum {
				A = true,
				B = FlagWithEnum.MyEnum.Z,
			}
		);
	}
}
