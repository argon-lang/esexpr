using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

public sealed class SourceModelSyntax<T>(T syntax) where T : CSharpSyntaxNode {
	public T Syntax => syntax;

	public override bool Equals(object? obj) {
		if(obj is not SourceModelSyntax<T> other) {
			return false;
		}
		
		return Syntax.ToString() == other.Syntax.ToString();
	}

	public override int GetHashCode() {
		return Syntax.ToString().GetHashCode();
	}

	public override string ToString() {
		return Syntax.ToString();
	}
}
