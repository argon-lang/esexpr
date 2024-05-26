package dev.argon.esexpr.gen_error_tests;
public class MyRecord_Codec implements dev.argon.esexpr.ESExprCodec<dev.argon.esexpr.gen_error_tests.MyRecord> {
	public static final dev.argon.esexpr.ESExprCodec<dev.argon.esexpr.gen_error_tests.MyRecord> INSTANCE = new dev.argon.esexpr.gen_error_tests.MyRecord_Codec();
	@java.lang.Override
	public java.util.Set<dev.argon.esexpr.ESExprTag> tags() {
		var tags = new java.util.HashSet<dev.argon.esexpr.ESExprTag>();
		tags.add(new dev.argon.esexpr.ESExprTag.Constructor("my-record"));
		return tags;
	}
	@java.lang.Override
	public dev.argon.esexpr.ESExpr encode(dev.argon.esexpr.gen_error_tests.MyRecord value) {
		var args = new java.util.ArrayList<dev.argon.esexpr.ESExpr>();
		var kwargs = new java.util.HashMap<java.lang.String, dev.argon.esexpr.ESExpr>();
		for(var arg : value.a()) {
			args.add(dev.argon.esexpr.ESExprCodec.STRING_CODEC.encode(arg));
		}
		args.add(dev.argon.esexpr.ESExprCodec.STRING_CODEC.encode(value.b()));
		return new dev.argon.esexpr.ESExpr.Constructor("my-record", args, kwargs);
	}
	@java.lang.Override
	public dev.argon.esexpr.gen_error_tests.MyRecord decode(dev.argon.esexpr.ESExpr expr) throws dev.argon.esexpr.DecodeException {
		if(expr instanceof dev.argon.esexpr.ESExpr.Constructor(var name, var args0, var kwargs0) && name.equals("my-record")) {
			var args = new java.util.ArrayList<>(args0);
			var kwargs = new java.util.HashMap<>(kwargs0);
			var field_a = new java.util.ArrayList<java.lang.String>();for(var arg : args) {
				field_a.add(dev.argon.esexpr.ESExprCodec.STRING_CODEC.decode(arg));
			}
			args.clear();
			if(args.isEmpty()) { throw new dev.argon.esexpr.DecodeException("Not enough arguments"); }
			var field_b = dev.argon.esexpr.ESExprCodec.STRING_CODEC.decode(args.removeFirst());
			if(!args.isEmpty()) { throw new dev.argon.esexpr.DecodeException("Extra positional arguments were found."); }
			if(!kwargs.isEmpty()) { throw new dev.argon.esexpr.DecodeException("Extra keyword arguments were found."); }
			return new dev.argon.esexpr.gen_error_tests.MyRecord(field_a, field_b);
		}
		else {
			throw new dev.argon.esexpr.DecodeException("Expected a my-record constructor");
		}
	}
}
