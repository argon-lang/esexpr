# ESExpr Generator

This is a source generator that creates ESExpr codes for types with the `[ESExprCodec]` attribute.


Requires the .NET 11 RC2 SDK and C# 15. Records use `sealed record` for
product types and `closed record` for enums with payloads. Enum cases remain
public nested sealed records. A private parameterless base constructor is optional.

```csharp
[ESExprCodec]
public closed partial record Result {
    public sealed record Success : Result {
        public required int Value { get; init; }
    }
    public sealed record Failure : Result;

    [TypeClassInstance]
    public static partial IESExprCodec<Result> Codec { get; }
}
```
