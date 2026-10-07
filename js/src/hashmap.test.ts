import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { HashMap } from "./hashmap.js";
import { hashSymbol, equalsSymbol } from "./hash.js";

describe("HashMap", () => {
	test("empty map", () => {
		const map = new HashMap<string, number>();
		assert.strictEqual(map.size, 0);
		assert.strictEqual(map.has("a"), false);
		assert.strictEqual(map.get("a"), undefined);
	});

	test("constructor with entries", () => {
		const map = new HashMap<string, number>([
			["a", 1],
			["b", 2],
		]);
		assert.strictEqual(map.size, 2);
		assert.strictEqual(map.get("a"), 1);
		assert.strictEqual(map.get("b"), 2);
	});

	test("set and update values", () => {
		// Current HashMap implementation only supports setting via constructor
		// as it doesn't have a 'set' method. Let's verify what's there.
		const map = new HashMap<string, number>([
			["a", 1],
			["a", 2],
		]);
		assert.strictEqual(map.size, 1);
		assert.strictEqual(map.get("a"), 2);
	});

	test("different key types", () => {
		const map = new HashMap<unknown, string>([
			[1, "number"],
			["1", "string"],
			[true, "boolean"],
			[null, "null"],
			[undefined, "undefined"],
			[[1, 2], "array"],
			[{ a: 1 }, "object"],
		]);

		assert.strictEqual(map.get(1), "number");
		assert.strictEqual(map.get("1"), "string");
		assert.strictEqual(map.get(true), "boolean");
		assert.strictEqual(map.get(null), "null");
		assert.strictEqual(map.get(undefined), "undefined");
		assert.strictEqual(map.get([1, 2]), "array");
		assert.strictEqual(map.get({ a: 1 }), "object");

		assert.strictEqual(map.size, 7);
	});

	test("custom hash and equals", () => {
		class Custom {
			constructor(readonly id: number, readonly name: string) {}
			[hashSymbol]() {
				return this.id;
			}

			[equalsSymbol](other: unknown) {
				return other instanceof Custom && this.id === other.id;
			}
		}

		const c1 = new Custom(1, "a");
		const c2 = new Custom(1, "b"); // Same id, should be equal
		const c3 = new Custom(2, "c");

		const map = new HashMap<Custom, string>([
			[c1, "one"],
			[c3, "two"],
		]);

		assert.strictEqual(map.size, 2);
		assert.strictEqual(map.has(c2), true);
		assert.strictEqual(map.get(c2), "one");
	});

	test("iteration", () => {
		const entries: [string, number][] = [
			["a", 1],
			["b", 2],
			["c", 3],
		];
		const map = new HashMap<string, number>(entries);

		assert.deepStrictEqual([...map.keys()].sort(), ["a", "b", "c"]);
		assert.deepStrictEqual([...map.values()].sort(), [1, 2, 3]);
		assert.deepStrictEqual([...map.entries()].sort((a, b) => a[0].localeCompare(b[0])), entries);
		assert.deepStrictEqual([...map].sort((a, b) => a[0].localeCompare(b[0])), entries);
	});

	test("collisions", () => {
		// Force collisions by using keys that (might) have same hash
		// Since we don't know the exact hash function without looking deeper,
		// let's use many keys to ensure some buckets have multiple entries.
		const entries: [number, number][] = [];
		for (let i = 0; i < 1000; i++) {
			entries.push([i, i * 2]);
		}
		const map = new HashMap<number, number>(entries);
		assert.strictEqual(map.size, 1000);
		for (let i = 0; i < 1000; i++) {
			assert.strictEqual(map.get(i), i * 2);
		}
	});
});
