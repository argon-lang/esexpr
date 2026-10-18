package dev.argon.esexpr.tests;

import dev.argon.esexpr.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.collections.api.factory.primitive.ByteLists;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.ShortLists;
import org.eclipse.collections.impl.factory.Lists;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;

import java.math.BigInteger;
import java.util.*;

public class ESExprJsonDeserializer extends ValueDeserializer<ESExpr> {

	@Override
	public ESExpr deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
		throws JacksonException {
		return parseJsonNode(deserializationContext.readTree(jsonParser), deserializationContext);
	}

	private static ESExpr parseJsonNode(JsonNode node, DeserializationContext deserializationContext)
		throws JacksonException {
		if(node.isBoolean()) {
			return new ESExpr.Bool(node.asBoolean());
		}
		else if(node.isIntegralNumber()) {
			return new ESExpr.Int(node.bigIntegerValue());
		}
		else if(node.isString()) {
			return new ESExpr.Str(node.asString());
		}
		else if(node.isNull()) {
			return new ESExpr.Null(BigInteger.ZERO);
		}
		else if(node.isArray()) {
			var l = ImmutableList.<ESExpr>builder();
			for(var elem : node) {
				l.add(parseJsonNode(elem, deserializationContext));
			}

			return new ESExpr.Constructor("list", l.build(), ImmutableMap.of());
		}
		else if(node.isObject()) {
			if(node.has("constructor_name")) {
				var name = node.get("constructor_name").asString();

				var args = ImmutableList.<ESExpr>builder();
				if(node.has("args")) {
					for(var elem : node.get("args")) {
						args.add(parseJsonNode(elem, deserializationContext));
					}
				}

				Map<String, ESExpr> kwargs = new HashMap<>();
				if(node.has("kwargs")) {
					for(var entry : node.get("kwargs").properties()) {
						kwargs.put(entry.getKey(), parseJsonNode(entry.getValue(), deserializationContext));
					}
				}

				return new ESExpr.Constructor(name, args.build(), ImmutableMap.copyOf(kwargs));
			}
			else if(node.has("int")) {
				return new ESExpr.Int(new BigInteger(node.get("int").asString()));
			}
			else if(node.has("float16")) {
				var fv = node.get("float16");
				short value;
				if(fv.isString()) {
					value = switch(fv.asString()) {
						case "+inf" -> Float.floatToFloat16(Float.POSITIVE_INFINITY);
						case "-inf" -> Float.floatToFloat16(Float.NEGATIVE_INFINITY);
						case "nan" -> Float.floatToFloat16(Float.NaN);
						default -> deserializationContext.reportInputMismatch(ESExpr.class, "Unexpected float text");
					};
				}
				else {
					value = Float.floatToFloat16(fv.floatValue());
				}

				return new ESExpr.Float16(value);
			}
			else if(node.has("float32")) {
				var fv = node.get("float32");
				float value;
				if(fv.isString()) {
					value = switch(fv.asString()) {
						case "+inf" -> Float.POSITIVE_INFINITY;
						case "-inf" -> Float.NEGATIVE_INFINITY;
						case "nan" -> Float.NaN;
						default -> deserializationContext.reportInputMismatch(ESExpr.class, "Unexpected float text");
					};
				}
				else {
					value = fv.floatValue();
				}

				return new ESExpr.Float32(value);
			}
			else if(node.has("float64")) {
				var fv = node.get("float64");
				double value;
				if(fv.isString()) {
					value = switch(fv.asString()) {
						case "+inf" -> Double.POSITIVE_INFINITY;
						case "-inf" -> Double.NEGATIVE_INFINITY;
						case "nan" -> Double.NaN;
						default -> deserializationContext.reportInputMismatch(ESExpr.class, "Unexpected float text");
					};
				}
				else {
					value = fv.doubleValue();
				}

				return new ESExpr.Float64(value);
			}
			else if(node.has("base64")) {
				byte[] bytes = Base64.getDecoder().decode(node.get("base64").asString());
				return new ESExpr.Array8(ByteLists.immutable.of(bytes));
			}
			else if(node.has("array8")) {
				var array = node.get("array8");
				byte[] bytes = new byte[array.size()];
				for(int i = 0; i < array.size(); i++) {
					bytes[i] = getInt(array.get(i)).byteValue();
				}
				return new ESExpr.Array8(ByteLists.immutable.of(bytes));
			}
			else if(node.has("array16")) {
				var array = node.get("array16");
				short[] shorts = new short[array.size()];
				for(int i = 0; i < array.size(); i++) {
					shorts[i] = getInt(array.get(i)).shortValue();
				}
				return new ESExpr.Array16(ShortLists.immutable.of(shorts));
			}
			else if(node.has("array32")) {
				var array = node.get("array32");
				int[] ints = new int[array.size()];
				for(int i = 0; i < array.size(); i++) {
					ints[i] = getInt(array.get(i)).intValue();
				}
				return new ESExpr.Array32(IntLists.immutable.of(ints));
			}
			else if(node.has("array64")) {
				var array = node.get("array64");
				long[] longs = new long[array.size()];
				for(int i = 0; i < array.size(); i++) {
					longs[i] = getInt(array.get(i)).longValue();
				}
				return new ESExpr.Array64(LongLists.immutable.of(longs));
			}
			else if(node.has("array128")) {
				var array = node.get("array128");
				long[] longs = new long[array.size() * 2];
				for(int i = 0; i < array.size(); i++) {
					BigInteger value = getInt(array.get(i));
					longs[i * 2] = value.longValue();
					longs[i * 2 + 1] = value.shiftRight(64).longValue();
				}
				return new ESExpr.Array128(LongLists.immutable.of(longs));
			}
			else if(node.has("null")) {
				return new ESExpr.Null(node.get("null").bigIntegerValue());
			}
		}

		return deserializationContext.reportInputMismatch(ESExpr.class, "Unexpected JSON for ESExpr value");
	}

	private static BigInteger getInt(JsonNode node) {
		if(node.isString()) {
			return new BigInteger(node.asString());
		}
		else {
			return node.bigIntegerValue();
		}
	}

}
