package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;


public sealed interface ESExprTag {
    public static record Constructor(@NotNull String constructor) implements ESExprTag {}

    public static record Bool() implements ESExprTag {}

    public static record Int() implements ESExprTag {}

    public static record Str() implements ESExprTag {}

    public static record Binary() implements ESExprTag {}

    public static record Float32() implements ESExprTag {}

    public static record Float64() implements ESExprTag {}

    public static record Null() implements ESExprTag {}

}
