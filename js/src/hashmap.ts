import {hash, equals} from "./hash.js";

export class HashMap<K, V> {
    constructor(entries?: readonly (readonly [K, V])[] | null) {
        this.#buckets = Array.from({length: HashMap.#BUCKET_COUNT}, () => []);

        if (entries) {
            for (const [k, v] of entries) {
                const h = hash(k) >>> 0;
                const bucketIndex = (h % HashMap.#BUCKET_COUNT + HashMap.#BUCKET_COUNT) % HashMap.#BUCKET_COUNT;
                const bucket = this.#buckets[bucketIndex]!;

                let found = false;
                for (const entry of bucket) {
                    if (entry[0] === h && equals(entry[1], k)) {
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
    readonly #buckets: [number, K, v: V][][];
    #size = 0;

    get size(): number {
        return this.#size;
    }

    get(key: K): V | undefined {
        const h = hash(key) >>> 0;
        const bucketIndex = (h % HashMap.#BUCKET_COUNT + HashMap.#BUCKET_COUNT) % HashMap.#BUCKET_COUNT;
        const bucket = this.#buckets[bucketIndex]!;

        for (const [entryHash, k, v] of bucket) {
            if (entryHash === h && equals(k, key)) {
                return v;
            }
        }

        return undefined;
    }

    has(key: K): boolean {
        const h = hash(key) >>> 0;
        const bucketIndex = (h % HashMap.#BUCKET_COUNT + HashMap.#BUCKET_COUNT) % HashMap.#BUCKET_COUNT;
        const bucket = this.#buckets[bucketIndex]!;

        for (const [entryHash, k, _] of bucket) {
            if (entryHash === h && equals(k, key)) {
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



