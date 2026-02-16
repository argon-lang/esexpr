using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator.TypeClass;

public record LocalInfo(string Name, ITypeSymbol Type);
