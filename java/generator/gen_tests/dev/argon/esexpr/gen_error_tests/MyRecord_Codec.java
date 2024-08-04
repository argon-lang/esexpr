package dev.argon.esexpr.gen_error_tests;
public class MyRecord_Codec extends dev.argon.esexpr.ESExprCodec<dev.argon.esexpr.gen_error_tests.MyRecord> {
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
		args.add(new java.util.Map_Codec<>(dev.argon.esexpr.ESExprCodec.STRING_CODEC, dev.argon.esexpr.ESExprCodec.STRING_CODEC).encode(value.a()));
		kwargs.put("b", dev.argon.esexpr.ESExprCodec.STRING_CODEC.encode(value.b()));
		return new dev.argon.esexpr.ESExpr.Constructor("my-record", args, kwargs);
	}
	@java.lang.Override
	public dev.argon.esexpr.gen_error_tests.MyRecord decode(dev.argon.esexpr.ESExpr expr, dev.argon.esexpr.ESExprCodec.FailurePath path) throws dev.argon.esexpr.DecodeException {
		if(expr instanceof dev.argon.esexpr.ESExpr.Constructor(var name, var args0, var kwargs0) && name.equals("my-record")) {
			var args = new java.util.ArrayList<>(args0);
			var kwargs = new java.util.HashMap<>(kwargs0);
			if(args.isEmpty()) { throw new dev.argon.esexpr.DecodeException("Not enough arguments", path.withConstructor("my-record")); }
			var field_a = new java.util.Map_Codec<>(dev.argon.esexpr.ESExprCodec.STRING_CODEC, dev.argon.esexpr.ESExprCodec.STRING_CODEC).decode(args.removeFirst(), path.append(
			"my-record", 0));
			var expr_b = kwargs.remove("b");
			if(expr_b == null) { throw new dev.argon.esexpr.DecodeException("Missing required keyword argument", path.withConstructor("my-record")); }
			var field_b = dev.argon.esexpr.ESExprCodec.STRING_CODEC.decode(expr_b, path.append("my-record", "b"));
			if(!args.isEmpty()) { throw new dev.argon.esexpr.DecodeException("Extra positional arguments were found.", path.withConstructor(
			"my-record")); }
			if(!kwargs.isEmpty()) { throw new dev.argon.esexpr.DecodeException("Extra keyword arguments were found.", path.withConstructor(
			"my-record")); }
			return new dev.argon.esexpr.gen_error_tests.MyRecord(field_a, field_b);
		}
		else {
			throw new dev.argon.esexpr.DecodeException("Expected a my-record constructor", path);
		}
	}
}
