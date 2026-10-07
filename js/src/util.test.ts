import { test } from "node:test";
import assert from "node:assert/strict";
import { valuesEqual } from "./util.js";

test("value equality handles primitive and nested object differences", () => {
    const same = {};
    for (const [a, b, expected] of [
        [same, same, true], [null, null, true], [NaN, NaN, true],
        [0, -0, false], [1, "1", false], [null, {}, false], [{}, null, false],
        [{ a: [1, { b: true }] }, { a: [1, { b: true }] }, true],
        [{ a: 1 }, { a: 2 }, false], [{ a: 1 }, {}, false], [{}, { a: 1 }, false],
    ] as const) {
        assert.strictEqual(valuesEqual(a, b), expected);
    }
});

for (const ArrayType of [Uint8Array, Uint16Array, Uint32Array]) {
    test(`${ArrayType.name} equality checks type, length, and elements`, () => {
        const a = new ArrayType([1, 2]);
        assert.ok(valuesEqual(a, new ArrayType([1, 2])));
        assert.strictEqual(valuesEqual(a, {}), false);
        assert.strictEqual(valuesEqual(a, new ArrayType([1])), false);
        assert.strictEqual(valuesEqual(a, new ArrayType([1, 3])), false);
    });
}

test("BigUint64Array equality checks type, length, and elements", () => {
    const a = new BigUint64Array([1n, 2n]);
    assert.ok(valuesEqual(a, new BigUint64Array([1n, 2n])));
    for (const b of [{}, new BigUint64Array([1n]), new BigUint64Array([1n, 3n])]) {
        assert.strictEqual(valuesEqual(a, b), false);
    }
});

test("map equality compares keys and nested values in either direction", () => {
    const a = new Map([["a", { value: 1 }]]);
    assert.ok(valuesEqual(a, new Map([["a", { value: 1 }]])));
    for (const b of [{}, new Map(), new Map([["b", { value: 1 }]]),
        new Map([["a", { value: 2 }]]), new Map([...a, ["b", { value: 1 }]])]) {
        assert.strictEqual(valuesEqual(a, b), false);
    }
});

test("set equality compares membership in either direction", () => {
    const a = new Set([1, 2]);
    assert.ok(valuesEqual(a, new Set([2, 1])));
    for (const b of [{}, new Set([1]), new Set([1, 3]), new Set([1, 2, 3])]) {
        assert.strictEqual(valuesEqual(a, b), false);
    }
});
