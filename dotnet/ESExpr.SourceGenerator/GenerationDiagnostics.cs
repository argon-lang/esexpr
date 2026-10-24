using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

internal sealed class InputDiagnosticException(DiagnosticDescriptor descriptor, params object[] arguments) : Exception(descriptor.Title.ToString(System.Globalization.CultureInfo.InvariantCulture)) {
	public DiagnosticDescriptor Descriptor { get; } = descriptor;
	public object[] Arguments { get; } = arguments;
	public Location? SourceLocation { get; init; }
}

internal sealed class GenerationDiagnostics {
	private readonly List<Diagnostic> diagnostics = [];
	private readonly HashSet<(DiagnosticDescriptor, Location, string)> keys = [];
	public ImmutableArray<Diagnostic> Diagnostics => diagnostics.ToImmutableArray();
	public List<Location> FailedFields { get; } = [];
	public bool HasErrors => diagnostics.Count != 0;
	public Location Location { get; set; } = Location.None;

	public void Report(Diagnostic diagnostic) {
		var key = (diagnostic.Descriptor, diagnostic.Location, diagnostic.GetMessage(System.Globalization.CultureInfo.InvariantCulture));
		if(keys.Add(key)) diagnostics.Add(diagnostic);
	}

	public T Recover<T>(Location location, Func<T> action, T fallback) {
		var previous = Location;
		Location = location;
		try { return action(); }
		catch(Exception ex) when(IsRecoverable(ex)) { ReportException(ex); return fallback; }
		finally { Location = previous; }
	}

	public void ReportException(Exception ex) {
		switch(ex) {
			case AbortGenerationException abort: Report(abort.Diagnostic); break;
			case InputDiagnosticException input: Report(Diagnostic.Create(input.Descriptor, input.SourceLocation ?? Location, input.Arguments)); break;
			default: Report(Diagnostic.Create(Errors.InternalFailure, Location, ex.ToString().Replace("\n", " "))); break;
		}
	}

	public static bool IsRecoverable(Exception ex) => ex is not (OperationCanceledException or OutOfMemoryException or StackOverflowException or AccessViolationException);
}

internal sealed record TypeBuildResult(ITypeSourceModel? Model, ImmutableArray<Diagnostic> Diagnostics, Location Location);
