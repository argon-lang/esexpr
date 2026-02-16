using System.Collections.Generic;
using System.Collections.Immutable;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator.TypeClass;

internal abstract record TypeClassResult {
	public sealed record Method(
		ImmutableList<ITypeSymbol> TypeArguments,
		ImmutableList<TypeClassResult> Arguments,
		IMethodSymbol MethodSymbol
	) : TypeClassResult;
	public sealed record Property(IPropertySymbol PropertySymbol) : TypeClassResult;
	public sealed record Local(LocalInfo LocalInfo) : TypeClassResult;
	
}
