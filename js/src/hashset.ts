import {hash, equals} from "./hash.js";

export class HashSet<T> {
    constructor(values?: readonly T[] | null) {
        for (let i = 0; i < HashSet.#BUCKET_COUNT; i++) {
            this.#buckets.push([]);
        }

        if (values) {
            for (const v of values) {
                const h = hash(v) >>> 0;
                const bucketIndex = (h % HashSet.#BUCKET_COUNT + HashSet.#BUCKET_COUNT) % HashSet.#BUCKET_COUNT;
                const bucket = this.#buckets[bucketIndex]!;

                let found = false;
                for (const entry of bucket) {
                    if (entry[0] === h && equals(entry[1], v)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    bucket.push([h, v]);
                    this.#size++;
                }
            }
        }
    }

    static readonly #BUCKET_COUNT = 256;
    readonly #buckets: [number, T][][] = [];
    #size = 0;

    get size(): number {
        return this.#size;
    }

    has(value: T): boolean {
        const h = hash(value) >>> 0;
        const bucketIndex = (h % HashSet.#BUCKET_COUNT + HashSet.#BUCKET_COUNT) % HashSet.#BUCKET_COUNT;
        const bucket = this.#buckets[bucketIndex]!;

        for (const [entryHash, v] of bucket) {
            if (entryHash === h && equals(v, value)) {
                return true;
            }
        }

        return false;
    }

    *values(): IterableIterator<T, undefined, undefined> {
        for (const bucket of this.#buckets) {
            for (const [_, v] of bucket) {
                yield v;
            }
        }
        return undefined;
    }


    [Symbol.iterator](): IterableIterator<T, undefined, undefined> {
        return this.values();
    }
}
