package dev.argon.esexpr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

/**
 * A string table used while reading binary ESExprs.
 * @param values The strings in the string table.
 */
public record StringTable(List<String> values) {
	
	public static final ESExprCodec<StringTable> codec = new ESExprCodec<StringTable>() {

		@Override
		public @NotNull Set<@NotNull ESExprTag> tags() {
			return Set.of(new ESExprTag.Constructor(BinToken.StringTableName));
		}

		@Override
		public @NotNull ESExpr encode(@NotNull StringTable value) {
			return new ESExpr.Constructor(
				BinToken.StringTableName,
				value.values.stream().map(ESExprCodec.STRING_CODEC::encode).toList(),
				new HashMap<>()
			);
		}

		@Override
		public @NotNull StringTable decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
			if(expr instanceof ESExpr.Constructor(var name, var args, var kwargs) && name.equals(BinToken.StringTableName)) {
				if(!kwargs.isEmpty()) {
					throw new DecodeException("Unexpected keyword arguments for string table", path.withConstructor(BinToken.StringTableName));
				}

				var values = new ArrayList<String>(args.size());
				int i = 0;

				for(var arg : args) {
					values.add(ESExprCodec.STRING_CODEC.decode(arg, path.append(BinToken.StringTableName, i)));
					++i;
				}

				return new StringTable(values);
			}
			else {
				throw new DecodeException("Expected a string-table constructor", path);
			}
		}
		
	};

}
