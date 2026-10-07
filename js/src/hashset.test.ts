import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { HashSet } from "./hashset.js";
import { hashSymbol, equalsSymbol } from "./hash.js";

describe("HashSet", () => {
	test("empty set", () => {
		const set = new HashSet<string>();
		assert.strictEqual(set.size, 0);
		assert.strictEqual(set.has("a"), false);
	});

	test("constructor with values", () => {
		const set = new HashSet<string>(["a", "b", "a"]);
		assert.strictEqual(set.size, 2);
		assert.strictEqual(set.has("a"), true);
		assert.strictEqual(set.has("b"), true);
		assert.strictEqual(set.has("c"), false);
	});

	test("different value types", () => {
		const values = [
			1,
			"1",
			true,
			null,
			undefined,
			[1, 2],
			{ a: 1 },
		];
		const set = new HashSet<unknown>(values);

		for (const v of values) {
			assert.strictEqual(set.has(v), true);
		}

		assert.strictEqual(set.size, 7);
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

		const set = new HashSet<Custom>([c1, c3]);

		assert.strictEqual(set.size, 2);
		assert.strictEqual(set.has(c2), true);
	});

	test("iteration", () => {
		const values = ["a", "b", "c"];
		const set = new HashSet<string>(values);

		assert.deepStrictEqual([...set.values()].sort(), values);
		assert.deepStrictEqual([...set].sort(), values);
	});

	test("collisions", () => {
		const values: number[] = [];
		for (let i = 0; i < 1000; i++) {
			values.push(i);
		}
		const set = new HashSet<number>(values);
		assert.strictEqual(set.size, 1000);
		for (let i = 0; i < 1000; i++) {
			assert.strictEqual(set.has(i), true);
		}
	});
});
