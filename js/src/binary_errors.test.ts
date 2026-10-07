import { test } from "node:test";
import assert from "node:assert/strict";
import { ExprReader, ESExprFormatError, ArrayStringPool, readExprStream } from "./binary_format.js";

async function* chunks(bytes: readonly number[]): AsyncIterable<Uint8Array> {
    yield new Uint8Array();
    for (const byte of bytes) {
        yield new Uint8Array([byte]);
        yield new Uint8Array();
    }
}

for (const [name, bytes, message] of [
    ["constructor end outside constructor", [0xE0], "Unexpected constructor end"],
    ["keyword outside constructor", [0xC0], "Unexpected keyword: index=0"],
    ["missing string pool value", [0x80], "Invalid string pool index"],
    ["missing constructor name", [0x00], "Invalid string pool index"],
    ["unterminated constructor", [0xE7, 0xE1], "Unexpected end of file"],
    ["truncated integer", [0x30], "Unexpected end of file"],
    ["truncated string", [0x62, 0x61], "Unexpected end of file"],
    ["truncated array", [0xA2, 0x01], "Unexpected end of file"],
    ["truncated float16", [0xEC, 0x00], "Unexpected end of file"],
    ["truncated float32", [0xE4, 0x00], "Unexpected end of file"],
    ["truncated float64", [0xE5, 0x00], "Unexpected end of file"],
    ["invalid string pool update", [0xEB, 0xE1], "Invalid string pool"],
] as const) {
    test(`binary reader rejects ${name}`, async () => {
        await assert.rejects(new ExprReader(chunks(bytes)).readExpr(), (error: unknown) => {
            assert.ok(error instanceof ESExprFormatError);
            assert.strictEqual(error.message, message);
            return true;
        });
    });
}

test("empty streams end cleanly while required reads reject EOF", async () => {
    assert.strictEqual(await new ExprReader(chunks([])).tryReadExpr(), undefined);
    await assert.rejects(new ExprReader(chunks([])).readExpr(), ESExprFormatError);
    assert.deepStrictEqual(await Array.fromAsync(readExprStream(chunks([]))), []);
    assert.deepStrictEqual(await Array.fromAsync(readExprStream(chunks([0xE1, 0xE2, 0xE3]))), [true, false, null]);
});

test("string pools copy input, append entries, and reject invalid indexes", () => {
    const initial = ["a"];
    const pool = new ArrayStringPool(initial);
    initial[0] = "changed";
    assert.strictEqual(pool.get(0), "a");
    assert.strictEqual(pool.lookupIndex("missing"), undefined);
    assert.strictEqual(pool.lookupIndex("a"), 0);
    assert.strictEqual(pool.append("b"), 1);
    assert.strictEqual(pool.append(["c", "d"]), 2);
    assert.strictEqual(pool.length, 4);
    assert.deepStrictEqual(pool.toEncoded(), { values: ["a", "b", "c", "d"] });
    assert.deepStrictEqual(ArrayStringPool.fromEncoded(pool.toEncoded()).toEncoded(), pool.toEncoded());
    for (const index of [-1, 4]) assert.throws(() => pool.get(index), ESExprFormatError);
});

test("keyword values must be complete expressions", async () => {
    const pool = new ArrayStringPool(["key"]);
    for (const bytes of [[0xE7, 0xC0], [0xE7, 0xC0, 0xE0]]) {
        await assert.rejects(new ExprReader(chunks(bytes), pool).readExpr(), ESExprFormatError);
    }
});
