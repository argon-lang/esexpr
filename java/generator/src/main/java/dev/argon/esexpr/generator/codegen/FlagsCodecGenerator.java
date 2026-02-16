package dev.argon.esexpr.generator.codegen;

import dev.argon.esexpr.Constructor;
import dev.argon.esexpr.FlagMask;
import dev.argon.esexpr.generator.lookup.MetadataCache;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class FlagsCodecGenerator extends GeneratorBase {
	public FlagsCodecGenerator(PrintWriter writer, ProcessingEnvironment env, MetadataCache metadataCache, TypeElement elem) {
		super(writer, env, metadataCache, elem);
	}

	@Override
	protected void validateAnnotations() throws AbortException {
		if(elem.getAnnotation(Constructor.class) != null) {
			throw new AbortException("Constructor name may not be specified for flags", elem);
		}
	}

	@Override
	protected void writeEncodedEqualImpl() throws IOException, AbortException {
		println("return x.equals(y);");
	}

	@Override
	protected void writeEncodeImpl() throws IOException, AbortException {

		println("var bits = java.math.BigInteger.ZERO;");

		var usedBits = BigInteger.ZERO;

		for(var f : getFields(elem)) {
			var t = f.asType();

			BigInteger mask = null;

			switch(t.getKind()) {
				case BOOLEAN -> {
					mask = getFlagMask(f);
					var bitIndex = getFlagBitIndex(mask);

					print("if(value.");
					print(f.getSimpleName());
					print("()) bits = bits.setBit(");
					print(Integer.toString(bitIndex));
					println(");");
				}

				case DECLARED -> {
					var dt = (DeclaredType)t;
					var typeElem = dt.asElement();

					if(typeElem.getKind() == ElementKind.ENUM) {
						print("switch(value.");
						print(f.getSimpleName());
						println("()) {");
						indent();

						var maskMap = getEnumMaskMap(typeElem);
						mask = getEnumMask(maskMap);

						var usedValues = new HashSet<BigInteger>();
						for(var e : maskMap.entrySet()) {
							print("case ");
							print(e.getKey());
							print(" -> bits = bits.or(");
							writeBigIntegerLiteral(e.getValue());
							println(");");

							if(!usedValues.add(e.getValue())) {
								throw new AbortException("Duplicate enum mask for " + e.getKey(), f);
							}
						}

						dedent();
						println("}");
					}
				}

				default -> {}
			}

			if(mask == null) {
				throw new AbortException("Invalid field type for flags: " + t, f);
			}


			if(!usedBits.and(mask).equals(BigInteger.ZERO)) {
				throw new AbortException("Duplicate flag mask: " + mask, f);
			}

			usedBits = usedBits.or(mask);
		}

		println("return new dev.argon.esexpr.ESExpr.Int(bits);");
	}

	@Override
	protected void writeDecodeImpl() throws IOException, AbortException {
		println("if(!(expr instanceof dev.argon.esexpr.ESExpr.Int(var bits))) {");
		indent();
		println("throw new dev.argon.esexpr.DecodeException(\"Expected Int\", path);");
		dedent();
		println("}");

		for(var f : getFields(elem)) {
			var t = f.asType();

			switch(t.getKind()) {
				case BOOLEAN -> {
					var mask = getFlagMask(f);
					var bitIndex = getFlagBitIndex(mask);

					print("boolean field_");
					print(f.getSimpleName());
					print(" = bits.testBit(");
					print(Integer.toString(bitIndex));
					println(");");
					continue;
				}
				case DECLARED -> {
					var dt = (DeclaredType)t;
					var typeElem = (TypeElement)dt.asElement();

					if(typeElem.getKind() == ElementKind.ENUM) {
						var maskMap = getEnumMaskMap(typeElem);

						print("var field_");
						print(f.getSimpleName());
						print(" = switch(bits.and(");
						writeBigIntegerLiteral(getEnumMask(maskMap));
						println(")) {");
						indent();

						for(var mapping : maskMap.entrySet()) {
							print("case java.math.BigInteger enumBits when enumBits.equals(");
							writeBigIntegerLiteral(mapping.getValue());
							print(") -> ");
							print(typeElem.getQualifiedName());
							print(".");
							print(mapping.getKey());
							println(";");
						}

						println("default -> throw new dev.argon.esexpr.DecodeException(\"Invalid enum value\", path);");

						dedent();
						println("};");

						continue;
					}
				}

				default -> {}
			}

			throw new AbortException("Invalid field type for flags: " + t, f);
		}


		print("return new ");
		print(elem.getQualifiedName());
		printTypeArguments();
		print("(");

		int i = 0;
		for(var field : getFields(elem)) {
			if(i > 0) {
				print(", ");
			}
			++i;

			print("field_");
			print(field.getSimpleName());
		}

		println(");");

	}


	private BigInteger getFlagMask(Element e) throws AbortException {
		var ann = e.getAnnotation(FlagMask.class);
		if(ann == null) {
			throw new AbortException("Missing @FlagMask", e);
		}

		BigInteger result;
		var bitStr = ann.bits();
		if(!bitStr.isEmpty()) {
			try {
				if(bitStr.startsWith("0x") || bitStr.startsWith("0X")) {
					result = new BigInteger(bitStr.substring(2), 16);
				}
				else if(bitStr.startsWith("0b") || bitStr.startsWith("0B")) {
					result = new BigInteger(bitStr.substring(2), 2);
				}
				else if (bitStr.startsWith("0")) {
					result = new BigInteger(bitStr, 8);
				}
				else {
					result = new BigInteger(ann.bits());
				}
			}
			catch(NumberFormatException ex) {
				throw new AbortException("Invalid flag mask", e);
			}
		}
		else {
			result = BigInteger.valueOf(ann.value());
		}

		if(result.signum() < 0) {
			throw new AbortException("Flag mask must not be negative", e);
		}

		return result;
	}

	private int getFlagBitIndex(BigInteger mask) throws AbortException {
		if(mask.bitCount() != 1) {
			throw new AbortException("Flag mask must be a single bit", elem);
		}

		return mask.getLowestSetBit();
	}

	private Map<String, BigInteger> getEnumMaskMap(Element enumElem) throws AbortException {
		var map = new HashMap<String, BigInteger>();
		for(var e : enumElem.getEnclosedElements()) {
			if(e.getKind() != ElementKind.ENUM_CONSTANT) {
				continue;
			}

			var mask = getFlagMask(e);
			map.put(e.getSimpleName().toString(), mask);
		}

		return map;
	}

	private BigInteger getEnumMask(Map<String, BigInteger> maskMap) throws AbortException {
		var result = BigInteger.ZERO;
		for(var v : maskMap.values()) {
			result = result.or(v);
		}

		return result;
	}

	private void writeBigIntegerLiteral(BigInteger value) throws IOException {
		if(value.equals(BigInteger.ZERO)) {
			print("java.math.BigInteger.ZERO");
		}
		else if(value.equals(BigInteger.ONE)) {
			print("java.math.BigInteger.ONE");
		}
		else if(value.equals(BigInteger.TWO)) {
			print("java.math.BigInteger.TWO");
		}
		else if(value.equals(BigInteger.TEN)) {
			print("java.math.BigInteger.TEN");
		}
		else if(value.bitLength() < 64) {
			print("java.math.BigInteger.valueOf(");
			print(value.toString());
			print(")");
		}
		else {
			print("new java.math.BigInteger(\"");
			print(value.toString());
			print("\")");
		}
	}

}
