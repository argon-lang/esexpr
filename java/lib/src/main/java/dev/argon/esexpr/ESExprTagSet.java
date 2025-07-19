package dev.argon.esexpr;

import com.google.common.collect.ImmutableSet;

import java.util.Collections;

/**
 * Represents a set of expression tags.
 */
public sealed interface ESExprTagSet {
	/**
	 * A set of all tags.
	 */
	record All() implements ESExprTagSet {
		@Override
		public ESExprTagSet add(ESExprTag tag) {
			return this;
		}

		@Override
		public ESExprTagSet union(ESExprTagSet other) {
			return this;
		}

		@Override
		public boolean contains(ESExprTag tag) {
			return true;
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public boolean isAll() {
			return true;
		}

		@Override
		public boolean isDisjoint(ESExprTagSet other) {
			return other.isEmpty();
		}
	}

	/**
	 * A finite set of tags.
	 * @param tags The tags.
	 */
	record Tags(ImmutableSet<ESExprTag> tags) implements ESExprTagSet {
		@Override
		public ESExprTagSet add(ESExprTag tag) {
			if(tags.contains(tag)) {
				return this;
			}

			return new Tags(
				ImmutableSet.<ESExprTag>builder()
					.addAll(tags)
					.add(tag)
					.build()
			);
		}

		@Override
		public ESExprTagSet union(ESExprTagSet other) {
			return switch(other) {
				case All all -> all;
				case Tags(var otherTags) -> new Tags(
					ImmutableSet.<ESExprTag>builder()
						.addAll(tags)
						.addAll(otherTags)
						.build()
				);
			};
		}

		@Override
		public boolean isEmpty() {
			return tags.isEmpty();
		}

		@Override
		public boolean isAll() {
			return false;
		}

		@Override
		public boolean contains(ESExprTag tag) {
			return tags.contains(tag);
		}

		@Override
		public boolean isDisjoint(ESExprTagSet other) {
			return switch(other) {
				case All() -> isEmpty();
				case Tags(var otherTags) -> Collections.disjoint(tags, otherTags);
			};
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

	/**
	 * Creates a tag set with a new tag added.
	 * @param tag The tag to add.
	 * @return The updated set.
	 */
	ESExprTagSet add(ESExprTag tag);

	/**
	 * Creates a tag set with all elements of this and another set.
	 * @param other The other set.
	 * @return The updated set.
	 */
	ESExprTagSet union(ESExprTagSet other);

	/**
	 * {@return true iff the set is empty}
	 */
	boolean isEmpty();

	/**
	 * {@return true iff this is the set of all tags}
	 */
	boolean isAll();

	/**
	 * Checks if the set contains a tag.
	 * @param tag The tag to check.
	 * @return true iff the set contains the tag.
	 */
	boolean contains(ESExprTag tag);

	/**
	 * Checks if sets are disjoint.
	 * @param other The other set.
	 * @return true iff there are no common elements between the sets.
	 */
	boolean isDisjoint(ESExprTagSet other);
}
