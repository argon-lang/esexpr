import { test } from "node:test";
import assert from "node:assert/strict";
import { ESExpr } from "./index.js";
import * as esxb from "./binary_format.js";

import * as fs from "node:fs/promises";
import * as path from "node:path";

type KWArgs = {
	[K: string]: ESExprJson;
};

type ESExprJson =
    | string
    | bigint
    | boolean
    | null
    | { constructor_name: string, args?: readonly ESExprJson[], kwargs?: KWArgs }
    | readonly ESExprJson[]
    | { float16: number | "+inf" | "-inf" | "nan" }
    | { float32: number | "+inf" | "-inf" | "nan" }
    | { float64: number | "+inf" | "-inf" | "nan" }
    | { base64: string }
    | { array8: readonly number[] }
    | { array16: readonly number[] }
    | { array32: readonly number[] }
    | { array64: readonly bigint[] }
    | { array128: readonly bigint[] }
    | { null: bigint }
;

function json2esexpr(json: ESExprJson): ESExpr {
	if(typeof json === "bigint" || typeof json === "string" || typeof json === "boolean" || json === null) {
		return json;
	}
	else if(json instanceof Array) {
		return json2esexpr({
			constructor_name: "list",
			args: json,
		});
	}
	else if("constructor_name" in json) {
		const kwargs = new Map<string, ESExpr>();
		if(json.kwargs !== undefined) {
			for(const [k, v] of Object.entries(json.kwargs)) {
				kwargs.set(k, json2esexpr(v));
			}
		}

		return {
			type: "constructor",
			name: json.constructor_name,
			args: (json.args ?? []).map(json2esexpr),
			kwargs,
		};
	}
	else if("float16" in json) {
		if(json.float16 === "+inf") {
			return { type: "float16", value: Number.POSITIVE_INFINITY };
		}
		else if(json.float16 === "-inf") {
			return { type: "float16", value: Number.NEGATIVE_INFINITY };
		}
		else if(json.float16 === "nan") {
			return { type: "float16-nan", bits: 0x7E00 };
		}
		else {
			return { type: "float16", value: Math.f16round(json.float16) };
		}
	}
	else if("float32" in json) {
		if(json.float32 === "+inf") {
			return { type: "float32", value: Number.POSITIVE_INFINITY };
		}
		else if(json.float32 === "-inf") {
			return { type: "float32", value: Number.NEGATIVE_INFINITY };
		}
		else if(json.float32 === "nan") {
			return { type: "float32-nan", bits: 0x7FC00000 };
		}
		else {
			return { type: "float32", value: Math.fround(json.float32) };
		}
	}
	else if("float64" in json) {
		if(json.float64 === "+inf") {
			return Number.POSITIVE_INFINITY;
		}
		else if(json.float64 === "-inf") {
			return Number.NEGATIVE_INFINITY;
		}
		else if(json.float64 === "nan") {
			return { type: "float64-nan", bits: 0x7FF8000000000000n };
		}
		else {
			return json.float64;
		}
	}
	else if("base64" in json) {
		return new Uint8Array(Buffer.from(json.base64, "base64"));
	}
	else if("array8" in json) {
		return new Uint8Array(json.array8);
	}
	else if("array16" in json) {
		return new Uint16Array(json.array16);
	}
	else if("array32" in json) {
		return new Uint32Array(json.array32);
	}
	else if("array64" in json) {
		return new BigUint64Array(json.array64);
	}
	else if("array128" in json) {
		return {
			type: "array128",
			value: new Uint8Array(json.array128.flatMap(n => {
				let n2 = n;
				const bytes: number[] = [];
				for(let i = 0; i < 16; ++i) {
					bytes.push(Number(BigInt.asUintN(8, n2)));
					n2 >>= 8n;
				}
				return bytes;
			})),
		};
	}
	else if("null" in json) {
		return { type: "null", level: json.null };
	}
	else {
		throw new Error("Invalid ESExpr JSON: " + (json satisfies never));
	}
}


class DeserializeNum {
	constructor(
		readonly value: number,
		readonly rawValue: string,
	) {}
}

type JSONWithNum =
	| Map<string, JSONWithNum>
	| JSONWithNum[]
	| string
	| DeserializeNum
	| boolean
	| null
;

function parseJsonWithNum(text: string): JSONWithNum {
	return JSON.parse(text, (_key: string, value: unknown, context: { source?: string } = {}): unknown => {
		if(typeof value === "number") {
			if(context.source === undefined) {
				throw new Error("JSON parsing requires reviver source context");
			}
			return new DeserializeNum(value, context.source);
		}
		if(value !== null && typeof value === "object" && !Array.isArray(value)) {
			return new Map(Object.entries(value));
		}
		return value;
	}) as JSONWithNum;
}

