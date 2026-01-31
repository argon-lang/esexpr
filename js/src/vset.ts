import type {HashEq} from "./hash.js";

export interface VSet<T> {
    readonly hash: HashEq<T>;
    readonly size: number;
    has(value: T): boolean;

    values(): IterableIterator<T, undefined, undefined>;
    [Symbol.iterator](): IterableIterator<T, undefined, undefined>;
}

export namespace VSet {
    export function create<T>(hash: HashEq<T>, values?: readonly T[] | null): VSet<T> {
        if(hash.isPrimitive) {
            return new PrimitiveVSet(hash, new Set(values));
        }
        else {
            return new HashSet(hash, values);
        }
    }
}

class HashSet<T> implements VSet<T> {
    constructor(readonly hash: HashEq<T>, values?: readonly T[] | null) {
        for (let i = 0; i < HashSet.#BUCKET_COUNT; i++) {
            this.#buckets.push([]);
        }

        if (values) {
            for (const v of values) {
                const h = hash.hash(v) >>> 0;
                const bucketIndex = (h % HashSet.#BUCKET_COUNT + HashSet.#BUCKET_COUNT) % HashSet.#BUCKET_COUNT;
                const bucket = this.#buckets[bucketIndex]!;

                let found = false;
                for (const entry of bucket) {
                    if (entry[0] === h && hash.equals(entry[1], v)) {
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
        const h = this.hash.hash(value) >>> 0;
        const bucketIndex = (h % HashSet.#BUCKET_COUNT + HashSet.#BUCKET_COUNT) % HashSet.#BUCKET_COUNT;
        const bucket = this.#buckets[bucketIndex]!;

        for (const [entryHash, v] of bucket) {
            if (entryHash === h && this.hash.equals(v, value)) {
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

class PrimitiveVSet<T> implements VSet<T> {
    constructor(readonly hash: HashEq<T>, set: ReadonlySet<T>) {
        this.#set = set;
    }

    readonly #set: ReadonlySet<T>;

    get size(): number {
        return this.#set.size;
    }

    has(value: T): boolean {
        return this.#set.has(value);
    }

    values(): IterableIterator<T, undefined, undefined> {
        return this.#set.values();
    }


    [Symbol.iterator](): IterableIterator<T, undefined, undefined> {
        return this.#set[Symbol.iterator]();
    }
}
