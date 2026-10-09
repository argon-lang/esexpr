import { test } from "node:test";
import assert from "node:assert/strict";
import * as e from "./index.js";

function ctor(name: string, args: readonly e.ESExpr[] = [], kwargs: ReadonlyMap<string, e.ESExpr> = new Map()): e.ESExpr.Constructor {
	return { type: "constructor", name, args, kwargs };
}
function failurePath<T>(codec: e.ESExprCodec<T>, expr: e.ESExpr, path: e.DecodeErrorPath): void {
	const result = codec.decode(expr);
	assert.strictEqual(result.success, false);
	if(!result.success) assert.deepStrictEqual(result.path, path);
}

test("hash map codec round trips entries and compares keys and values", () => {
	const codec = e.hashmapCodec(e.strCodec, e.boolCodec);
	const value = new e.HashMap([["a", true], ["b", false]]);
	const expr = ctor("map", ["a", true, "b", false]);
	assert.deepStrictEqual(codec.tags, new Set(["map"]));
	assert.deepStrictEqual(codec.encode(value), ctor("map", [...value].flatMap(([key, item]) => [key, item])));
	const decoded = codec.decode(expr);
	assert.ok(decoded.success);
	assert.deepStrictEqual([...decoded.value], [...value]);
	assert.ok(codec.isEncodedEqual(value, new e.HashMap([["b", false], ["a", true]])));
	for(const other of [new e.HashMap<string, boolean>(), new e.HashMap([["c", true], ["b", false]]), new e.HashMap([["a", false], ["b", false]])]) assert.strictEqual(codec.isEncodedEqual(value, other), false);
	for(const invalid of [null, ctor("wrong"), ctor("map", [], new Map([["a", true]]))]) failurePath(codec, invalid, { type: "current" });
	failurePath(codec, ctor("map", [false, true]), { type: "positional", constructor: "map", index: 0, next: { type: "current" } });
	failurePath(codec, ctor("map", ["a", "bad"]), { type: "positional", constructor: "map", index: 1, next: { type: "current" } });
});

test("hash set codec deduplicates values and reports invalid item positions", () => {
	const codec = e.hashsetCodec(e.strCodec);
	const value = new e.HashSet(["a", "b"]);
	assert.deepStrictEqual(codec.tags, new Set(["set"]));
	assert.deepStrictEqual(codec.encode(value), ctor("set", [...value]));
	const decoded = codec.decode(ctor("set", ["a", "b", "a"]));
	assert.ok(decoded.success);
	assert.deepStrictEqual([...decoded.value].sort(), ["a", "b"]);
	assert.ok(codec.isEncodedEqual(value, new e.HashSet(["b", "a"])));
	assert.strictEqual(codec.isEncodedEqual(value, new e.HashSet(["a"])), false);
	assert.strictEqual(codec.isEncodedEqual(value, new e.HashSet(["a", "c"])), false);
	for(const invalid of [null, ctor("wrong"), ctor("set", [], new Map([["a", true]]))]) failurePath(codec, invalid, { type: "current" });
	failurePath(codec, ctor("set", ["a", false]), { type: "positional", constructor: "set", index: 1, next: { type: "current" } });
});

test("option codec distinguishes absence, present values, and present null", () => {
	const codec = e.optionCodec(e.strCodec);
	assert.deepStrictEqual(codec.tags, new Set([null, String]));
	assert.strictEqual(codec.encode(null), null);
	assert.deepStrictEqual(codec.decode(null), { success: true, value: null });
	assert.strictEqual(codec.encode(e.Option.some("a")), "a");
	assert.deepStrictEqual(codec.decode("a"), { success: true, value: e.Option.some("a") });
	assert.ok(codec.isEncodedEqual(null, null));
	assert.ok(codec.isEncodedEqual("a", "a"));
	for(const [a, b] of [[null, "a"], ["a", null], ["a", "b"]] as const) assert.strictEqual(codec.isEncodedEqual(a, b), false);
	failurePath(codec, true, { type: "current" });
	const nested = e.optionCodec(codec);
	const presentNull = e.Option.some(null);
	assert.deepStrictEqual(nested.encode(presentNull), { type: "null", level: 1n });
	assert.deepStrictEqual(nested.decode({ type: "null", level: 1n }), { success: true, value: presentNull });
	const twice = e.Option.some(presentNull);
	assert.strictEqual(e.Option.get(twice), presentNull);
	assert.deepStrictEqual(e.optionCodec(nested).encode(twice), { type: "null", level: 2n });
	assert.strictEqual(e.optionCodec(e.ESExpr.codec).tags, e.ESExprTagSet.All);
});

