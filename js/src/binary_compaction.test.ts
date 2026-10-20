import { test } from "node:test";
import assert from "node:assert/strict";
import fc from "fast-check";
import type { ESExpr } from "./index.js";
import { addStrings, ArrayStringPool, readExprStream, StringTableBuilder, writeExpr, writeExprs } from "./binary_format.js";
import { expressions } from "./expression_arbitraries.test.js";

function constructor(name: string, args: readonly ESExpr[] = [], kwargs: ReadonlyMap<string, ESExpr> = new Map()): ESExpr {
	return { type: "constructor", name, args, kwargs };
}

async function bytes(data: AsyncIterable<Uint8Array>): Promise<Buffer> {
	return Buffer.concat(await Array.fromAsync(data));
}

async function* compact(exprs: readonly ESExpr[]): AsyncIterable<Uint8Array> {
	const builder = new StringTableBuilder();
	for(const expr of exprs) builder.record(expr);
	const pool = new ArrayStringPool();
	yield* addStrings(builder.build(), pool);
	yield* writeExprs(exprs, pool);
}

test("compaction records nested strings, mixed roles, and mandatory single uses", () => {
	const builder = new StringTableBuilder();
	assert.deepStrictEqual(builder.build(), []);
	builder.record(constructor("outer", [constructor("inner", ["shared", "once"])], new Map([["shared", "shared"]])));
	assert.deepStrictEqual(builder.build(), ["shared", "inner", "outer"]);
	const table = builder.build();
	table.length = 0;
	assert.deepStrictEqual(builder.build(), ["shared", "inner", "outer"]);
});

test("dedicated names count only as literals and keywords", () => {
	const builder = new StringTableBuilder();
	for(const name of ["list", "map", "set", "string-table"]) builder.record(constructor(name));
	assert.deepStrictEqual(builder.build(), []);
	builder.record(constructor("list", ["map", "map"], new Map([["set", true]])));
	assert.deepStrictEqual(builder.build(), ["map", "set"]);
});

test("literal selection uses UTF-8 savings and excludes single uses and break-even entries", () => {
	const builder = new StringTableBuilder();
	for(const s of ["", "x", "ab", "é", "a long single-use string", "", "x", "ab", "é", "x"]) builder.record(s);
	assert.deepStrictEqual(builder.build(), ["x", "ab", "é"]);
	const breakEven = new StringTableBuilder();
	breakEven.record("x");
	breakEven.record("x");
	assert.deepStrictEqual(breakEven.build(), []);
});

test("ties use Unicode scalar ordering independently of recording order", () => {
	const first = new StringTableBuilder();
	const second = new StringTableBuilder();
	const names = ["a", "b", "c", "\uE000", "\u{10000}"];
	for(const name of names) first.record(constructor(name));
	for(const name of names.toReversed()) second.record(constructor(name));
	assert.deepStrictEqual(first.build(), names);
	assert.deepStrictEqual(second.build(), names);
});

test("selection accounts for index widths and removed candidates", () => {
	for(const prefix of [15, 16, 2047, 2048]) {
		const builder = new StringTableBuilder();
		// This rejected entry sorts first and must not consume an index.
		for(let i = 0; i < 8; ++i) builder.record("");
		for(let i = 0; i < prefix; ++i) {
			for(let j = 0; j < 5; ++j) builder.record(constructor(`a${i}`));
		}
		const value = prefix < 100 ? "zz" : "zzz";
		for(let i = 0; i < 3; ++i) builder.record(value);
		assert.strictEqual(builder.build().includes(value), prefix === 15 || prefix === 2047);
	}
});

test("compacted streams use references and decode with a fresh reader", async() => {
	const exprs = [constructor("custom", ["repeated long value", "repeated long value"], new Map([["key", "once"]]))];
	const output = await bytes(compact(exprs));
	assert.ok(output.includes(Buffer.from([0x80, 0x80])));
	assert.ok(output.length < (await bytes(writeExprs(exprs))).length);
	assert.deepStrictEqual(await Array.fromAsync(readExprStream(compact(exprs))), exprs);
});

