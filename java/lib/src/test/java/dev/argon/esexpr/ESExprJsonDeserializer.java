package dev.argon.esexpr;

import org.eclipse.collections.api.factory.primitive.ByteLists;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.ShortLists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

public class ESExprJsonDeserializer extends JsonDeserializer<ESExpr> {

	@Override
	public ESExpr deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
		ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
		JsonNode node = mapper.readTree(jsonParser);

		if(node.isBoolean()) {
			return new ESExpr.Bool(node.asBoolean());
		}
		else if(node.isTextual()) {
			return new ESExpr.Str(node.asText());
		}
		else if(node.isNull()) {
			return new ESExpr.Null(BigInteger.ZERO);
		}
		else if(node.isArray()) {
			List<ESExpr> l = new ArrayList<>();
			for(var elem : node) {
				var value = mapper.convertValue(elem, ESExpr.class);
				l.add(value == null ? new ESExpr.Null(BigInteger.ZERO) : value);
			}

			return new ESExpr.Constructor(
				"list",
				l,
				new HashMap<>()
			);
		}
		else if(node.isObject()) {
			if(node.has("constructor_name")) {
				var name = node.get("constructor_name").asText();

				List<ESExpr> args = new ArrayList<>();
				if(node.has("args")) {
					for(var elem : node.get("args")) {
						args.add(mapper.convertValue(elem, ESExpr.class));
					}
				}

				Map<String, ESExpr> kwargs = new HashMap<>();
				if(node.has("kwargs")) {
					kwargs = mapper.convertValue(node.get("kwargs"), new TypeReference<>() {});
				}
				for(var entry : kwargs.entrySet()) {
					if(entry.getValue() == null) {
						entry.setValue(new ESExpr.Null(BigInteger.ZERO));
					}
				}

				return new ESExpr.Constructor(name, args, kwargs);
			}
			else if(node.has("int")) {
				return new ESExpr.Int(new BigInteger(node.get("int").asText()));
			}
			else if(node.has("float16")) {
				var fv = node.get("float16");
				short value;
				if(fv.isTextual()) {
					value = switch (fv.asText()) {
						case "+inf" -> Float.floatToFloat16(Float.POSITIVE_INFINITY);
						case "-inf" -> Float.floatToFloat16(Float.NEGATIVE_INFINITY);
						default -> throw new RuntimeException("Unexpected float text");
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
				if(fv.isTextual()) {
					value = switch (fv.asText()) {
						case "+inf" -> Float.POSITIVE_INFINITY;
						case "-inf" -> Float.NEGATIVE_INFINITY;
						default -> throw new RuntimeException("Unexpected float text");
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
				if(fv.isTextual()) {
					value = switch (fv.asText()) {
						case "+inf" -> Double.POSITIVE_INFINITY;
						case "-inf" -> Double.NEGATIVE_INFINITY;
						default -> throw new RuntimeException("Unexpected float text");
					};
				}
				else {
					value = fv.doubleValue();
				}

				return new ESExpr.Float64(value);
			}
			else if(node.has("base64")) {
				byte[] bytes = Base64.getDecoder().decode(node.get("base64").asText());
				return new ESExpr.Array8(ByteLists.immutable.of(bytes));
			}
			else if(node.has("array8")) {
				var array = node.get("array8");
				byte[] bytes = new byte[array.size()];
				for (int i = 0; i < array.size(); i++) {
					bytes[i] = getInt(array.get(i)).byteValue();
				}
				return new ESExpr.Array8(ByteLists.immutable.of(bytes));
			}
			else if(node.has("array16")) {
				var array = node.get("array16");
				short[] shorts = new short[array.size()];
				for (int i = 0; i < array.size(); i++) {
					shorts[i] = getInt(array.get(i)).shortValue();
				}
				return new ESExpr.Array16(ShortLists.immutable.of(shorts));
			}
			else if(node.has("array32")) {
				var array = node.get("array32");
				int[] ints = new int[array.size()];
				for (int i = 0; i < array.size(); i++) {
					ints[i] = getInt(array.get(i)).intValue();
				}
				return new ESExpr.Array32(IntLists.immutable.of(ints));
			}
			else if(node.has("array64")) {
				var array = node.get("array64");
				long[] longs = new long[array.size()];
				for (int i = 0; i < array.size(); i++) {
					longs[i] = getInt(array.get(i)).longValue();
				}
				return new ESExpr.Array64(LongLists.immutable.of(longs));
			}
			else if(node.has("array128")) {
				var array = node.get("array128");
				long[] longs = new long[array.size() * 2];
				for (int i = 0; i < array.size(); i++) {
					BigInteger value = getInt(array.get(i));
					longs[i * 2] = value.longValue();
					longs[i * 2 + 1] = value.shiftLeft(64).longValue();
				}
				return new ESExpr.Array128(LongLists.immutable.of(longs));
			}
			else if(node.has("null")) {
				return new ESExpr.Null(new BigInteger(node.get("null").asText()));
			}
		}

		throw JsonMappingException.from(jsonParser, "Unexpected JSON for ESExpr value");
	}
	
	private static BigInteger getInt(JsonNode node) {
		if(node.isTextual()) {
			return new BigInteger(node.asText());
		}
		else {
			return BigInteger.valueOf(node.asLong());	
		}
	}

}