test("record failures retain positional and keyword paths", () => {
	const codec = e.recordCodec("record", { a: e.positionalFieldCodec(e.boolCodec), b: e.keywordFieldCodec("b", e.strCodec) });
	assert.ok(codec.isEncodedEqual({ a: true, b: "a" }, { a: true, b: "a" }));
	for(const other of [{ a: false, b: "a" }, { a: true, b: "b" }]) assert.strictEqual(codec.isEncodedEqual({ a: true, b: "a" }, other), false);
	failurePath(codec, ctor("record"), { type: "constructor", constructor: "record" });
	failurePath(codec, ctor("record", [true]), { type: "constructor", constructor: "record" });
	failurePath(codec, ctor("record", ["bad"]), { type: "positional", constructor: "record", index: 0, next: { type: "current" } });
	failurePath(codec, ctor("record", [true], new Map([["b", false]])), { type: "keyword", constructor: "record", keyword: "b", next: { type: "current" } });
	failurePath(codec, ctor("record", [true, false], new Map([["b", "a"]])), { type: "positional", constructor: "record", index: 1, next: { type: "current" } });
	failurePath(codec, ctor("record", [true], new Map([["b", "a"], ["extra", "x"]])), { type: "keyword", constructor: "record", keyword: "extra", next: { type: "current" } });
});

test("records safely decode fields named after Object prototype properties", () => {
	const codec = e.recordCodec("record", { toString: e.keywordFieldCodec("name", e.strCodec) });
	const result = codec.decode(ctor("record", [], new Map([["name", "value"]])));
	assert.ok(result.success);
	assert.strictEqual(result.value.toString, "value");
	assert.ok(Object.hasOwn(result.value, "toString"));
	assert.strictEqual(Object.getPrototypeOf(result.value), Object.prototype);
});

test("default positional fields omit defaults and leave other tags for following fields", () => {
	const field = e.defaultPositionalFieldCodec(e.signedInt8Codec, () => 4);
	const codec = e.recordCodec("r", { a: field, b: e.positionalFieldCodec(e.strCodec) });
	assert.deepStrictEqual(field.tags, new Set([BigInt]));
	assert.ok(field.isEncodedEqual(4, 4));
	assert.strictEqual(field.isEncodedEqual(4, 5), false);
	for(const [value, args] of [[{ a: 4, b: "x" }, ["x"]], [{ a: 5, b: "x" }, [5n, "x"]]] as const) {
		assert.deepStrictEqual(codec.encode(value), ctor("r", args));
		assert.deepStrictEqual(codec.decode(ctor("r", args)), { success: true, value });
	}
	assert.deepStrictEqual(e.recordCodec("r", { a: field }).decode(ctor("r")), { success: true, value: { a: 4 } });
	assert.strictEqual(codec.decode(ctor("r", [128n, "x"])).success, false);
});

test("optional fields compare absence and propagate invalid values", () => {
	const optional = e.undefinedOptionalCodec(e.signedInt8Codec);
	assert.ok(optional.isEncodedEqual(undefined, undefined));
	assert.ok(optional.isEncodedEqual(1, 1));
	for(const [a, b] of [[undefined, 1], [1, undefined], [1, 2]] as const) assert.strictEqual(optional.isEncodedEqual(a, b), false);
	for(const field of [e.optionalPositionalFieldCodec(optional), e.optionalKeywordFieldCodec("a", optional)]) {
		assert.deepStrictEqual(field.tags, new Set([BigInt]));
		assert.ok(field.isEncodedEqual(undefined, undefined));
		assert.strictEqual(field.isEncodedEqual(1, 2), false);
		const codec = e.recordCodec("r", { a: field });
		assert.deepStrictEqual(codec.decode(codec.encode({ a: undefined })), { success: true, value: { a: undefined } });
		assert.deepStrictEqual(codec.decode(codec.encode({ a: 1 })), { success: true, value: { a: 1 } });
		assert.strictEqual(codec.decode(codec.encode({ a: 128 })).success, false);
	}
	failurePath(e.recordCodec("r", { a: e.optionalPositionalFieldCodec(optional) }), ctor("r", [128n]), { type: "positional", constructor: "r", index: 0, next: { type: "current" } });
});

test("default keyword fields omit defaults and identify invalid keyword values", () => {
	const field = e.defaultKeywordFieldCodec("a", () => 4, e.signedInt8Codec);
	assert.deepStrictEqual(field.tags, new Set([BigInt]));
	assert.ok(field.isEncodedEqual(4, 4));
	assert.strictEqual(field.isEncodedEqual(4, 5), false);
	const codec = e.recordCodec("r", { a: field });
	assert.deepStrictEqual(codec.encode({ a: 4 }), ctor("r"));
	assert.deepStrictEqual(codec.decode(ctor("r")), { success: true, value: { a: 4 } });
	failurePath(codec, ctor("r", [], new Map([["a", 128n]])), { type: "keyword", constructor: "r", keyword: "a", next: { type: "current" } });
});

