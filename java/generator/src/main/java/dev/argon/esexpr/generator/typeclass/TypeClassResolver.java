package dev.argon.esexpr.generator.typeclass;

import com.google.common.collect.ImmutableList;
import dev.argon.esexpr.TypeClassInstance;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

public final class TypeClassResolver {
	public TypeClassResolver(Types types, TypeClassLocalScope localScope) {
		this.types = types;
		this.localScope = localScope;
	}

	private final Types types;
	private final TypeClassLocalScope localScope;

	public @Nullable TypeClassResult resolve(TypeElement typeClass, TypeMirror type) {
		return resolve(types.getDeclaredType(typeClass, type));
	}

	public @Nullable TypeClassResult resolve(TypeMirror typeClassType) {
		return resolve(typeClassType, 10);
	}

	public TypeMirror getTypeClassType(TypeClassResult tc) {
		return switch(tc) {
			case TypeClassResult.Field(var field) -> field.asType();
			case TypeClassResult.Local(var local) -> local.asType();
			case TypeClassResult.Method method -> {
				if(method.arguments().size() != method.method().getParameters().size()) {
					throw new IllegalArgumentException("Method " + method.method() + " has incorrect number of arguments");
				}

				Map<String, TypeMirror> typeParams = new HashMap<>();

				for(int i = 0; i < method.arguments().size(); ++i) {
					var arg = method.arguments().get(i);
					var param = method.method().getParameters().get(i);

					var expectedType = getTypeClassType(arg);
					var actualType = param.asType();

					if(!unify(expectedType, actualType, typeParams)) {
						throw new IllegalArgumentException("Method " + method.method() + " has incorrect argument type at index " + i);
					}
				}

				var t = substituteTypes(method.method().getReturnType(), typeParams);
				if(t == null) {
					throw new IllegalArgumentException("Could not substitute types for method " + method.method());
				}

				yield t;
			}
		};
	}



	private @Nullable TypeClassResult resolve(TypeMirror typeClassType, int depthLimit) {
		if(depthLimit <= 0) {
			return null;
		}

		TypeClassResult result;

		if(typeClassType instanceof DeclaredType declaredType) {
			result = resolveInClass(declaredType.asElement(), typeClassType, depthLimit);
			if(result != null) {
				return result;
			}

			boolean hasCheckedLocal = false;
			for(var typeArg : declaredType.getTypeArguments()) {
				switch(typeArg.getKind()) {
					case DECLARED -> {
						var declaredTypeArg = (DeclaredType)typeArg;
						result = resolveInClass(declaredTypeArg.asElement(), typeClassType, depthLimit);
						if(result != null) {
							return result;
						}
					}

					case TYPEVAR -> {
						if(!hasCheckedLocal) {
							result = resolveLocal(typeClassType);
							if(result != null) {
								return result;
							}
							hasCheckedLocal = true;
						}
					}

					default -> {}
				}
			}
		}

		return null;
	}

	private @Nullable TypeClassResult resolveInClass(Element cls, TypeMirror typeClassType, int depthLimit) {
		candidateLoop: for(var elem : cls.getEnclosedElements()) {
			switch(elem.getKind()) {
				case METHOD -> {
					var method = (ExecutableElement)elem;
					if(!method.getModifiers().contains(Modifier.STATIC) || !method.getModifiers().contains(Modifier.PUBLIC)) {
						continue;
					}

					if(method.getAnnotation(TypeClassInstance.class) == null) {
						continue;
					}

					Map<String, TypeMirror> typeParams = new HashMap<>();
					if(unify(typeClassType, method.getReturnType(), typeParams)) {
						var arguments = ImmutableList.<TypeClassResult>builder();
						for(var arg : method.getParameters()) {
							var argType = substituteTypes(arg.asType(), typeParams);
							if(argType == null) {
								continue candidateLoop;
							}

							var argResult = resolve(argType, depthLimit - 1);
							if(argResult == null) {
								continue candidateLoop;
							}

							arguments.add(argResult);
						}

						return new TypeClassResult.Method(method, arguments.build());
					}
				}

				case FIELD -> {
					var field = (VariableElement)elem;
					if(!field.getModifiers().contains(Modifier.STATIC) || !field.getModifiers().contains(Modifier.PUBLIC)) {
						continue;
					}

					if(field.getAnnotation(TypeClassInstance.class) == null) {
						continue;
					}

					if(unify(typeClassType, field.asType(), Collections.emptyMap())) {
						return new TypeClassResult.Field(field);
					}
				}

				default -> {}
			}
		}

		return null;
	}

	private @Nullable TypeClassResult resolveLocal(TypeMirror typeClassType) {
		for(var local : localScope.typeClasses()) {
			if(expectedTypeMatches(local.asType(), typeClassType)) {
				return new TypeClassResult.Local(local);
			}
		}
		return null;
	}


