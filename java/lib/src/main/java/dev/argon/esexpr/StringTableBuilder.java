package dev.argon.esexpr;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Records expression strings to build a compact, frequency-ordered binary
 * string table.
 * 
 * <pre>{@code
 * var table = new StringTableBuilder();
 * expressions.forEach(table::record);
 * var writer = new ESExprBinaryWriter(output);
 * writer.addStrings(table.build());
 * for(var expr : expressions)
 * 	writer.write(expr);
 * }</pre>
 * 
 * The emitted stream can be decoded by a reader without an initial string
 * table.
 */
public final class StringTableBuilder {
	private static final class Usage {
		long required;
		long literal;

		long total() {
			return required > Long.MAX_VALUE - literal ? Long.MAX_VALUE : required + literal;
		}
	}

	private final Map<String, Usage> strings = new HashMap<>();

	/** Creates an empty builder. */
	public StringTableBuilder() {}

	/**
	 * Records constructor names, keyword names, and string literals recursively.
	 * Dedicated constructor tags do not require table entries.
	 * 
	 * @param expr
	 *            The expression to record.
	 */
	public void record(ESExpr expr) {
		switch (expr) {
			case ESExpr.Constructor(var name, var args, var kwargs) -> {
				if(!isDedicatedConstructor(name)) {
					recordString(name, true);
				}
				args.forEach(this::record);
				kwargs.forEach((key, value) -> {
					recordString(key, true);
					record(value);
				});
			}
			case ESExpr.Str(var value) -> recordString(value, false);
			default -> {
			}
		}
	}

	private void recordString(String value, boolean required) {
		var usage = strings.computeIfAbsent(value, key -> new Usage());
		if(required) {
			if(usage.required < Long.MAX_VALUE)
				++usage.required;
		}
		else if(usage.literal < Long.MAX_VALUE) {
			++usage.literal;
		}
	}

	/**
	 * Builds a table ordered by decreasing combined frequency, with lexical ties.
	 * Constructor and keyword entries are mandatory. Literal-only entries must
	 * occur at least twice and save more bytes than their inline table-entry cost
	 * at their prospective index. Shared framing and effects on later indices are
	 * excluded from this heuristic. This method does not reset recorded usage.
	 * 
	 * @return The selected strings in table-index order.
	 */
	public List<String> build() {
		var candidates = new ArrayList<String>();
		strings.forEach((value, usage) -> {
			if(usage.required != 0 || usage.literal > 1)
				candidates.add(value);
		});
		candidates.sort((a, b) -> {
			int order = Long.compare(
				Objects.requireNonNull(strings.get(b)).total(),
				Objects.requireNonNull(strings.get(a)).total()
			);
			return order != 0 ? order : compareStrings(a, b);
		});
		int accepted = 0;
		for(int i = 0; i < candidates.size(); ++i) {
			var value = candidates.get(i);
			var usage = Objects.requireNonNull(strings.get(value));
			long inline = inlineStringSize(value);
			long saving = inline - taggedIntSize(accepted);
			// Division avoids overflowing count * saving.
			if(usage.required != 0 || (saving > 0 && usage.literal > inline / saving)) {
				candidates.set(accepted++, value);
			}
		}
		candidates.subList(accepted, candidates.size()).clear();
		return candidates;
	}

	// Compare Unicode code points, matching Rust's UTF-8 lexical ordering.
	private static int compareStrings(String a, String b) {
		int ai = 0;
		int bi = 0;
		while (ai < a.length() && bi < b.length()) {
			int ac = a.codePointAt(ai);
			int bc = b.codePointAt(bi);
			if(ac != bc)
				return Integer.compare(ac, bc);
			ai += Character.charCount(ac);
			bi += Character.charCount(bc);
		}
		return Integer.compare(a.length() - ai, b.length() - bi);
	}

	static boolean isDedicatedConstructor(String name) {
		return switch (name) {
			case BinToken.StringTableName, BinToken.ListName, BinToken.MapName, BinToken.SetName -> true;
			default -> false;
		};
	}

	static int taggedIntSize(int value) {
		int size = 1;
		value >>>= 4;
		while (value != 0) {
			++size;
			value >>>= 7;
		}
		return size;
	}

	static long inlineStringSize(String value) {
		int size = value.getBytes(StandardCharsets.UTF_8).length;
		return (long)size + taggedIntSize(size);
	}
}