test("vararg and dictionary fields compare collections and retain failure locations", () => {
	const repeated = e.arrayRepeatedValuesCodec(e.signedInt8Codec);
	const vararg = e.varargFieldCodec(repeated);
	assert.deepStrictEqual(vararg.tags, new Set([BigInt]));
	assert.ok(vararg.isEncodedEqual([1, 2], [1, 2]));
	assert.strictEqual(vararg.isEncodedEqual([1], []), false);
	assert.strictEqual(vararg.isEncodedEqual([1], [2]), false);
	failurePath(e.recordCodec("r", { a: vararg }), ctor("r", [1n, 128n]), { type: "positional", constructor: "r", index: 1, next: { type: "current" } });
	const mapped = e.mapMappedValueCodec(e.signedInt8Codec);
	const dict = e.dictFieldCodec(mapped);
	assert.deepStrictEqual(dict.tags, new Set([BigInt]));
	const a = new Map([["a", 1]]);
	assert.ok(dict.isEncodedEqual(a, new Map(a)));
	for(const b of [new Map<string, number>(), new Map([["b", 1]]), new Map([["a", 2]])]) assert.strictEqual(dict.isEncodedEqual(a, b), false);
	failurePath(e.recordCodec("r", { a: dict }), ctor("r", [], new Map([["a", 128n]])), { type: "keyword", constructor: "r", keyword: "a", next: { type: "current" } });
});

test("record validation rejects duplicate keywords and incompatible dict fields", () => {
	const keyword = e.keywordFieldCodec("same", e.strCodec);
	const dict = e.dictFieldCodec(e.mapMappedValueCodec(e.strCodec));
	assert.throws(() => e.recordCodec("r", { a: keyword, b: keyword }), /Duplicate keyword argument/);
	assert.throws(() => e.recordCodec("r", { a: dict, b: dict }), /Only a single dict/);
	assert.throws(() => e.recordCodec("r", { a: keyword, b: dict }), /Keyword arguments cannot/);
	assert.throws(() => e.recordCodec("r", { a: dict, b: keyword }), /Keyword arguments cannot/);
	assert.throws(() => e.recordCodec("r", {
		a: e.optionalPositionalFieldCodec(e.undefinedOptionalCodec(e.ESExpr.codec)),
		b: e.positionalFieldCodec(e.strCodec),
	}), /cannot follow optional positional arguments with all tags/);
});

test("enums compare cases and reject overlapping tags and invalid payloads", () => {
	type Value = { readonly $type: "box", readonly value: number } | { readonly $type: "inline", readonly value: number };
	const box = e.caseCodec<"box", Extract<Value, { $type: "box" }>>("box", { value: e.positionalFieldCodec(e.signedInt8Codec) });
	const inline = e.inlineCaseCodec<"value", "inline", number>("value", e.signedInt8Codec);
	const codec = e.enumCodec<Value>({ box, inline });
	assert.ok(codec.isEncodedEqual({ $type: "box", value: 1 }, { $type: "box", value: 1 }));
	assert.ok(codec.isEncodedEqual({ $type: "inline", value: 1 }, { $type: "inline", value: 1 }));
	assert.strictEqual(codec.isEncodedEqual({ $type: "box", value: 1 }, { $type: "inline", value: 1 }), false);
	assert.strictEqual(codec.isEncodedEqual({ $type: "inline", value: 1 }, { $type: "inline", value: 2 }), false);
	failurePath(codec, true, { type: "current" });
	failurePath(codec, 128n, { type: "current" });
	failurePath(codec, ctor("box", [128n]), { type: "positional", constructor: "box", index: 0, next: { type: "current" } });
	assert.throws(() => e.enumCodec({ a: e.inlineCaseCodec("value", e.strCodec), b: e.inlineCaseCodec("value", e.strCodec) }), /Overlapping tags/);
});

test("simple enums reject duplicate names and nonstring input", () => {
	assert.throws(() => e.simpleEnumCodec({ a: "same", b: "same" }), /duplicate value/);
	const codec = e.simpleEnumCodec({ a: "first", b: "second" });
	assert.deepStrictEqual(codec.tags, new Set([String]));
	assert.ok(codec.isEncodedEqual("a", "a"));
	assert.strictEqual(codec.isEncodedEqual("a", "b"), false);
	failurePath(codec, true, { type: "current" });
});

test("flags compare encoded values and safely decode prototype property names", () => {
	const codec = e.flags({ toString: e.flagBit(1n) });
	assert.ok(codec.isEncodedEqual({ toString: true }, { toString: true }));
	assert.strictEqual(codec.isEncodedEqual({ toString: true }, { toString: false }), false);
	const result = codec.decode(1n);
	assert.ok(result.success);
	assert.strictEqual(result.value.toString, true);
	assert.ok(Object.hasOwn(result.value, "toString"));
});

test("deeply nested options preserve each present-null level on decode", () => {
	const codec = e.optionCodec(e.optionCodec(e.optionCodec(e.strCodec)));
	const value = e.Option.some(e.Option.some(null));
	const expr = { type: "null", level: 2n } as const;
	assert.deepStrictEqual(codec.encode(value), expr);
	assert.deepStrictEqual(codec.decode(expr), { success: true, value });
});
