import { expect, test, describe } from "vitest";
import { HashSet } from "./hashset.js";
import { hashSymbol, equalsSymbol } from "./hash.js";

describe("HashSet", () => {
    test("empty set", () => {
        const set = new HashSet<string>();
        expect(set.size).toBe(0);
        expect(set.has("a")).toBe(false);
    });

    test("constructor with values", () => {
        const set = new HashSet<string>(["a", "b", "a"]);
        expect(set.size).toBe(2);
        expect(set.has("a")).toBe(true);
        expect(set.has("b")).toBe(true);
        expect(set.has("c")).toBe(false);
    });

    test("different value types", () => {
        const values = [
            1,
            "1",
            true,
            null,
            undefined,
            [1, 2],
            {a: 1},
        ];
        const set = new HashSet<unknown>(values);

        for (const v of values) {
            expect(set.has(v)).toBe(true);
        }
        
        expect(set.size).toBe(7);
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

        expect(set.size).toBe(2);
        expect(set.has(c2)).toBe(true);
    });

    test("iteration", () => {
        const values = ["a", "b", "c"];
        const set = new HashSet<string>(values);

        expect([...set.values()].sort()).toEqual(values);
        expect([...set].sort()).toEqual(values);
    });

    test("collisions", () => {
        const values: number[] = [];
        for (let i = 0; i < 1000; i++) {
            values.push(i);
        }
        const set = new HashSet<number>(values);
        expect(set.size).toBe(1000);
        for (let i = 0; i < 1000; i++) {
            expect(set.has(i)).toBe(true);
        }
    });
});
