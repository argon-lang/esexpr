import { test } from "node:test";
import assert from "node:assert/strict";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import fc from "fast-check";
import type { ESExpr } from "./index.js";
import * as text from "./text_format.js";
import * as binary from "./binary_format.js";
import { expressions, primitives } from "./expression_arbitraries.test.js";

async function* chunks(source: string, size = 1): AsyncIterable<string> {
	yield "";
	for(let offset = 0; offset < source.length; offset += size) yield source.slice(offset, offset + size);
	yield "";
}

async function decode(source: string, size = 1): Promise<ESExpr[]> {
	return Array.fromAsync(text.readExprStream(chunks(source, size)));
}

async function format(expr: ESExpr, options: text.PrintOptions = {}): Promise<string> {
	return (await Array.fromAsync(text.writeExpr(expr, options))).join("");
}

const fixtureDir = path.join(import.meta.dirname, "../../tests");
for(const file of await fs.readdir(fixtureDir)) {
	if(!file.endsWith(".esx")) continue;
	test(`text Rust fixture ${file} matches binary fixture`, async() => {
		const source = await fs.readFile(path.join(fixtureDir, file), "utf8");
		const bytes = await fs.readFile(path.join(fixtureDir, file.replace(/\.esx$/, ".esxb")));
		async function* input(): AsyncIterable<Uint8Array> {
			yield bytes;
		}
		const expected = await Array.fromAsync(binary.readExprStream(input()));
		const parsed = await decode(source);
		assert.deepStrictEqual(parsed, expected);
		assert.deepStrictEqual(await Array.fromAsync(text.readExprStream(text.writeExprs(parsed))), expected);
	});
}

for(const [name, arbitrary] of Object.entries(primitives)) {
	test(`text ${name} round trips`, async() => {
		await fc.assert(fc.asyncProperty(arbitrary, async expr => {
			assert.deepStrictEqual(await decode(await format(expr)), [expr]);
		}), { numRuns: 200 });
	});
}

for(const indentation of [undefined, "", "  ", "\t"]) {
	test(`text recursive streams round trip with indentation ${JSON.stringify(indentation)}`, async() => {
		await fc.assert(fc.asyncProperty(
			fc.array(expressions(3), { maxLength: 12 }),
			fc.integer({ min: 1, max: 64 }),
			async(expected, size) => {
				async function* input(): AsyncIterable<ESExpr> {
					yield* expected;
				}
				const output = (await Array.fromAsync(text.writeExprs(input(), indentation === undefined ? {} : { indentation }))).join("");
				assert.deepStrictEqual(await decode(output, size), expected);
			},
		), { numRuns: 200 });
	});
}

test("text Rust canonical output and indentation", async() => {
	for(const [input, expected] of [
		["(example 42 z: #true a: (nested))", "(example 42 a: (nested) z: #true)"],
		["('' '': \"\")", "('' '': \"\")"],
		["#u8[0 255 16]", "#\"00ff10\""],
		["#null0", "#null"], ["-0.0f16", "-0.0f16"], ["1.0f", "1.0f"], ["1.0", "1.0d"],
		["#float64:-nan:1", "#float64:-nan:1"],
		["\"\\\"\\\\\\n\\r\\t\\f\\u{0}λ\"", "\"\\\"\\\\\\n\\r\\t\\f\\u{0}λ\""],
	]) assert.strictEqual(await format((await decode(input!))[0]!), expected);
	const expr = (await decode("(outer (inner 1) z: #true a: (empty))"))[0]!;
	assert.strictEqual(await format(expr, { indentation: "  " }), "(outer\n  (inner\n    1\n  )\n  a: (empty)\n  z: #true\n)");
	assert.strictEqual(await format(expr, { indentation: "" }), "(outer\n(inner\n1\n)\na: (empty)\nz: #true\n)");
	assert.strictEqual(await format(expr, { indentation: "\t" }), "(outer\n\t(inner\n\t\t1\n\t)\n\ta: (empty)\n\tz: #true\n)");
	assert.strictEqual(await format((await decode("(empty)"))[0]!, { indentation: "  " }), "(empty)");
});

