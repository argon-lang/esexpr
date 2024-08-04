package dev.argon.esexpr;

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
			return new ESExpr.Null();
		}
		else if(node.isArray()) {
			List<ESExpr> l = new ArrayList<>();
			for(var elem : node) {
				l.add(mapper.convertValue(elem, ESExpr.class));
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

				return new ESExpr.Constructor(name, args, kwargs);
			}
			else if(node.has("int")) {
				return new ESExpr.Int(new BigInteger(node.get("int").asText()));
			}
			else if(node.has("float32")) {
				return new ESExpr.Float32(node.get("float32").floatValue());
			}
			else if(node.has("float64")) {
				return new ESExpr.Float64(node.get("float64").doubleValue());
			}
			else if(node.has("base64")) {
				return new ESExpr.Binary(Base64.getDecoder().decode(node.get("base64").asText()));
			}
		}

		throw JsonMappingException.from(jsonParser, "Unexpected JSON for ESExpr value");
	}

}
