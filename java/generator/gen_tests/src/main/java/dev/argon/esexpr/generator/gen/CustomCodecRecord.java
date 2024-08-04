package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@ESExprCodecGen
public record CustomCodecRecord(
	@UseCodec(ACodec.class)
	int a,

	@UseCodec(BCodec.class)
	List<String> b,

	@UseCodec(CCodec.class)
	List<String> c
) {

	public static class ACodec extends ESExprCodec<Integer> {
		@Override
		public Set<ESExprTag> tags() {
			return Set.of(new ESExprTag.Int());
		}

		@Override
		public ESExpr encode(Integer value) {
			return new ESExpr.Int(BigInteger.valueOf(2L * value));
		}

		@Override
		public Integer decode(ESExpr expr, FailurePath path) throws DecodeException {
			var value = ESExprCodec.BIG_INTEGER_CODEC.decode(expr, path);
			return value.divide(BigInteger.TWO).intValue();
		}
	}

	public static class BCodec extends ESExprCodec<List<String>> {
		@Override
		public Set<ESExprTag> tags() {
			return Set.of(new ESExprTag.Int());
		}

		@Override
		public ESExpr encode(List<String> value) {
			return new ESExpr.Int(BigInteger.valueOf(value.size()));
		}

		@Override
		public List<String> decode(ESExpr expr, FailurePath path) throws DecodeException {
			var value = ESExprCodec.BIG_INTEGER_CODEC.decode(expr, path).intValue();
			return Collections.nCopies(value, "b");
		}
	}

	public static class CCodec<A> extends ESExprCodec<List<A>> {
		public CCodec(ESExprCodec<A> aCodec) {
			this.aCodec = aCodec;
		}

		private final ESExprCodec<A> aCodec;

		@Override
		public Set<ESExprTag> tags() {
			return Set.of(new ESExprTag.Constructor("list"));
		}

		@Override
		public ESExpr encode(List<A> value) {
			return ESExprCodec.listCodec(aCodec).encode(value.reversed());
		}

		@Override
		public List<A> decode(ESExpr expr, FailurePath path) throws DecodeException {
			return ESExprCodec.listCodec(aCodec).decode(expr, path).reversed();
		}
	}
}
