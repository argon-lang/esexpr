namespace ESExpr.Runtime;

public abstract record DecodeFailurePath {
	private DecodeFailurePath() {}

	public sealed record Current : DecodeFailurePath {
		public override DecodeFailurePath WithConstructor(string constructor) {
			return new Constructor(constructor);
		}

		public override DecodeFailurePath Append(string constructor, int index) {
			return new Positional(constructor, index, this);
		}

		public override DecodeFailurePath Append(string constructor, string kw) {
			return new Keyword(constructor, kw, this);
		}
	}

	public sealed record Constructor(string name) : DecodeFailurePath {
		public override DecodeFailurePath WithConstructor(string constructor) {
			return new Constructor(constructor);
		}

		public override DecodeFailurePath Append(string constructor, int index) {
			return new Positional(constructor, index, this);
		}

		public override DecodeFailurePath Append(string constructor, string kw) {
			return new Keyword(constructor, kw, this);
		}
	}
	public sealed record Positional(string constructor, int index, DecodeFailurePath next) : DecodeFailurePath {
		public override DecodeFailurePath WithConstructor(string constructor) {
			return new Positional(this.constructor, index, next.WithConstructor(constructor));
		}

		public override DecodeFailurePath Append(string constructor, int index) {
			return new Positional(this.constructor, this.index, next.Append(constructor, index));
		}

		public override DecodeFailurePath Append(string constructor, string kw) {
			return new Positional(this.constructor, index, next.Append(constructor, kw));
		}
	}
	public sealed record Keyword(string constructor, string keyword, DecodeFailurePath next) : DecodeFailurePath {
		public override DecodeFailurePath WithConstructor(string constructor) {
			return new Keyword(this.constructor, keyword, next.WithConstructor(constructor));
		}

		public override DecodeFailurePath Append(string constructor, int index) {
			return new Keyword(this.constructor, this.keyword, next.Append(constructor, index));
		}

		public override DecodeFailurePath Append(string constructor, string kw) {
			return new Keyword(this.constructor, keyword, next.Append(constructor, kw));
		}
	}

	public abstract DecodeFailurePath WithConstructor(string constructor);
	public abstract DecodeFailurePath Append(string constructor, int index);
	public abstract DecodeFailurePath Append(string constructor, string kw);
}
