import fc from "fast-check";
import type { ESExpr } from "./index.js";

// UTF-8 cannot preserve lone UTF-16 surrogates.
const scalar = fc.oneof(
	fc.integer({ min: 0, max: 0xD7FF }),
	fc.integer({ min: 0xE000, max: 0x10FFFF }),
).map(value => String.fromCodePoint(value));
const string = fc.string({ unit: scalar, maxLength: 64 });
const name = fc.oneof(fc.constantFrom("list", "map", "set", "string-table", "custom", "key"), string);
const arrayConstraints = { maxLength: 32 };

function floatFromBits(width: 16 | 32 | 64): fc.Arbitrary<ESExpr> {
	return fc.bigInt({ min: 0n, max: (1n << BigInt(width)) - 1n }).map(bits => {
		const data = new DataView(new ArrayBuffer(width / 8));
		if (width === 16) {
			data.setUint16(0, Number(bits));
			const value = data.getFloat16(0);
			return Number.isNaN(value)
				? { type: "float16-nan", bits: Number(bits) }
				: { type: "float16", value };
		}
		else if (width === 32) {
			data.setUint32(0, Number(bits));
			const value = data.getFloat32(0);
			return Number.isNaN(value)
				? { type: "float32-nan", bits: Number(bits) }
				: { type: "float32", value };
		}
		else {
			data.setBigUint64(0, bits);
			const value = data.getFloat64(0);
			return Number.isNaN(value) ? { type: "float64-nan", bits } : value;
		}
	});
}

export const primitives: Record<string, fc.Arbitrary<ESExpr>> = {
	boolean: fc.boolean(),
	integer: fc.bigInt({ min: -(1n << 256n), max: 1n << 256n }),
	string,
	float16: floatFromBits(16),
	float32: floatFromBits(32),
	float64: floatFromBits(64),
	array8: fc.uint8Array(arrayConstraints),
	array16: fc.uint16Array(arrayConstraints),
	array32: fc.uint32Array(arrayConstraints),
	array64: fc.array(fc.bigInt({ min: 0n, max: (1n << 64n) - 1n }), arrayConstraints)
		.map(values => new BigUint64Array(values)),
	array128: fc.array(fc.uint8Array({ minLength: 16, maxLength: 16 }), { maxLength: 8 })
		.map(values => ({ type: "array128", value: new Uint8Array(values.flatMap(value => [...value])) })),
	null: fc.constant(null),
	nestedNull: fc.bigInt({ min: 1n, max: 1n << 256n }).map(level => ({ type: "null", level })),
};

export function expressions(depth: number): fc.Arbitrary<ESExpr> {
	const primitive = fc.oneof(...Object.values(primitives));
	if (depth === 0) {
		return primitive;
	}
	const child = expressions(depth - 1);
	const constructor: fc.Arbitrary<ESExpr> = fc.record({
		type: fc.constant("constructor" as const),
		name,
		args: fc.array(child, { maxLength: 4 }),
		kwargs: fc.uniqueArray(fc.tuple(name, child), { maxLength: 4, selector: entry => entry[0] })
			.map(entries => new Map(entries)),
	}).map(value => ({ ...value }));
	return fc.oneof(primitive, constructor);
}

