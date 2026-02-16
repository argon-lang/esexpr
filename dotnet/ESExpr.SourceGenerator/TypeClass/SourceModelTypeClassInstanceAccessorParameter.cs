namespace ESExpr.SourceGenerator.TypeClass;

internal sealed record SourceModelTypeClassInstanceAccessorParameter {
	public required string Name { get; init; }
	public required SourceModelType Type { get; init; }
};