test("additions emit inline payloads, skip duplicates, and preserve indices", async() => {
	const pool = new ArrayStringPool();
	async function* encode(): AsyncIterable<Uint8Array> {
		yield* addStrings([], pool);
		yield* addStrings(["first", "first"], pool);
		yield* writeExpr("first", pool);
		yield* addStrings(["first", "second", "third", "second"], pool);
		yield* addStrings(["third"], pool);
		for(const value of ["first", "second", "third"]) yield* writeExpr(value, pool);
	}
	assert.deepStrictEqual(await bytes(encode()), Buffer.from([
		0xEB, 0x65, 102, 105, 114, 115, 116, 0x80,
		0xEB, 0xE6, 0x66, 115, 101, 99, 111, 110, 100, 0x65, 116, 104, 105, 114, 100, 0xE0,
		0x80, 0x81, 0x82,
	]));
	assert.strictEqual(pool.length, 3);
});

test("additions preserve automatic and preseeded entries", async() => {
	const pool = new ArrayStringPool(["known"]);
	async function* encode(): AsyncIterable<Uint8Array> {
		yield* writeExprs([constructor("first")], pool);
		yield* addStrings(["known", "first", "second", "second"], pool);
		yield* writeExprs(["known", "first", "second"], pool);
	}
	const readerPool = new ArrayStringPool(["known"]);
	assert.deepStrictEqual(await Array.fromAsync(readExprStream(encode(), readerPool)), [constructor("first"), "known", "first", "second"]);
	assert.strictEqual(pool.length, 3);
});

test("literal references use actual index widths and inline fallback", async() => {
	for(const [index, value, expected] of [
		[15, "zz", [0x8F]], [16, "zz", [0x90, 1]], [2047, "zz", [0x9F, 0x7F]],
		[2048, "zz", [0x90, 0x80, 1]], [2048, "x", [0x61, 120]],
	] as const) {
		const pool = new ArrayStringPool([...Array.from({ length: index }, (_, i) => `entry${i}`), value]);
		assert.deepStrictEqual(await bytes(writeExpr(value, pool)), Buffer.from(expected));
	}
});

test("string-length boundaries round trip", async() => {
	for(const length of [15, 16, 2047, 2048]) {
		const exprs = ["z".repeat(length), "z".repeat(length)];
		assert.deepStrictEqual(await Array.fromAsync(readExprStream(compact(exprs))), exprs);
	}
});

test("empty additions leave custom pools untouched", async() => {
	const pool = new ArrayStringPool();
	assert.deepStrictEqual(await bytes(addStrings([], pool)), Buffer.alloc(0));
	assert.strictEqual(pool.length, 0);
	assert.deepStrictEqual(await bytes(writeExprs([], pool)), Buffer.alloc(0));
});

test("addition errors propagate and incomplete emissions do not update the pool", async() => {
	const pool = new ArrayStringPool();
	const iterator = addStrings(["value"], pool)[Symbol.asyncIterator]();
	await iterator.next();
	await iterator.return?.();
	assert.strictEqual(pool.length, 0);
	const failing = {
		get: (i: number) => pool.get(i),
		lookupIndex: (s: string) => pool.lookupIndex(s),
		append: (_s: string | readonly string[]): number => { throw new Error("injected append failure"); },
	};
	await assert.rejects(bytes(addStrings(["value"], failing)), /injected append failure/);
});

test("compacted recursive streams round trip through single-byte chunks", async() => {
	await fc.assert(fc.asyncProperty(fc.array(expressions(3), { maxLength: 16 }), async expected => {
		async function* chunked(): AsyncIterable<Uint8Array> {
			for await (const chunk of compact(expected)) {
				yield new Uint8Array();
				for(const byte of chunk) yield new Uint8Array([byte]);
			}
		}
		assert.deepStrictEqual(await Array.fromAsync(readExprStream(chunked())), expected);
	}), { numRuns: 200 });
});
