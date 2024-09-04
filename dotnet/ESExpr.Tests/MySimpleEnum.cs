using ESExpr.Runtime;

namespace ESExpr.Tests;

public enum MySimpleEnum {
	TestName123,
	TestName_456,
	OtherNameHere,
	[Constructor("my-custom-name")]
	CustomName,
}