function toESExprJson(value: JSONWithNum): ESExprJson {
	if(value instanceof DeserializeNum) {
		return BigInt(value.rawValue);
	}
	if(Array.isArray(value)) {
		return value.map(toESExprJson);
	}
	if(!(value instanceof Map)) {
		return value;
	}

	function string(value: JSONWithNum | undefined): string {
		if(typeof value !== "string") {
			throw new Error("Expected a JSON string");
		}
		return value;
	}

	function array(value: JSONWithNum | undefined): JSONWithNum[] {
		if(!Array.isArray(value)) {
			throw new Error("Expected a JSON array");
		}
		return value;
	}

	function number(value: JSONWithNum): number {
		if(!(value instanceof DeserializeNum)) {
			throw new Error("Expected a JSON number");
		}
		return value.value;
	}

	function wideInteger(value: JSONWithNum | undefined): bigint {
		if(!(value instanceof DeserializeNum)) {
			throw new Error("Expected a JSON integer");
		}
		return BigInt(value.rawValue);
	}

	function float(value: JSONWithNum | undefined): number | "+inf" | "-inf" | "nan" {
		if(value instanceof DeserializeNum) {
			return value.value;
		}
		if(value === "+inf" || value === "-inf" || value === "nan") {
			return value;
		}
		throw new Error("Expected a JSON float");
	}

	if(value.has("constructor_name")) {
		const args = value.get("args");
		const kwargs = value.get("kwargs");
		if(kwargs !== undefined && !(kwargs instanceof Map)) {
			throw new Error("Expected a keyword argument object");
		}
		return {
			constructor_name: string(value.get("constructor_name")),
			...(args === undefined ? {} : { args: array(args).map(toESExprJson) }),
			...(kwargs === undefined ? {} : { kwargs: Object.fromEntries([...kwargs].map(([key, item]) => [key, toESExprJson(item)])) }),
		};
	}
	if(value.has("float16")) {
		return { float16: float(value.get("float16")) };
	}
	if(value.has("float32")) {
		return { float32: float(value.get("float32")) };
	}
	if(value.has("float64")) {
		return { float64: float(value.get("float64")) };
	}
	if(value.has("base64")) {
		return { base64: string(value.get("base64")) };
	}
	if(value.has("array8")) {
		return { array8: array(value.get("array8")).map(number) };
	}
	if(value.has("array16")) {
		return { array16: array(value.get("array16")).map(number) };
	}
	if(value.has("array32")) {
		return { array32: array(value.get("array32")).map(number) };
	}
	if(value.has("array64")) {
		return { array64: array(value.get("array64")).map(wideInteger) };
	}
	if(value.has("array128")) {
		return { array128: array(value.get("array128")).map(wideInteger) };
	}
	if(value.has("null")) {
		return { null: wideInteger(value.get("null")) };
	}
	throw new Error("Invalid ESExpr JSON object");
}

function parseESExprJson(text: string): ESExprJson {
	return toESExprJson(parseJsonWithNum(text));
}

function json2esexprMany(json: ESExprJson): ESExpr[] {
	if(json instanceof Array) {
		return json.map(json2esexpr);
	}
	else {
		return [json2esexpr(json)];
	}
}

async function* arrayToAsyncIterable<A>(arr: readonly A[]): AsyncIterable<A> {
	for(const a of arr) {
		yield a;
	}
}

async function arrayFromAsync<T>(iter: AsyncIterable<T>): Promise<T[]> {
	const res: T[] = [];
	for await (const x of iter) {
		res.push(x);
	}
	return res;
}


async function* encodeBin(expr: ESExpr): AsyncIterable<Uint8Array> {
	yield* esxb.writeExprs([expr]);
}

async function decodeBinMany(data: AsyncIterable<Uint8Array>): Promise<ESExpr[]> {
	const reader = new esxb.ExprReader(data);
	return await arrayFromAsync(reader.readAll());
}

async function decodeBin1(data: AsyncIterable<Uint8Array>): Promise<ESExpr> {
	const esxbArray = await decodeBinMany(data);

	if(esxbArray.length !== 1) {
		throw new Error("Expected a single expr");
	}

	return esxbArray[0]!;
}

async function* singleByteChunks(data: AsyncIterable<Uint8Array>): AsyncIterable<Uint8Array> {
	for await (const arr of data) {
		for(const b of arr) {
			yield new Uint8Array([b]);
		}
	}
}

async function run_test_case(esxbFile: string): Promise<void> {
	const esxbData: Uint8Array = await fs.readFile(esxbFile);
	const exprs = await decodeBinMany(arrayToAsyncIterable([esxbData]));

	const json = parseESExprJson(
		await fs.readFile(esxbFile.substring(0, esxbFile.length - 4) + "json", { encoding: "utf-8" }),
	);

	const reencoded: ESExpr[] = [];
	for(const expr of exprs) {
		reencoded.push(await decodeBin1(encodeBin(expr)));
	}

	assert.deepStrictEqual(json2esexprMany(json), exprs);
	assert.deepStrictEqual(reencoded, exprs);
}

