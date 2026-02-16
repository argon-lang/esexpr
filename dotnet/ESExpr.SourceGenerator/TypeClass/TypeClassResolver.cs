using System;
using Microsoft.CodeAnalysis;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Diagnostics;
using System.Linq;
using static ESExpr.SourceGenerator.GenUtils;

namespace ESExpr.SourceGenerator.TypeClass;

internal sealed class TypeClassResolver {
	public TypeClassResolver(Compilation compilation, List<LocalInfo> localScope) {
		this.compilation = compilation;
		this.localScope = localScope;

	}
	
	private readonly Compilation compilation;
	private readonly List<LocalInfo> localScope;

	public TypeClassResult? Resolve(ITypeSymbol typeSymbol) {
		return Resolve(typeSymbol, 10);
	}

	public ITypeSymbol GetTypeClassType(TypeClassResult typeClass) {
		return typeClass switch {
			TypeClassResult.Local { LocalInfo: var local } => local.Type,
			TypeClassResult.Property { PropertySymbol: var property } => property.Type,
			TypeClassResult.Method method => GetTypeForMethod(method),
			_ => throw new ArgumentOutOfRangeException(nameof(typeClass))
		};

		ITypeSymbol GetTypeForMethod(TypeClassResult.Method method) {
			if(method.Arguments.Count != method.MethodSymbol.Parameters.Length) {
				throw new ArgumentException("Method arguments count does not match parameter count", nameof(method));
			}
			
			var typeParams = new Dictionary<ITypeParameterSymbol, ITypeSymbol>(SymbolEqualityComparer.Default);
			
			foreach(var (arg, param) in method.Arguments.Zip(method.MethodSymbol.Parameters, (arg, param) => (arg, param))) {
				var argType = GetTypeClassType(arg);
				if(!Unify(argType, param.Type, typeParams)) {
					throw new ArgumentException("Could not unify argument type with parameter type", nameof(method));
				}
			}
			
			return SubstituteTypes(method.MethodSymbol.ReturnType, typeParams)
				?? throw new ArgumentException("Could not substitute return type", nameof(method));
		}
	}

	private TypeClassResult? Resolve(ITypeSymbol typeSymbol, int depthLimit) {
		if(depthLimit <= 0) {
			return null;
		}

		TypeClassResult? result;

		if(typeSymbol is INamedTypeSymbol nst) {
			result = ResolveInClass(nst, typeSymbol, depthLimit);
			if(result is not null) {
				return result;
			}

			bool hasCheckedLocal = false;
			foreach(var typeArg in nst.TypeArguments) {
				switch(typeArg) {
					case INamedTypeSymbol nt:
						result = ResolveInClass(nt, typeSymbol, depthLimit);
						if(result is not null) {
							return result;
						}
						break;
					
					case ITypeParameterSymbol tp:
						if(!hasCheckedLocal) {
							result = ResolveLocal(typeSymbol);
							if(result is not null) {
								return result;
							}
							hasCheckedLocal = true;
						}
						break;
				}
			}
		}

		return null;
	}

	private TypeClassResult? ResolveInClass(INamedTypeSymbol declaringType, ITypeSymbol t, int depthLimit) {
		if(!EnsureNonGenericType(ref declaringType)) {
			return null;
		}
		
		foreach(var member in declaringType.GetMembers()) {
			switch(member) {
				case IMethodSymbol method: {
					if(!method.IsStatic || method.DeclaredAccessibility != Accessibility.Public) {
						continue;
					}

					if(!HasAttribute(method, "ESExpr.Runtime.TypeClassInstanceAttribute")) {
						continue;
					}

					var typeParams = new Dictionary<ITypeParameterSymbol, ITypeSymbol>(SymbolEqualityComparer.Default);
					if(Unify(t, method.ReturnType, typeParams)) {
						var arguments = ImmutableList.CreateBuilder<TypeClassResult>();
						foreach(var arg in method.Parameters) {
							var argType = SubstituteTypes(arg.Type, typeParams);
							if(argType is null) {
								goto nextCandidate;
							}
							
							var argResult = Resolve(argType, depthLimit - 1);
							if(argResult is null) {
								goto nextCandidate;
							}
							
							arguments.Add(argResult);
						}

						var typeArguments = ImmutableList.CreateBuilder<ITypeSymbol>();
						foreach(var typeParam in typeParams.Keys) {
							if(!typeParams.TryGetValue(typeParam, out var value)) {
								goto nextCandidate;
							}
							
							if(!CheckConstraints(typeParam, value)) {
								goto nextCandidate;
							}
							
							typeArguments.Add(value);
						}
						
						return new TypeClassResult.Method(
							typeArguments.ToImmutable(),
							arguments.ToImmutable(),
							method
						);
					}

					break;
				}

				case IPropertySymbol property: {
					if(!property.IsStatic || property.DeclaredAccessibility != Accessibility.Public) {
						continue;
					}

					if(!HasAttribute(property, "ESExpr.Runtime.TypeClassInstanceAttribute")) {
						continue;
					}

					var typeParams = new Dictionary<ITypeParameterSymbol, ITypeSymbol>(SymbolEqualityComparer.Default);
					if(Unify(t, property.Type, typeParams)) {
						return new TypeClassResult.Property(property);
					}
					
					break;
				}
			}
			
			nextCandidate: ;
		}

		return null;
	}

	private TypeClassResult? ResolveLocal(ITypeSymbol typeSymbol) {
		foreach(var local in localScope) {
			if(ExpectedTypeMatches(local.Type, typeSymbol)) {
				return new TypeClassResult.Local(local);
			}
		}
		
		return null;
	}