	private @Nullable TypeMirror substituteTypes(TypeMirror t, Map<String, TypeMirror> typeParams) {
		return switch(t.getKind()) {
			case ARRAY -> {
				var array = (ArrayType)t;
				var componentType = substituteTypes(array.getComponentType(), typeParams);
				if(componentType == null) {
					yield null;
				}
				yield types.getArrayType(componentType);
			}
			case DECLARED -> {
				var declared = (DeclaredType)t;
				var args = declared.getTypeArguments();
				var newArgs = new TypeMirror[args.size()];
				for(int i = 0; i < args.size(); i++) {
					var arg = args.get(i);
					var newArg = substituteTypes(arg, typeParams);
					if(newArg == null) {
						yield null;
					}
					newArgs[i] = newArg;
				}
				yield types.getDeclaredType((TypeElement)declared.asElement(), newArgs);
			}
			case TYPEVAR -> {
				var typeVar = (TypeVariable)t;
				var name = typeVar.asElement().getSimpleName().toString();
				yield typeParams.get(name);
			}
			case WILDCARD -> {
				var wildcard = (WildcardType)t;

				var extendsBound = wildcard.getExtendsBound();
				var superBound = wildcard.getSuperBound();

				if(extendsBound != null) {
					extendsBound = substituteTypes(extendsBound, typeParams);
					if(extendsBound == null) {
						yield null;
					}
				}

				if(superBound != null) {
					superBound = substituteTypes(superBound, typeParams);
					if(superBound == null) {
						yield null;
					}
				}

				yield types.getWildcardType(extendsBound, superBound);
			}
			default -> t;
		};
	}

	private boolean unify(TypeMirror expectedType, TypeMirror actualType, Map<String, TypeMirror> typeParameters) {
		if(expectedType.getKind() == TypeKind.WILDCARD) {
			return true;
		}

		return switch(actualType.getKind()) {
			// Simple types
			case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID, NULL, NONE ->
				expectedType.getKind() == actualType.getKind();

			// Not types
			case ERROR, PACKAGE, MODULE, EXECUTABLE, OTHER -> false;

			// Unsupported types
			case WILDCARD, UNION, INTERSECTION -> false;


			case ARRAY ->
				expectedType.getKind() == TypeKind.ARRAY &&
					unify(
						((ArrayType)expectedType).getComponentType(),
						((ArrayType)actualType).getComponentType(),
						typeParameters
					);
			case DECLARED -> {
				if(expectedType.getKind() != TypeKind.DECLARED) {
					yield false;
				}

				var expectedTypeDeclared = (DeclaredType)expectedType;
				var actualTypeDeclared = (DeclaredType)actualType;

				var expectedTypeElement = (TypeElement)expectedTypeDeclared.asElement();
				var actualTypeElement = (TypeElement)actualTypeDeclared.asElement();


				if(!unify(expectedTypeDeclared.getEnclosingType(), actualTypeDeclared.getEnclosingType(), typeParameters)) {
					yield false;
				}

				if(!expectedTypeElement.getQualifiedName().toString().equals(actualTypeElement.getQualifiedName().toString())) {
					yield false;
				}

				var typeClassTypeArgs = expectedTypeDeclared.getTypeArguments();
				var actualTypeArgs = actualTypeDeclared.getTypeArguments();

				if(typeClassTypeArgs.size() != actualTypeArgs.size()) {
					yield false;
				}

				for(int i = 0; i < typeClassTypeArgs.size(); i++) {
					var typeClassTypeArg = typeClassTypeArgs.get(i);
					var actualTypeArg = actualTypeArgs.get(i);

					if(!unify(typeClassTypeArg, actualTypeArg, typeParameters)) {
						yield false;
					}
				}

				yield true;
			}
			case TYPEVAR -> {
				var actualTypeVar = (TypeVariable)actualType;

				var knownParamValue = typeParameters.get(actualTypeVar.asElement().getSimpleName().toString());
				if(knownParamValue == null) {
					typeParameters.put(actualTypeVar.asElement().getSimpleName().toString(), expectedType);
					yield true;
				}
				else {
					yield expectedTypeMatches(expectedType, knownParamValue);
				}
			}
		};
	}

	private boolean expectedTypeMatches(TypeMirror a, TypeMirror b) {
		if(a.getKind() != b.getKind()) {
			return false;
		}

		return switch(a.getKind()) {
			// Simple types
			case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID, NULL, NONE ->
				true;

			// Not types
			case ERROR, PACKAGE, MODULE, EXECUTABLE, OTHER -> false;

			// Unsupported types
			case WILDCARD, UNION, INTERSECTION -> false;


			case ARRAY ->
				expectedTypeMatches(
					((ArrayType)a).getComponentType(),
					((ArrayType)b).getComponentType()
				);

			case DECLARED -> {
				var aDeclared = (DeclaredType)a;
				var bDeclared = (DeclaredType)b;

				var aElement = (TypeElement)aDeclared.asElement();
				var bElement = (TypeElement)bDeclared.asElement();

				if(!expectedTypeMatches(aDeclared.getEnclosingType(), bDeclared.getEnclosingType())) {
					yield false;
				}

				if(!aElement.getQualifiedName().toString().equals(bElement.getQualifiedName().toString())) {
					yield false;
				}

				var aTypeArgs = aDeclared.getTypeArguments();
				var bTypeArgs = bDeclared.getTypeArguments();

				if(aTypeArgs.size() != bTypeArgs.size()) {
					yield false;
				}

				for(int i = 0; i < aTypeArgs.size(); i++) {
					var typeClassTypeArg = aTypeArgs.get(i);
					var actualTypeArg = bTypeArgs.get(i);

					if(!expectedTypeMatches(typeClassTypeArg, actualTypeArg)) {
						yield false;
					}
				}

				yield true;
			}
			case TYPEVAR -> {
				var aTypeVar = (TypeVariable)a;
				var bTypeVar = (TypeVariable)b;

				yield aTypeVar.asElement().getSimpleName().toString().equals(bTypeVar.asElement().getSimpleName().toString());
			}
		};
	}



}
