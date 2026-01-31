import { expect, test } from "vitest";
import { VMap } from "./vmap.js";
import type { HashEq } from "./hash.js";

test("PrimitiveVMap", () => {
    const hash: HashEq<string> = {
        isPrimitive: true,
        hash: (s) => s.length,
        equals: (a, b) => a === b,
    };

    const map = VMap.create(hash, [["a", 1], ["b", 2]]);
    expect(map.size).toBe(2);
    expect(map.get("a")).toBe(1);
    expect(map.get("b")).toBe(2);
    expect(map.get("c")).toBeUndefined();
    expect(map.has("a")).toBe(true);
    expect(map.has("c")).toBe(false);

    const keys = Array.from(map.keys());
    expect(keys).toContain("a");
    expect(keys).toContain("b");

    const values = Array.from(map.values());
    expect(values).toContain(1);
    expect(values).toContain(2);

    const entries = Array.from(map.entries());
    expect(entries).toContainEqual(["a", 1]);
    expect(entries).toContainEqual(["b", 2]);
});

test("HashMap with non-primitive keys", () => {
    interface Key {
        id: number;
    }

    const hash: HashEq<Key> = {
        hash: (k) => k.id,
        equals: (a, b) => a.id === b.id,
    };

    const k1 = { id: 1 };
    const k1_alt = { id: 1 };
    const k2 = { id: 2 };

    const map = VMap.create(hash, [[k1, "val1"], [k2, "val2"]]);
    expect(map.size).toBe(2);
    expect(map.get(k1)).toBe("val1");
    expect(map.get(k1_alt)).toBe("val1");
    expect(map.get(k2)).toBe("val2");
    expect(map.has(k1_alt)).toBe(true);

    const entries = Array.from(map.entries());
    expect(entries.length).toBe(2);
});

test("HashMap collisions", () => {
    const hash: HashEq<number> = {
        hash: () => 1, // All keys collide
        equals: (a, b) => a === b,
    };

    const map = VMap.create(hash, [[1, "a"], [2, "b"]]);
    expect(map.size).toBe(2);
    expect(map.get(1)).toBe("a");
    expect(map.get(2)).toBe("b");
    expect(map.get(3)).toBeUndefined();
});

test("HashMap many entries", () => {
    const hash: HashEq<number> = {
        hash: (n) => n,
        equals: (a, b) => a === b,
    };

    const entries: [number, string][] = [];
    for (let i = 0; i < 100; i++) {
        entries.push([i, `val${i}`]);
    }

    const map = VMap.create(hash, entries);
    expect(map.size).toBe(100);

    for (let i = 0; i < 100; i++) {
        expect(map.get(i)).toBe(`val${i}`);
    }

    expect(map.get(100)).toBeUndefined();
});
