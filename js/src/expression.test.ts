import { test } from "node:test";
import assert from "node:assert/strict";
import * as e from "./index.js";

const expressions: readonly (readonly [e.ESExpr, e.ESExprTag, string])[] = [
    [true, Boolean, "bool"], [1n, BigInt, "int"], ["a", String, "string"], [1, Number, "float64"],
    [null, null, "null"], [{ type: "null", level: 2n }, null, "null"],
    [new Uint8Array([1]), Uint8Array, "array8"], [new Uint16Array([1]), Uint16Array, "array16"],
    [new Uint32Array([1]), Uint32Array, "array32"], [new BigUint64Array([1n]), BigUint64Array, "array64"],
    [{ type: "array128", value: new Uint8Array(16) }, e.Array128Symbol, "array128"],
    [{ type: "float16", value: 1 }, e.Float16Symbol, "float16"],
    [{ type: "float16-nan", bits: 0x7E01 }, e.Float16Symbol, "float16"],
    [{ type: "float32", value: 1 }, e.Float32Symbol, "float32"],
    [{ type: "float32-nan", bits: 0x7FC00001 }, e.Float32Symbol, "float32"],
    [{ type: "float64-nan", bits: 0x7FF8000000000001n }, Number, "float64"],
    [{ type: "constructor", name: "box", args: [true], kwargs: new Map([["a", 1n]]) }, "box", "constructor box"],
];
for (const [i, [expr, tag, display]] of expressions.entries()) {
    test(`expression codec preserves representation and tag (${i}: ${display})`, () => {
        assert.strictEqual(e.ESExpr.tagOf(expr), tag);
        assert.strictEqual(e.ESExprTag.show(tag), display);
        assert.strictEqual(e.ESExpr.codec.encode(expr), expr);
        assert.deepStrictEqual(e.ESExpr.codec.decode(expr), { success: true, value: expr });
        assert.ok(e.ESExpr.codec.isEncodedEqual(expr, structuredClone(expr)));
        for (const [other] of expressions) {
            if (e.ESExpr.tagOf(other) !== tag) {
                assert.strictEqual(e.ESExpr.codec.isEncodedEqual(expr, other), false);
                assert.strictEqual(e.ESExpr.codec.isEncodedEqual(other, expr), false);
            }
        }
    });
}

test("expression equality compares constructor names, arguments, keywords, and payloads", () => {
    const base: e.ESExpr.Constructor = { type: "constructor", name: "a", args: [true], kwargs: new Map([["x", 1n]]) };
    for (const other of [
        { ...base, name: "b" }, { ...base, args: [false] }, { ...base, kwargs: new Map([["x", 2n]]) },
        { ...base, kwargs: new Map() }, { ...base, kwargs: new Map([["y", 1n]]) },
    ]) assert.strictEqual(e.ESExpr.codec.isEncodedEqual(base, other), false);
    for (const [a, b] of [
        [{ type: "float16", value: 0 }, { type: "float16", value: -0 }],
        [{ type: "float32", value: 1 }, { type: "float32", value: 2 }],
        [{ type: "float16-nan", bits: 1 }, { type: "float16-nan", bits: 2 }],
        [{ type: "float32-nan", bits: 1 }, { type: "float32-nan", bits: 2 }],
        [{ type: "float64-nan", bits: 1n }, { type: "float64-nan", bits: 2n }],
        [{ type: "null", level: 1n }, { type: "null", level: 2n }],
        [{ type: "array128", value: new Uint8Array([1]) }, { type: "array128", value: new Uint8Array([2]) }],
    ] as const) assert.strictEqual(e.ESExpr.codec.isEncodedEqual(a, b), false);
});

test("tag sets handle universal, empty, overlapping, and disjoint sets", () => {
    const all = e.ESExprTagSet.All;
    const empty = new Set<e.ESExprTag>();
    const a = new Set<e.ESExprTag>([Boolean]);
    const b = new Set<e.ESExprTag>([String]);
    assert.strictEqual(e.ESExpr.codec.tags, all);
    assert.ok(e.ESExprTagSet.isEmpty(empty));
    assert.strictEqual(e.ESExprTagSet.isEmpty(all), false);
    assert.strictEqual(e.ESExprTagSet.isEmpty(a), false);
    assert.ok(e.ESExprTagSet.has(all, String));
    assert.ok(e.ESExprTagSet.has(a, Boolean));
    assert.strictEqual(e.ESExprTagSet.has(a, String), false);
    assert.ok(e.ESExprTagSet.disjoint(a, b));
    assert.strictEqual(e.ESExprTagSet.disjoint(a, a), false);
    for (const set of [empty, a, all]) {
        assert.strictEqual(e.ESExprTagSet.disjoint(all, set), set === empty);
        assert.strictEqual(e.ESExprTagSet.disjoint(set, all), set === empty);
        assert.strictEqual(e.ESExprTagSet.union(all, set), all);
        assert.strictEqual(e.ESExprTagSet.union(set, all), all);
    }
    assert.deepStrictEqual(e.ESExprTagSet.union(a, b), new Set([Boolean, String]));
    assert.strictEqual(e.ESExprTagSet.show(all), "all-tags");
    assert.strictEqual(e.ESExprTagSet.show(a), "[ bool ]");
});
