package dev.argon.esexpr;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a set of expression tags.
 */
public sealed interface ESExprTagSet {
	/**
	 * A set of all tags.
	 */
	record All() implements ESExprTagSet {
		@Override
		public boolean contains(@NotNull ESExprTag tag) {
			return true;
		}
	}

	/**
	 * A finite set of tags.
	 * @param tags The tags.
	 */
	record Tags(ImmutableSet<@NotNull ESExprTag> tags) implements ESExprTagSet {
		@Override
		public boolean contains(@NotNull ESExprTag tag) {
			return tags.contains(tag);
		}
	}

	/**
	 * Creates an instance of {@code ESExprTagSet} from the given tags.
	 *
	 * @param tags The array of {@code ESExprTag} elements to include in the set.
	 * @return A {@code ESExprTagSet} containing the specified tags.
	 */
	public static ESExprTagSet of(ESExprTag... tags) {
		return new ESExprTagSet.Tags(ImmutableSet.copyOf(tags));
	}

	boolean contains(@NotNull ESExprTag tag);
}