	private bool Unify(ITypeSymbol expectedType, ITypeSymbol actualType, Dictionary<ITypeParameterSymbol, ITypeSymbol> typeParams) {
		if(expectedType is INamedTypeSymbol expectedNamed && actualType is INamedTypeSymbol actualNamed) {
			if(!SymbolEqualityComparer.Default.Equals(
				   expectedNamed.ConstructedFrom,
				   actualNamed.ConstructedFrom
			)) {
				return false;
			}

			if(expectedNamed.TypeArguments.Length != actualNamed.TypeArguments.Length) {
				return false;
			}

			foreach(var (expected, actual) in expectedNamed.TypeArguments.Zip(actualNamed.TypeArguments, (expected, actual) => (expected, actual))) {
				if(!Unify(expected, actual, typeParams)) {
					return false;
				}
			}

			return true;
		}
		else if(expectedType is IDynamicTypeSymbol) {
			return true;
		}
		else if(expectedType is IPointerTypeSymbol expectedPointer && actualType is IPointerTypeSymbol actualPointer) {
			return Unify(expectedPointer.PointedAtType, actualPointer.PointedAtType, typeParams);
		}
		else if(expectedType is IArrayTypeSymbol expectedArray && actualType is IArrayTypeSymbol actualArray) {
			return Unify(expectedArray.ElementType, actualArray.ElementType, typeParams);
		}
		else if(actualType is ITypeParameterSymbol actualTP) {
			if(!typeParams.TryGetValue(actualTP, out var knownParamValue)) {
				typeParams.Add(actualTP, expectedType);
				return true;
			}
			
			return ExpectedTypeMatches(knownParamValue, expectedType);
		}
		else {
			return false;
		} 
	}

	public ITypeSymbol? SubstituteTypes(ITypeSymbol t, Dictionary<ITypeParameterSymbol, ITypeSymbol> typeParams) {
		switch(t) {
			case ITypeParameterSymbol tp:
				return typeParams.TryGetValue(tp, out var value) ? value : null;

			case INamedTypeSymbol nt: {
				if(!nt.IsGenericType) {
					return nt;
				}
				
				var substArgs = new ITypeSymbol[nt.TypeArguments.Length];
				int i = 0;
				foreach(var typeArg in nt.TypeArguments) {
					var substArg = SubstituteTypes(typeArg, typeParams);
					if(substArg is null) {
						return null;
					}
					substArgs[i] = substArg;
					++i;
				}
				
				return nt.ConstructedFrom.Construct(substArgs);
			}

			case IPointerTypeSymbol pt: {
				var substElem = SubstituteTypes(pt.PointedAtType, typeParams);
				if(substElem is null) {
					return null;
				}
				
				return compilation.CreatePointerTypeSymbol(substElem);
			}

			case IArrayTypeSymbol at: {
				var substElem = SubstituteTypes(at.ElementType, typeParams);
				if(substElem is null) {
					return null;
				}
				
				return compilation.CreateArrayTypeSymbol(substElem, at.Rank);
			}
			
			default:
				return t;
		}
	}

	private bool ExpectedTypeMatches(ITypeSymbol a, ITypeSymbol b) {
		if(a is INamedTypeSymbol anst && b is INamedTypeSymbol bnst) {
			return SymbolEqualityComparer.Default.Equals(anst.ConstructedFrom, bnst.ConstructedFrom) &&
				   anst.TypeArguments.Length == bnst.TypeArguments.Length &&
				   anst.TypeArguments.Zip(bnst.TypeArguments, ExpectedTypeMatches).All(x => x);
		}
		else if(a is IArrayTypeSymbol aa && b is IArrayTypeSymbol ba) {
			return ExpectedTypeMatches(aa.ElementType, ba.ElementType);
		}
		else if(a is IPointerTypeSymbol ap && b is IPointerTypeSymbol bp) {
			return ExpectedTypeMatches(ap.PointedAtType, bp.PointedAtType);
		}
		else if(a is IDynamicTypeSymbol || b is IDynamicTypeSymbol) {
			return true;
		}
		else if(a is ITypeParameterSymbol tp && b is ITypeParameterSymbol tb) {
			return a.Name == b.Name;
		}
		else {
			return SymbolEqualityComparer.Default.Equals(a, b);			
		}
	}

	private bool CheckConstraints(ITypeParameterSymbol tp, ITypeSymbol typeSymbol) {
		if(tp.HasValueTypeConstraint && !typeSymbol.IsValueType) {
			return false;
		}
							
		if(tp.HasReferenceTypeConstraint && typeSymbol.IsValueType) {
			return false;
		}

		if(tp.HasUnmanagedTypeConstraint && !typeSymbol.IsUnmanagedType) {
			return false;
		}

		if(tp.HasNotNullConstraint && typeSymbol.NullableAnnotation == NullableAnnotation.Annotated) {
			return false;
		}

		if(tp.HasConstructorConstraint) {
			if(typeSymbol is not INamedTypeSymbol nt) {
				return false;
			}
			
			if(!nt.Constructors.Any(ctor => ctor.Parameters.Length == 0 && ctor.DeclaredAccessibility == Accessibility.Public)) {
				return false;
			}
		}
			
		
		foreach(var constraint in tp.ConstraintTypes) {
			if(!SatisfiesTypeConstraint(typeSymbol, constraint)) {
				return false;
			}
		}

		return true;
	}
	
	private bool SatisfiesTypeConstraint(ITypeSymbol type, ITypeSymbol constraint) {
		if (SymbolEqualityComparer.Default.Equals(type, constraint))
			return true;

		for(var current = type.BaseType; current != null; current = current.BaseType) {
			if(SymbolEqualityComparer.Default.Equals(current, constraint))
				return true;
		}

		foreach(var iface in type.AllInterfaces) {
			if(SymbolEqualityComparer.Default.Equals(iface, constraint))
				return true;
		}

		return false;
	}
	
	
}
