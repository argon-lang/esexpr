using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class SimpleEnumTests : TestBase {
	[Test]
	public void SimpleEnum() {
		AssertCodecMatch(
			SimpleEnumUser.Codec,
			new Expr.Constructor(
				"simple-enum-user",
				[new Expr.Str("test-name123")],
				ImmutableDictionary<string, Expr>.Empty
			),
			new SimpleEnumUser {
				SimpleEnumValue = MySimpleEnum.TestName123,
			}
		);
		AssertCodecMatch(
			SimpleEnumUser.Codec,
			new Expr.Constructor(
				"simple-enum-user",
				[new Expr.Str("test-name-456")],
				ImmutableDictionary<string, Expr>.Empty
			),
			new SimpleEnumUser {
				SimpleEnumValue = MySimpleEnum.TestName_456,
			}
		);
		AssertCodecMatch(
			SimpleEnumUser.Codec,
			new Expr.Constructor(
				"simple-enum-user",
				[new Expr.Str("other-name-here")],
				ImmutableDictionary<string, Expr>.Empty
			),
			new SimpleEnumUser {
				SimpleEnumValue = MySimpleEnum.OtherNameHere,
			}
		);
		AssertCodecMatch(
			SimpleEnumUser.Codec,
			new Expr.Constructor(
				"simple-enum-user",
				[new Expr.Str("my-custom-name")],
				ImmutableDictionary<string, Expr>.Empty
			),
			new SimpleEnumUser {
				SimpleEnumValue = MySimpleEnum.CustomName,
			}
		);
	}
}
