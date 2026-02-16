using System.Collections.Immutable;
using System.Linq;

namespace ESExpr.SourceGenerator.TypeClass;

internal record SourceModelTypeClassInstanceAccessor {
	public required bool IsProperty { get; init; }
	public required ImmutableList<SourceModelTypeClassInstanceAccessorParameter> Parameters { get; init; } 
	public required string Name {  get; init; }

	public override int GetHashCode() {
		int hash = 17;
		unchecked {
			foreach(var item in Parameters) {
				hash = hash * 31 + item.GetHashCode();
			}
			
			hash = hash * 23 + Name.GetHashCode();
		}
		return hash;
	}

	public virtual bool Equals(SourceModelTypeClassInstanceAccessor? other) {
		return other is not null &&
			Parameters.SequenceEqual(other.Parameters) &&
			Name == other.Name;
	}
}