const dir = path.join(import.meta.dirname, "../../tests/");
for(const file of await fs.readdir(dir, { withFileTypes: true })) {
	if(file.isDirectory()) {
		continue;
	}

	if(path.extname(file.name) !== ".esxb") {
		continue;
	}

	const fileName = path.join(dir, file.name);

	test("Binary Format " + file.name, async() => {
		await run_test_case(fileName);
	});

}

test("Multi byte strings", async() => {
	const strings = [
		"ñ", // Latin-1 Supplement (U+00F1)
		"Δ", // Greek (U+0394)
		"Я", // Cyrillic (U+042F)
		"ש", // Hebrew (U+05E9)
		"ك", // Arabic (U+0643)
		"漢", // CJK (Chinese/Japanese/Korean, U+6F22)
		"❤", // Emoji (U+2764)
		"😊", // Emoji (U+1F60A)
		"𐎀", // Historic scripts (U+10380, Ugaritic letter)
		"𝕏", // Mathematical symbols (U+1D54F)
	];

	for(const s of strings) {
		const converted = await decodeBin1(singleByteChunks(encodeBin(s)));
		assert.deepStrictEqual(converted, s);
	}
});


test("Raw JSON integers preserve precision", () => {
	for(const value of [0n, 42n, -42n, (1n << 53n) + 1n, 1n << 64n, 1n << 256n, -(1n << 256n)]) {
		assert.deepStrictEqual(json2esexpr(parseESExprJson(String(value))), value);
		assert.deepStrictEqual(json2esexprMany(parseESExprJson(`[${value}]`)), [value]);
		assert.deepStrictEqual(json2esexpr(parseESExprJson(JSON.stringify(String(value)))), String(value));
		assert.deepStrictEqual(json2esexpr(parseESExprJson(`{"constructor_name":"nested","args":[${value}],"kwargs":{"value":${value}}}`)), {
			type: "constructor", name: "nested", args: [value], kwargs: new Map([["value", value]]),
		});
	}
	assert.throws(() => json2esexpr(parseESExprJson("1.5")));
	assert.throws(() => json2esexpr(parseESExprJson("1e3")));
});

test("JSON reviver preserves typed floats and integer arrays", () => {
	assert.deepStrictEqual(json2esexpr(parseESExprJson("{\"float64\":-0}")), -0);
	assert.deepStrictEqual(json2esexpr(parseESExprJson("{\"float32\":42}")), { type: "float32", value: 42 });
	assert.deepStrictEqual(json2esexpr(parseESExprJson("{\"float64\":1e3}")), 1000);
	assert.deepStrictEqual(parseESExprJson("{\"array64\":[42]}"), { array64: [42n] });
});

test("JSON reviver converts only expression numbers", () => {
	for(const width of [16, 32, 64]) {
		for(const value of [1.5, -2.75, 42, -0]) {
			const token = Object.is(value, -0) ? "-0" : String(value);
			assert.deepStrictEqual(parseESExprJson(`{"float${width}":${token}}`), { [`float${width}`]: value });
		}
	}
	assert.deepStrictEqual(json2esexpr(parseESExprJson("{\"float64\":1.5}")), 1.5);
	assert.deepStrictEqual(json2esexpr(parseESExprJson("{\"float32\":1.5}")), { type: "float32", value: 1.5 });
	assert.deepStrictEqual(json2esexpr(parseESExprJson("{\"float16\":1.5}")), { type: "float16", value: 1.5 });
	assert.deepStrictEqual(parseESExprJson("{\"constructor_name\":\"nested\",\"kwargs\":{\"float64\":9007199254740993,\"\":42},\"args\":[{\"float64\":1.5},[42]]}"), {
		constructor_name: "nested", kwargs: { "float64": 9007199254740993n, "": 42n }, args: [{ float64: 1.5 }, [42n]],
	});
});

test("JSON number wrappers retain source text and numeric values", () => {
	const parsed = parseJsonWithNum("{\"integer\":9007199254740993,\"fraction\":1.25,\"zero\":-0,\"list\":[42]}");
	assert.deepStrictEqual(parsed, new Map<string, JSONWithNum>([
		["integer", new DeserializeNum(9007199254740992, "9007199254740993")],
		["fraction", new DeserializeNum(1.25, "1.25")],
		["zero", new DeserializeNum(-0, "-0")],
		["list", [new DeserializeNum(42, "42")]],
	]));
});

test("Wide integer arrays retain exact numeric tokens", () => {
	assert.deepStrictEqual(json2esexpr(parseESExprJson("{\"array64\":[18446744073709551615]}")), new BigUint64Array([0xFFFFFFFFFFFFFFFFn]));
	const array128 = json2esexpr(parseESExprJson("{\"array128\":[340282366920938463463374607431768211455]}"));
	assert.deepStrictEqual(array128, { type: "array128", value: new Uint8Array(16).fill(255) });
});

test("JSON null levels preserve arbitrary precision", () => {
	const level = 1n << 256n;
	assert.deepStrictEqual(json2esexpr(parseESExprJson(`{"null":${level}}`)), { type: "null", level });
	assert.throws(() => parseESExprJson("{\"null\":\"1\"}"));
});
