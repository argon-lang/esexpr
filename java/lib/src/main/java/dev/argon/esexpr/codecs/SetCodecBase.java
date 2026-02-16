package dev.argon.esexpr.codecs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dev.argon.esexpr.*;

import java.util.Set;
import java.util.function.Consumer;

/**
 * An abstract base class for implementing codecs that encode and decode Set-like structures.
 *
 * @param <T> The type of set elements.
 * @param <S> The type of set being encoded and decoded.
 */
public abstract class SetCodecBase<T, S extends Set<T>> implements ESExprCodec<S> {

	/**
	 * Creates a codec for encoding and decoding Set-like structures.
	 *
	 * @param codec The codec used for encoding and decoding the elements of the set.
	 */
	public SetCodecBase(ESExprCodec<T> codec) {
		this.codec = codec;
	}

	private final ESExprCodec<T> codec;

	@Override
	public ESExprTagSet tags() {
		return new ESExprTagSet.Tags(ImmutableSet.of(new ESExprTag.Constructor("set")));
	}

	@Override
	public ESExpr encode(S value) {
		var args = ImmutableList.<ESExpr>builder();

		for(var element : value) {
			args.add(codec.encode(element));
		}

		return new ESExpr.Constructor("set", args.build(), ImmutableMap.of());
	}

	@Override
	public abstract S decode(ESExpr expr, FailurePath path) throws DecodeException;

	/**
	 * Decodes the given ESExpr representing a set into individual elements and passes them to the provided consumer.
	 * The method expects the expression to represent a "set" constructor with no keyword arguments.
	 * If the expression does not meet this requirement, a {@link DecodeException} is thrown.
	 *
	 * @param expr The ESExpr to be decoded.
	 * @param path The current failure path for error reporting.
	 * @param add A consumer that accepts each decoded element.
	 * @throws DecodeException If the expression is not a valid "set" constructor,
	 *                         it contains keyword arguments, or
	 *                         decoding fails for any of the set elements.
	 */
	protected final void decodeInto(ESExpr expr, FailurePath path, Consumer<T> add) throws DecodeException {
		if(!(expr instanceof ESExpr.Constructor(var name, var args, var kwargs) && name.equals("set"))) {
			throw new DecodeException("Expected a set value", path);
		}

		if(!kwargs.isEmpty()) {
			throw new DecodeException("Unexpected keyword arguments for set.", path.withConstructor("set"));
		}

		for(int i = 0; i < args.size(); i++) {
			var element = codec.decode(args.get(i), path.append("set", i));
			add.accept(element);
		}
	}

	@Override
	public boolean isEncodedEqual(S x, S y) {
		if(x.size() != y.size()) {
			return false;
		}

		for(var element : x) {
			boolean found = false;
			for (var other : y) {
				if (codec.isEncodedEqual(element, other)) {
					found = true;
					break;
				}
			}

			if (!found) {
				return false;
			}
		}

		return true;
	}
}
