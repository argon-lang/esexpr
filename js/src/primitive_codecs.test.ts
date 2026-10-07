import { test } from "node:test";
import assert from "node:assert/strict";
import * as e from "./index.js";

function checkCodec<T>(codec: e.ESExprCodec<T>, value: T, different: T, expr: e.ESExpr, tag: e.ESExprTag): void {
    assert.deepStrictEqual(codec.tags, new Set([tag]));
    assert.deepStrictEqual(codec.encode(value), expr);
    assert.deepStrictEqual(codec.decode(expr), { success: true, value });
    assert.ok(codec.isEncodedEqual(value, value));
    assert.strictEqual(codec.isEncodedEqual(value, different), false);
    const failure = codec.decode(null);
    assert.strictEqual(failure.success, false);
    if (!failure.success) {
        assert.deepStrictEqual(failure.path, { type: "current" });
        assert.ok(failure.message.length > 0);
    }
}

test("primitive codecs round trip and reject incorrect types", () => {
    checkCodec(e.boolCodec, true, false, true, Boolean);
    checkCodec(e.intCodec, 123n, 124n, 123n, BigInt);
    checkCodec(e.natCodec, 123n, 124n, 123n, BigInt);
    checkCodec(e.strCodec, "hello", "world", "hello", String);
    assert.deepStrictEqual(e.natCodec.decode(-1n), {
        success: false, message: "Nat values must be non-negative", path: { type: "current" },
    });
});

const smallIntegers: readonly (readonly [string, e.ESExprCodec<number>, number, number])[] = [
    ["int8", e.signedInt8Codec, -128, 127], ["uint8", e.unsignedInt8Codec, 0, 255],
    ["int16", e.signedInt16Codec, -32768, 32767], ["uint16", e.unsignedInt16Codec, 0, 65535],
    ["int32", e.signedInt32Codec, -2147483648, 2147483647],
    ["uint32", e.unsignedInt32Codec, 0, 4294967295],
];
for (const [name, codec, min, max] of smallIntegers) {
    test(`${name} accepts boundaries and rejects overflow`, () => {
        checkCodec(codec, min, max, BigInt(min), BigInt);
        assert.deepStrictEqual(codec.decode(BigInt(max)), { success: true, value: max });
        for (const value of [BigInt(min) - 1n, BigInt(max) + 1n]) {
            assert.deepStrictEqual(codec.decode(value), {
                success: false, message: "Integer value is out of range", path: { type: "current" },
            });
        }
    });
}
for (const [name, codec, min, max] of [
    ["int64", e.signedInt64Codec, -(2n ** 63n), 2n ** 63n - 1n],
    ["uint64", e.unsignedInt64Codec, 0n, 2n ** 64n - 1n],
] as const) {
    test(`${name} preserves large integers and rejects overflow`, () => {
        checkCodec(codec, max, min, max, BigInt);
        assert.deepStrictEqual(codec.decode(min), { success: true, value: min });
        for (const value of [min - 1n, max + 1n]) {
            assert.strictEqual(codec.decode(value).success, false);
        }
    });
}

function checkArray<T>(codec: e.ESExprCodec<T>, a: T, copy: T, shorter: T, changed: T, expr: e.ESExpr, tag: e.ESExprTag): void {
    checkCodec(codec, a, changed, expr, tag);
    assert.ok(codec.isEncodedEqual(a, copy));
    assert.strictEqual(codec.isEncodedEqual(a, shorter), false);
}

test("array codecs compare contents and preserve their element types", () => {
    checkArray(e.array8Codec, new Uint8Array([1, 2]), new Uint8Array([1, 2]), new Uint8Array([1]), new Uint8Array([1, 3]), new Uint8Array([1, 2]), Uint8Array);
    checkArray(e.array16Codec, new Uint16Array([1, 2]), new Uint16Array([1, 2]), new Uint16Array([1]), new Uint16Array([1, 3]), new Uint16Array([1, 2]), Uint16Array);
    checkArray(e.array32Codec, new Uint32Array([1, 2]), new Uint32Array([1, 2]), new Uint32Array([1]), new Uint32Array([1, 3]), new Uint32Array([1, 2]), Uint32Array);
    checkArray(e.array64Codec, new BigUint64Array([1n, 2n]), new BigUint64Array([1n, 2n]), new BigUint64Array([1n]), new BigUint64Array([1n, 3n]), new BigUint64Array([1n, 2n]), BigUint64Array);
    const bytes = new Uint8Array(16);
    const changed = bytes.slice();
    changed[15] = 1;
    checkArray(e.array128Codec, bytes, bytes.slice(), new Uint8Array(), changed, { type: "array128", value: bytes }, e.Array128Symbol);
});

