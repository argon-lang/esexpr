import { expect, test } from "vitest";
import { VSet } from "./vset.js";
import type { HashEq } from "./hash.js";

test("PrimitiveVSet", () => {
    const hash: HashEq<string> = {
        isPrimitive: true,
        hash: (s) => s.length,
        equals: (a, b) => a === b,
    };

    const set = VSet.create(hash, ["a", "b"]);
    expect(set.size).toBe(2);
    expect(set.has("a")).toBe(true);
    expect(set.has("b")).toBe(true);
    expect(set.has("c")).toBe(false);

    const values = Array.from(set.values());
    expect(values).toContain("a");
    expect(values).toContain("b");
});

test("HashSet with non-primitive values", () => {
    interface Item {
        id: number;
    }

    const hash: HashEq<Item> = {
        hash: (k) => k.id,
        equals: (a, b) => a.id === b.id,
    };

    const i1 = { id: 1 };
    const i1_alt = { id: 1 };
    const i2 = { id: 2 };

    const set = VSet.create(hash, [i1, i2]);
    expect(set.size).toBe(2);
    expect(set.has(i1)).toBe(true);
    expect(set.has(i1_alt)).toBe(true);
    expect(set.has(i2)).toBe(true);

    const values = Array.from(set.values());
    expect(values.length).toBe(2);
});

test("HashSet collisions", () => {
    const hash: HashEq<number> = {
        hash: () => 1, // All values collide
        equals: (a, b) => a === b,
    };

    const set = VSet.create(hash, [1, 2]);
    expect(set.size).toBe(2);
    expect(set.has(1)).toBe(true);
    expect(set.has(2)).toBe(true);
    expect(set.has(3)).toBe(false);
});

test("HashSet many values", () => {
    const hash: HashEq<number> = {
        hash: (n) => n,
        equals: (a, b) => a === b,
    };

    const values: number[] = [];
    for (let i = 0; i < 100; i++) {
        values.push(i);
    }

    const set = VSet.create(hash, values);
    expect(set.size).toBe(100);

    for (let i = 0; i < 100; i++) {
        expect(set.has(i)).toBe(true);
    }

    expect(set.has(100)).toBe(false);
});