test("text integer bases, float suffixes, signs and exponents", async() => {
	assert.deepStrictEqual(await decode("0XFF -0b11 +0o377 -00012"), [255n, -3n, 255n, -12n]);
	for(const [decimal, hex, value] of [["1.5", "0x1.8p0", 1.5], ["-1.5e+1", "-0x1.ep3", -15], ["+1.25E-1", "+0x1.0P-3", 0.125]] as const) {
		for(const suffix of ["", "d", "D", "f", "F", "f16", "F16"]) {
			const expected = suffix.toLowerCase() === "f16" ? { type: "float16", value } : suffix.toLowerCase() === "f" ? { type: "float32", value } : value;
			assert.deepStrictEqual(await decode(decimal + suffix), [expected]);
			assert.deepStrictEqual(await decode(hex + suffix), [expected]);
		}
	}
	assert.deepStrictEqual(await decode("0x0.1p-1070 0x0.0p999999999999999999999 0x1.0p999999999999999999999 -0x1.0p-999999999999999999999"), [Number.MIN_VALUE, 0, Infinity, -0]);
	assert.deepStrictEqual(await decode("0x1.fffffffffffffp1023"), [Number.MAX_VALUE]);
});

test("text NaN signs and payloads", async() => {
	for(const width of [16, 32, 64] as const) {
		const fraction = width === 16 ? 10n : width === 32 ? 23n : 52n;
		const sign = 1n << BigInt(width - 1);
		const mask = (1n << fraction) - 1n;
		const exponent = (sign - 1n) ^ mask;
		for(const negative of [false, true]) {
			for(const payload of [1n, mask, (mask + 1n) >> 1n]) {
				const bits = exponent | payload | (negative ? sign : 0n);
				const expected: ESExpr = width === 64 ? { type: "float64-nan", bits } : { type: width === 16 ? "float16-nan" : "float32-nan", bits: Number(bits) };
				assert.deepStrictEqual(await decode(`#float${width}:${negative ? "-" : "+"}nan:${payload}`), [expected]);
				assert.deepStrictEqual(await decode(await format(expected)), [expected]);
			}
		}
		assert.strictEqual(await format((await decode(`#float${width}:nan`))[0]!), `#float${width}:+nan:${(mask + 1n) >> 1n}`);
		assert.strictEqual(await format((await decode(`#float${width}:-nan:0`))[0]!), `#float${width}:-inf`);
		await assert.rejects(decode(`#float${width}:nan:${mask + 1n}`), text.ESExprFormatError);
	}
});

test("text comments, duplicate keywords and Unicode sorting", async() => {
	assert.deepStrictEqual(await decode("// first\n(box key: 1 // middle\n #true key: 2) // last\n"), [
		{ type: "constructor", name: "box", args: [true], kwargs: new Map([["key", 2n]]) },
	]);
	assert.strictEqual(await format({ type: "constructor", name: "box", args: [], kwargs: new Map([["𐀀", 1n], ["\uE000", 2n]]) }), "(box '\uE000': 2 '𐀀': 1)");
	assert.deepStrictEqual(await decode("\"😊\\u{1f60a}\\'\""), ["😊😊'"]);
});

test("text reader yields before the rest of the stream is available", async() => {
	let continued = false;
	async function* input(): AsyncIterable<string> {
		yield "(first)";
		continued = true;
		yield "(second)";
	}
	const reader = new text.ExprReader(input());
	assert.deepStrictEqual(await reader.readExpr(), { type: "constructor", name: "first", args: [], kwargs: new Map() });
	assert.strictEqual(continued, false);
	assert.deepStrictEqual(await Array.fromAsync(reader.readAll()), [{ type: "constructor", name: "second", args: [], kwargs: new Map() }]);
	assert.strictEqual(await reader.tryReadExpr(), undefined);
	await assert.rejects(reader.readExpr(), text.ESExprFormatError);
	assert.deepStrictEqual(await decode(" \t\r\n"), []);
});

