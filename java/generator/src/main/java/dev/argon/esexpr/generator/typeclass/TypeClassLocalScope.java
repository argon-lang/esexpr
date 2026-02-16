package dev.argon.esexpr.generator.typeclass;

import javax.lang.model.element.VariableElement;
import java.util.List;
import java.util.Map;

public record TypeClassLocalScope(
	List<VariableElement> typeClasses
) {
}
