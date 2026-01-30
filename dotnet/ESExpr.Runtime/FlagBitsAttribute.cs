using System;
using System.Globalization;
using System.Numerics;

namespace ESExpr.Runtime;

public class FlagBitsAttribute : Attribute {
	public FlagBitsAttribute(ulong bits) {
		Bits = bits;
	}

	public FlagBitsAttribute(string bits) {
		Bits = BigInteger.Parse(bits, CultureInfo.InvariantCulture);
	}
	
	public BigInteger Bits { get; }
}
