package dev.argon.esexpr.generator.typeclass;


import com.google.common.collect.ImmutableList;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

public sealed interface TypeClassResult {

	record Local(VariableElement localVar) implements TypeClassResult {}
	record Method(ExecutableElement method, ImmutableList<TypeClassResult> arguments) implements TypeClassResult {}
	record Field(VariableElement field) implements TypeClassResult {}

}
