import type {HashEq} from "./hash.js";

export interface VMap<K, V> {
    readonly hash: HashEq<K>;
    readonly size: number;
    get(key: K): V | undefined;
    has(key: K): boolean;

    entries(): IterableIterator<[K, V], undefined, undefined>;
    keys(): IterableIterator<K, undefined, undefined>;
    values(): IterableIterator<V, undefined, undefined>;
    [Symbol.iterator](): IterableIterator<[K, V], undefined, undefined>;
}

export namespace VMap {
    export function create<K, V>(hash: HashEq<K>, entries?: readonly (readonly [K, V])[] | null): VMap<K, V> {
        if(hash.isPrimitive) {
            return new PrimitiveVMap(hash, new Map(entries));
        }
        else {
            return new HashMap(hash, entries);
        }
    }
}

class HashMap<K, V> implements VMap<K, V> {
    constructor(readonly hash: HashEq<K>, entries?: readonly (readonly [K, V])[] | null) {
        for (let i = 0; i < HashMap.#BUCKET_COUNT; i++) {
            this.#buckets.push([]);
        }

        if (entries) {
            for (const [k, v] of entries) {
                const h = hash.hash(k) >>> 0;
                const bucketIndex = (h % HashMap.#BUCKET_COUNT + HashMap.#BUCKET_COUNT) % HashMap.#BUCKET_COUNT;
                const bucket = this.#buckets[bucketIndex]!;

                let found = false;
                for (const entry of bucket) {
                    if (entry[0] === h && hash.equals(entry[1], k)) {
                        entry[2] = v;
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    bucket.push([h, k, v]);
                    this.#size++;
                }
            }
        }
    }

    static readonly #BUCKET_COUNT = 256;
    readonly #buckets: [number, K, v: V][][] = [];
    #size = 0;

    get size(): number {
        return this.#size;
    }

    get(key: K): V | undefined {
        const h = this.hash.hash(key) >>> 0;
        const bucketIndex = (h % HashMap.#BUCKET_COUNT + HashMap.#BUCKET_COUNT) % HashMap.#BUCKET_COUNT;
        const bucket = this.#buckets[bucketIndex]!;

        for (const [entryHash, k, v] of bucket) {
            if (entryHash === h && this.hash.equals(k, key)) {
                return v;
            }
        }

        return undefined;
    }

    has(key: K): boolean {
        const h = this.hash.hash(key) >>> 0;
        const bucketIndex = (h % HashMap.#BUCKET_COUNT + HashMap.#BUCKET_COUNT) % HashMap.#BUCKET_COUNT;
        const bucket = this.#buckets[bucketIndex]!;

        for (const [entryHash, k, _] of bucket) {
            if (entryHash === h && this.hash.equals(k, key)) {
                return true;
            }
        }

        return false;
    }

    *entries(): IterableIterator<[K, V], undefined, undefined> {
        for (const bucket of this.#buckets) {
            for (const [_, k, v] of bucket) {
                yield [k, v];
            }
        }
        return undefined;
    }

    *keys(): IterableIterator<K, undefined, undefined> {
        for (const bucket of this.#buckets) {
            for (const [_, k, _v] of bucket) {
                yield k;
            }
        }
        return undefined;
    }

    *values(): IterableIterator<V, undefined, undefined> {
        for (const bucket of this.#buckets) {
            for (const [_, _k, v] of bucket) {
                yield v;
            }
        }
        return undefined;
    }


    [Symbol.iterator](): IterableIterator<[K, V], undefined, undefined> {
        return this.entries();
    }
}

class PrimitiveVMap<K, V> implements VMap<K, V> {
    constructor(readonly hash: HashEq<K>, map: ReadonlyMap<K, V>) {
        this.#map = map;
    }

    readonly #map: ReadonlyMap<K, V>;

    get size(): number {
        return this.#map.size;
    }

    get(key: K): V | undefined {
        return this.#map.get(key);
    }

    has(key: K): boolean {
        return this.#map.has(key);
    }

    entries(): IterableIterator<[K, V], undefined, undefined> {
        return this.#map.entries();
    }

    keys(): IterableIterator<K, undefined, undefined> {
        return this.#map.keys();
    }

    values(): IterableIterator<V, undefined, undefined> {
        return this.#map.values();
    }


    [Symbol.iterator](): IterableIterator<[K, V], undefined, undefined> {
        return this.#map[Symbol.iterator]();
    }
}



