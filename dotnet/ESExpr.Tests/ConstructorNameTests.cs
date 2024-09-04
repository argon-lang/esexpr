using System.Collections.Immutable;
using System.Numerics;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class Tests : TestBase {
    [Test]
    public void ConstructorNameConversion() {
	    Assert.That(
		    new PrimitiveFields.Codec().Tags,
		    Is.EqualTo((HashSet<ESExprTag>) [new ESExprTag.Constructor("primitive-fields")])
	    );
	    
	    AssertCodecMatch(
		    new PrimitiveFields.Codec(),
		    new Expr.Constructor(
			    "primitive-fields",
			    [
					new Expr.Bool(false),
					new Expr.Int(BigInteger.Zero),
					new Expr.Int(byte.MaxValue),
					new Expr.Int(BigInteger.Zero),
					new Expr.Int(ushort.MaxValue),
					new Expr.Int(BigInteger.Zero),
					new Expr.Int(uint.MaxValue),
					new Expr.Int(BigInteger.Zero),
					new Expr.Int(ulong.MaxValue),
					new Expr.Float32(0.0f),
					new Expr.Float64(0.0),
			    ],
			    ImmutableDictionary<string, Expr>.Empty
		    ),
		    new PrimitiveFields {
			    A = false,
			    B = 0,
			    B2 = byte.MaxValue,
			    C = 0,
			    C2 = ushort.MaxValue,
			    D = 0,
			    D2 = uint.MaxValue,
			    E = 0,
			    E2 = ulong.MaxValue,
			    F = 0.0f,
			    G = 0.0,
		    }
	    );
	    
	    Assert.That(
		    new ConstructorName123Conversion.Codec().Tags,
		    Is.EqualTo((HashSet<ESExprTag>) [new ESExprTag.Constructor("constructor-name123-conversion")])
		);
	    
	    AssertCodecMatch(
			new ConstructorName123Conversion.Codec(),
			new Expr.Constructor(
				"constructor-name123-conversion",
				[ new Expr.Int(BigInteger.Zero) ],
				ImmutableDictionary<string, Expr>.Empty
			),
			new ConstructorName123Conversion {
				A = 0,
			}
		);
	    
	    Assert.That(
		    new ConstructorName_456Conversion.Codec().Tags,
		    Is.EqualTo((HashSet<ESExprTag>) [new ESExprTag.Constructor("constructor-name-456-conversion")])
	    );
	    
	    AssertCodecMatch(
		    new ConstructorName_456Conversion.Codec(),
		    new Expr.Constructor(
			    "constructor-name-456-conversion",
			    [ new Expr.Int(BigInteger.Zero) ],
			    ImmutableDictionary<string, Expr>.Empty
		    ),
		    new ConstructorName_456Conversion {
			    A = 0,
		    }
	    );
	    
	    Assert.That(
		    new MyEnum.Codec().Tags,
		    Is.EqualTo((HashSet<ESExprTag>) [new ESExprTag.Constructor("my-case-a"), new ESExprTag.Constructor("my-case-b")])
	    );
	    
	    AssertCodecMatch(
		    new MyEnum.Codec(),
		    new Expr.Constructor(
			    "my-case-a",
			    [ new Expr.Int(BigInteger.Zero) ],
			    ImmutableDictionary<string, Expr>.Empty
		    ),
		    new MyEnum.MyCaseA {
			    A = 0,
		    }
	    );
	    
	    AssertCodecMatch(
		    new MyEnum.Codec(),
		    new Expr.Constructor(
			    "my-case-b",
			    [ new Expr.Float32(0.0f) ],
			    ImmutableDictionary<string, Expr>.Empty
		    ),
		    new MyEnum.MyCaseB {
			    B = 0.0f,
		    }
	    );
	    
	    
    }
}
