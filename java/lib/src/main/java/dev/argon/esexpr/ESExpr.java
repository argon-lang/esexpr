package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public sealed interface ESExpr {

	@NotNull ESExprTag tag();

    public static record Constructor(@NotNull String constructor, @NotNull List<ESExpr> args, @NotNull Map<String, ESExpr> kwargs) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Constructor(constructor);
		}
	}

    public static record Bool(boolean b) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Bool();
		}
	}

    public static record Int(@NotNull BigInteger n) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Int();
		}
	}

    public static record Str(@NotNull String s) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Str();
		}
	}

    public static record Binary(byte @NotNull[] b) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Binary();
		}
	}

    public static record Float32(float f) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Float32();
		}
	}

    public static record Float64(double d) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Float64();
		}
	}


    public static record Null() implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Null();
		}
	}

}
