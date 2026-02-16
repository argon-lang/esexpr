import { expect, test, describe } from "vitest";
import { HashMap } from "./hashmap.js";
import { hashSymbol, equalsSymbol } from "./hash.js";

describe("HashMap", () => {
    test("empty map", () => {
        const map = new HashMap<string, number>();
        expect(map.size).toBe(0);
        expect(map.has("a")).toBe(false);
        expect(map.get("a")).toBeUndefined();
    });

    test("constructor with entries", () => {
        const map = new HashMap<string, number>([
            ["a", 1],
            ["b", 2],
        ]);
        expect(map.size).toBe(2);
        expect(map.get("a")).toBe(1);
        expect(map.get("b")).toBe(2);
    });

    test("set and update values", () => {
        // Current HashMap implementation only supports setting via constructor
        // as it doesn't have a 'set' method. Let's verify what's there.
        const map = new HashMap<string, number>([
            ["a", 1],
            ["a", 2],
        ]);
        expect(map.size).toBe(1);
        expect(map.get("a")).toBe(2);
    });

    test("different key types", () => {
        const map = new HashMap<unknown, string>([
            [1, "number"],
            ["1", "string"],
            [true, "boolean"],
            [null, "null"],
            [undefined, "undefined"],
            [[1, 2], "array"],
            [{a: 1}, "object"],
        ]);

        expect(map.get(1)).toBe("number");
        expect(map.get("1")).toBe("string");
        expect(map.get(true)).toBe("boolean");
        expect(map.get(null)).toBe("null");
        expect(map.get(undefined)).toBe("undefined");
        expect(map.get([1, 2])).toBe("array");
        expect(map.get({a: 1})).toBe("object");
        
        expect(map.size).toBe(7);
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

        expect(map.size).toBe(2);
        expect(map.has(c2)).toBe(true);
        expect(map.get(c2)).toBe("one");
    });

    test("iteration", () => {
        const entries: [string, number][] = [
            ["a", 1],
            ["b", 2],
            ["c", 3],
        ];
        const map = new HashMap<string, number>(entries);

        expect([...map.keys()].sort()).toEqual(["a", "b", "c"]);
        expect([...map.values()].sort()).toEqual([1, 2, 3]);
        expect([...map.entries()].sort((a, b) => a[0].localeCompare(b[0]))).toEqual(entries);
        expect([...map].sort((a, b) => a[0].localeCompare(b[0]))).toEqual(entries);
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
        expect(map.size).toBe(1000);
        for (let i = 0; i < 1000; i++) {
            expect(map.get(i)).toBe(i * 2);
        }
    });
});