for (const [name, codec, preserving, nan, tag] of [
    ["float16", e.float16Codec, e.float16NaNCodec, { type: "float16-nan", bits: 0x7E01 }, e.Float16Symbol],
    ["float32", e.float32Codec, e.float32NaNCodec, { type: "float32-nan", bits: 0x7FC00001 }, e.Float32Symbol],
    ["float64", e.float64Codec, e.float64NaNCodec, { type: "float64-nan", bits: 0x7FF8000000000001n }, Number],
] as const) {
    test(`${name} preserves signed zero and NaN payloads`, () => {
        const expr: e.ESExpr = name === "float64" ? 1.5 : { type: name, value: 1.5 };
        checkCodec(codec, 1.5, 2.5, expr, tag);
        assert.ok(codec.isEncodedEqual(NaN, NaN));
        assert.strictEqual(codec.isEncodedEqual(0, -0), false);
        assert.deepStrictEqual(codec.decode(nan), { success: true, value: NaN });
        // Exercise each payload-preserving codec with its corresponding representation.
        if (name === "float16") {
            checkCodec(e.float16NaNCodec, nan, 1.5, nan, tag);
            assert.ok(e.float16NaNCodec.isEncodedEqual(nan, { ...nan }));
            assert.strictEqual(e.float16NaNCodec.isEncodedEqual(nan, { ...nan, bits: nan.bits + 1 }), false);
        } else if (name === "float32") {
            checkCodec(e.float32NaNCodec, nan, 1.5, nan, tag);
            assert.ok(e.float32NaNCodec.isEncodedEqual(nan, { ...nan }));
            assert.strictEqual(e.float32NaNCodec.isEncodedEqual(nan, { ...nan, bits: nan.bits + 1 }), false);
        } else {
            checkCodec(e.float64NaNCodec, nan, 1.5, nan, tag);
            assert.ok(e.float64NaNCodec.isEncodedEqual(nan, { ...nan }));
            assert.strictEqual(e.float64NaNCodec.isEncodedEqual(nan, { ...nan, bits: nan.bits + 1n }), false);
        }
        assert.deepStrictEqual(preserving.encode(1.5), expr);
        assert.deepStrictEqual(preserving.decode(expr), { success: true, value: 1.5 });
        assert.ok(preserving.isEncodedEqual(NaN, NaN));
    });
}

test("list decoding identifies invalid element paths and rejects keywords", () => {
    const codec = e.listCodec(e.boolCodec);
    const expr: e.ESExpr.Constructor = { type: "constructor", name: "list", args: [true, "bad"], kwargs: new Map() };
    const result = codec.decode(expr);
    assert.strictEqual(result.success, false);
    if (!result.success) {
        assert.deepStrictEqual(result.path, { type: "positional", constructor: "list", index: 1, next: { type: "current" } });
    }
    assert.strictEqual(codec.decode({ ...expr, args: [], kwargs: new Map([["a", true]]) }).success, false);
    assert.strictEqual(codec.decode(null).success, false);
    assert.deepStrictEqual(codec.tags, new Set(["list"]));
    assert.ok(codec.isEncodedEqual([true, false], [true, false]));
    assert.strictEqual(codec.isEncodedEqual([true], []), false);
    assert.strictEqual(codec.isEncodedEqual([true], [false]), false);
});

test("lazy codecs initialize once and delegate every operation", () => {
    let calls = 0;
    const codec = e.lazyCodec(() => { calls++; return e.strCodec; });
    assert.strictEqual(calls, 0);
    checkCodec(codec, "a", "b", "a", String);
    assert.strictEqual(calls, 1);
});
