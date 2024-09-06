using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using ESExpr.Runtime.Codecs;

namespace ESExpr.Runtime;

public record StringTable(VList<string> strings) {
	public class Codec : IESExprCodec<StringTable> {
		public ISet<ESExprTag> Tags => (HashSet<ESExprTag>) [ new ESExprTag.Constructor("string-table") ];
			
		internal const string StringTableConstructor = "string-table";
		
		public Expr Encode(StringTable value) {
			return new Expr.Constructor(
				StringTableConstructor,
				value.strings.Select(new StringCodec().Encode).ToImmutableList(),
				ImmutableDictionary<string, Expr>.Empty
			);
		}

		public StringTable Decode(Expr expr, DecodeFailurePath path) {
			if(expr is Expr.Constructor(StringTableConstructor, var args, var kwargs)) {
				if(kwargs.Count > 0) {
					throw new DecodeException("Unexpected keyword arguments for string table", path.WithConstructor(StringTableConstructor));
				}

				return new StringTable(
					args
						.Select((arg, i) =>
							new StringCodec().Decode(arg, path.Append(StringTableConstructor, i))
						)
						.ToImmutableList()
				);
			}
			else {
				Console.WriteLine(expr);
				throw new DecodeException("Expected a string-table constructor", path);
			}
		}
	}
}
