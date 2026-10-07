import { test } from "node:test";
import assert from "node:assert/strict";
import { ArrayStringPool, StringPool, ExprReader, ESExprFormatError, writeExpr, writeExprs } from "./binary_format.js";
import { ESExpr } from "./index.js";

async function* chunks(bytes: readonly number[]): AsyncIterable<Uint8Array> {
	for (const byte of bytes) yield new Uint8Array([byte]);
}
async function decode(data: AsyncIterable<Uint8Array>): Promise<ESExpr[]> {
	return Array.fromAsync(new ExprReader(data).readAll());
}

for (const name of ["map", "set"]) {
	test(`binary ${name} constructors round trip with their compact tags`, async () => {
		const expr: ESExpr = { type: "constructor", name, args: ["a", true], kwargs: new Map() };
		const encoded = await Array.fromAsync(writeExpr(expr, new ArrayStringPool()));
		assert.strictEqual(encoded[0]![0], name === "map" ? 0xF1 : 0xF2);
		assert.deepStrictEqual(await decode(writeExprs([expr])), [expr]);
	});
}

test("string-table updates inside constructors do not become arguments", async () => {
	// list, append string 'x' to pool, pool reference 0, end list
	assert.deepStrictEqual(await decode(chunks([0xE7, 0xEB, 0x61, 0x78, 0x80, 0xE0])), [
		{ type: "constructor", name: "list", args: ["x"], kwargs: new Map() },
	]);
});

test("binary reader rejects unknown tokens and lengths beyond safe integer range", async () => {
	await assert.rejects(decode(chunks([0xFF])), (error: unknown) => {
		assert.ok(error instanceof ESExprFormatError);
		assert.strictEqual(error.message, "Invalid token byte");
		return true;
	});
	// array16 length 2^53, encoded as a full unsigned varint
	await assert.rejects(decode(chunks([0xED, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x10])), (error: unknown) => {
		assert.ok(error instanceof ESExprFormatError);
		assert.strictEqual(error.message, "Integer is too large");
		return true;
	});
});

test("stream writer shares strings across expressions and supports async input", async () => {
	const expr: ESExpr = { type: "constructor", name: "box", args: ["value"], kwargs: new Map([["label", "value"]]) };
	async function* input(): AsyncIterable<ESExpr> {
		yield expr;
		yield expr;
	}
	assert.deepStrictEqual(await decode(writeExprs(input())), [expr, expr]);
	const pool = StringPool.fromArray(["a", "b"]);
	assert.strictEqual(pool.get(1), "b");
	assert.strictEqual(pool.lookupIndex("b"), 1);
});
