using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

public sealed class SourceModelIgnored<T>(T value) {
	public T Value => value;

	public override bool Equals(object? obj) {
		return obj is SourceModelIgnored<T>;
	}

	public override int GetHashCode() {
		return 0;
	}

	public override string ToString() {
		return Value?.ToString() ?? string.Empty;
	}
}
