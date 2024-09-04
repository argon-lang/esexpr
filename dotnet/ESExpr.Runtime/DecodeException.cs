using System;

namespace ESExpr.Runtime;

public class DecodeException : Exception {
	public DecodeException(string message, DecodeFailurePath path) : base(message) {
		Path = path;
	}
	
	public DecodeFailurePath Path { get; }
}