for(const input of [
	"(list", "\"unterminated", "('unterminated", "\"\\u{110000}\"", "\"\\u{d800}\"", "\"\\u{}\"", "\"\\u{xyz}\"", "\"\\q\"",
	"1.0 trailing", "0x1.0", "1.", ".5", "1e3", "#truex", "#null1x", "#float32:inf", "#float32:nan:4294967296",
	"(Upper)", "(trailing-)", "(list key)", "(list key:)", ")", "]", "#u8[-1]", "#u8[256]", "#u16[65536]",
	"#u32[4294967296]", "#u64[18446744073709551616]", "#u128[340282366920938463463374607431768211456]",
	"#u8[1.0]", "#u16[1", "#\"0\"", "#\"gg\"", "#\"\\u{30}0\"", "// no newline", "\"\uD800\"",
]) {
	test(`text rejects malformed input ${JSON.stringify(input)}`, async() => {
		await assert.rejects(decode(input), text.ESExprFormatError);
	});
}

test("text writer validates invalid representations", async() => {
	for(const expr of [
		"\uD800", { type: "null", level: -1n }, { type: "array128", value: new Uint8Array(1) },
		{ type: "float16-nan", bits: 0 }, { type: "float32-nan", bits: -1 }, { type: "float64-nan", bits: 1n << 64n },
	] satisfies ESExpr[]) await assert.rejects(format(expr), text.ESExprFormatError);
	await assert.rejects(format(true, { indentation: "x" }), text.ESExprFormatError);
});

test("text float32 parsing avoids double rounding and float16 parses through float32", async() => {
	assert.deepStrictEqual(await decode("1.00000005960464477539062500000000001f"), [{ type: "float32", value: 1 + 2 ** -23 }]);
	assert.deepStrictEqual(await decode("1.0004882812500000000001f16 0x1.0020000000000001p0f16"), [{ type: "float16", value: 1 }, { type: "float16", value: 1 }]);
	assert.deepStrictEqual(await decode("0x1.000001p0f 0x1.000003p0f 0x1.0p-150f 0x1.8p-149f"), [
		{ type: "float32", value: 1 }, { type: "float32", value: 1 + 2 ** -22 },
		{ type: "float32", value: 0 }, { type: "float32", value: 2 ** -148 },
	]);
	assert.deepStrictEqual(await decode("0.0e999999999999999999999f -1.0e-999999999999999999999f 1.0e999999999999999999999f"), [
		{ type: "float32", value: 0 }, { type: "float32", value: -0 }, { type: "float32", value: Infinity },
	]);
});

test("text preserves all float16 bit patterns", async() => {
	const expected: ESExpr[] = [];
	const view = new DataView(new ArrayBuffer(2));
	for(let bits = 0; bits <= 0xFFFF; ++bits) {
		view.setUint16(0, bits);
		const value = view.getFloat16(0);
		expected.push(Number.isNaN(value) ? { type: "float16-nan", bits } : { type: "float16", value });
	}
	const source = (await Array.fromAsync(text.writeExprs(expected))).join("");
	assert.deepStrictEqual(await decode(source, 256), expected);
});

test("text float32 and float64 boundaries round trip", async() => {
	for(const width of [32, 64] as const) {
		const sign = 1n << BigInt(width - 1);
		const payload = (1n << (width === 32 ? 23n : 52n)) - 1n;
		const exponent = (sign - 1n) ^ payload;
		const view = new DataView(new ArrayBuffer(width / 8));
		for(const bits of [0n, 1n, sign, payload, payload + 1n, exponent - 1n, exponent, exponent | sign, exponent | 1n, (sign << 1n) - 1n]) {
			if(width === 32) view.setUint32(0, Number(bits));
			else view.setBigUint64(0, bits);
			const value = width === 32 ? view.getFloat32(0) : view.getFloat64(0);
			const expected: ESExpr = Number.isNaN(value)
				? width === 32 ? { type: "float32-nan", bits: Number(bits) } : { type: "float64-nan", bits }
				: width === 32 ? { type: "float32", value } : value;
			assert.deepStrictEqual(await decode(await format(expected)), [expected]);
		}
	}
});

test("text permits adjacent self-delimiting expressions", async() => {
	assert.deepStrictEqual(await decode("1#true#false\"x\"(list#null)"), [1n, true, false, "x", { type: "constructor", name: "list", args: [null], kwargs: new Map() }]);
});
