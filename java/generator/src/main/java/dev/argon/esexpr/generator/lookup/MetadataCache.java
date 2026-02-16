package dev.argon.esexpr.generator.lookup;

import dev.argon.esexpr.generator.typeclass.TypeClassResolver;
import dev.argon.esexpr.generator.typeclass.TypeClassResult;
import org.jspecify.annotations.Nullable;

import javax.lang.model.type.*;
import java.util.*;

public class MetadataCache {

	private final Map<TypeMirror, TypeClassResult> resolvedTypeClasses = new HashMap<>();

	@Nullable TypeClassResult resolveTypeClass(TypeClassResolver resolver, TypeMirror type) {
		if(resolvedTypeClasses.containsKey(type)) {
			return resolvedTypeClasses.get(type);
		}

		var result = resolver.resolve(type);

		if(result != null && !hasLocal(result)) {
			resolvedTypeClasses.put(type, result);
		}

		return result;
	}

	private static boolean hasLocal(TypeClassResult result) {
		return switch(result) {
			case TypeClassResult.Field _ -> false;
			case TypeClassResult.Local _ -> true;
			case TypeClassResult.Method method ->
				method.arguments().stream().anyMatch(MetadataCache::hasLocal);
		};
	}

}
