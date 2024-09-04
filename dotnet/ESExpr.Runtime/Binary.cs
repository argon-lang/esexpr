using System;
using System.Diagnostics.CodeAnalysis;

namespace ESExpr.Runtime;

public readonly struct Binary : IEquatable<Binary> {
	public Binary(byte[] bytes) {
		this.bytes = bytes;
	}

	private readonly byte[]? bytes;
	
	public byte[] ByteArray => bytes ?? Array.Empty<byte>();
	public ReadOnlySpan<byte> Span => ByteArray;
	
	public static implicit operator byte[](Binary binary) => binary.ByteArray;
	public static implicit operator Binary(byte[] bytes) => new Binary(bytes);

	public bool Equals(Binary other) => Span.SequenceEqual(other.Span);

	public override bool Equals([NotNullWhen(true)] object? obj) =>
		obj is Binary other && Equals(other);

	public override int GetHashCode() {
		var hash = new HashCode();
		foreach(var b in ByteArray) {
			hash.Add(b);
		}
		return hash.ToHashCode();
	}

	public static bool operator ==(Binary left, Binary right) => left.Equals(right);
	public static bool operator !=(Binary left, Binary right) => !(left == right);
}
