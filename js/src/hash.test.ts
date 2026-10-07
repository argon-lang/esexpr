import { test } from "node:test";
import assert from "node:assert/strict";
import { hash, equals, hashSymbol, equalsSymbol } from "./hash.js";

const equalPairs: readonly (readonly [unknown, unknown])[] = [
    [NaN, NaN], [null, null], [undefined, undefined], [false, false],
    [1.5, 1.5], [Infinity, Infinity], [-Infinity, -Infinity],
    [2n ** 100n, 2n ** 100n], [-(2n ** 100n), -(2n ** 100n)],
    ["long odd string", "long odd string"], ["even", "even"],
    [[1, { a: [true] }], [1, { a: [true] }]],
    [{ a: 1, b: 2 }, { b: 2, a: 1 }],
    [Object.assign(Object.create(null), { a: 1 }), { a: 1 }],
    [new Uint8Array([1, 2, 3, 4, 5]), new Uint8Array([1, 2, 3, 4, 5])],
    [new Uint16Array([256, 65535]), new Uint16Array([256, 65535])],
    [new DataView(new Uint8Array([9, 1, 2, 9]).buffer, 1, 2), new DataView(new Uint8Array([1, 2]).buffer)],
];

for (const [i, [a, b]] of equalPairs.entries()) {
    test(`equal values have equal hashes (${i})`, () => {
        assert.ok(equals(a, b));
        assert.ok(equals(b, a));
        assert.strictEqual(hash(a), hash(b));
    });
}

test("structural equality rejects different values and types", () => {
    const pairs: readonly (readonly [unknown, unknown])[] = [
        [null, {}], [1, "1"], [0, -0], [true, false], [[], {}],
        [[1], [1, 2]], [[1], [2]], [{ a: 1 }, { a: 2 }], [{}, { a: 1 }],
        [new Uint8Array([1]), {}], [new Uint8Array([1]), new Uint16Array([1])],
        [new Uint8Array([1]), new Uint8Array([1, 2])],
        [new Uint8Array([1]), new Uint8Array([2])], [new Date(0), new Date(0)],
    ];
    for (const [a, b] of pairs) {
        assert.strictEqual(equals(a, b), false);
        assert.strictEqual(equals(b, a), false);
    }
});

test("identity hashes are stable for objects, functions, and symbols", () => {
    for (const value of [new Date(0), () => 1, Symbol("key")]) {
        assert.strictEqual(hash(value), hash(value));
        assert.ok(equals(value, value));
    }
});

test("symbol properties and custom equality participate in hashing", () => {
    const key = Symbol("key");
    const a = { [key]: 1 };
    const b = { [key]: 1 };
    assert.ok(equals(a, b));
    assert.strictEqual(hash(a), hash(b));
    assert.strictEqual(equals(a, { [key]: 2 }), false);
    const custom = {
        [hashSymbol]() { return 42; },
        [equalsSymbol](other: unknown) { return other === b; },
    };
    assert.strictEqual(hash(custom), 42);
    assert.ok(equals(custom, b));
    assert.strictEqual(equals(custom, {}), false);
});

test("small bigint hashes preserve the signed 32-bit representation", () => {
    for (const [value, expected] of [[0n, 0], [-2147483648n, -2147483648], [2147483647n, 2147483647], [4294967295n, -1]] as const) {
        assert.strictEqual(hash(value), expected);
        assert.strictEqual(hash(value), hash(Number(value)));
    }
});
