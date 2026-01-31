
import {ESExpr, Option } from "./index.js";

export interface HashEq<A> {
    readonly isPrimitive?: boolean
    hash(a: A): number;
    equals(a: A, b: A): boolean;
}

export namespace HashEq {
    export const boolean: HashEq<boolean> = {
        isPrimitive: true,
        hash(a: boolean): number {
            return a ? 1231 : 1237;
        },
        equals(a: boolean, b: boolean): boolean {
            return a === b;
        },
    };

    export const int8: HashEq<number> = {
        isPrimitive: true,
        hash(a: number): number {
            return a & 0xFF;
        },
        equals(a: number, b: number): boolean {
            return (a & 0xFF) === (b & 0xFF);
        },
    };

    export const int16: HashEq<number> = {
        isPrimitive: true,
        hash(a: number): number {
            return a & 0xFFFF;
        },
        equals(a: number, b: number): boolean {
            return (a & 0xFFFF) === (b & 0xFFFF);
        },
    };

    export const int32: HashEq<number> = {
        isPrimitive: true,
        hash(a: number): number {
            return a | 0;
        },
        equals(a: number, b: number): boolean {
            return (a | 0) === (b | 0);
        },
    };

    export const int64: HashEq<bigint> = {
        isPrimitive: true,
        hash(a: bigint): number {
            a = BigInt.asUintN(64, a);
            return Number(a & 0xFFFFFFFFn) ^ Number(a >> 32n);
        },
        equals(a: bigint, b: bigint): boolean {
            return BigInt.asUintN(64, a) === BigInt.asUintN(64, b);
        },
    };

    export const bigint: HashEq<bigint> = {
        isPrimitive: true,
        hash(a: bigint): number {
            let h = 0;
            if(a < 0n) {
                a = -a;
                h = 87;
            }
            while(a > 0n) {
                h = (Math.imul(31, h) + Number(BigInt.asUintN(32, a))) | 0;
                a >>= 32n;
            }
            return h;
        },
        equals(a: bigint, b: bigint): boolean {
            return a === b;
        },
    };

    export const string: HashEq<string> = {
        isPrimitive: true,
        hash(a: string): number {
            let h = 0;
            for (let i = 0; i < a.length; i++) {
                h = (Math.imul(31, h) + a.charCodeAt(i)) | 0;
            }
            return h;
        },
        equals(a: string, b: string): boolean {
            return a === b;
        },
    };

    export const uint8Array: HashEq<Uint8Array> = {
        hash(a: Uint8Array): number {
            let h = 0;
            for (let i = 0; i < a.length; i++) {
                h = (Math.imul(31, h) + a[i]!) | 0;
            }
            return h;
        },
        equals(a: Uint8Array, b: Uint8Array): boolean {
            if (a.length !== b.length) return false;
            for (let i = 0; i < a.length; i++) {
                if (a[i] !== b[i]) return false;
            }
            return true;
        },
    };

    export const uint16Array: HashEq<Uint16Array> = {
        hash(a: Uint16Array): number {
            let h = 0;
            for (let i = 0; i < a.length; i++) {
                h = (Math.imul(31, h) + a[i]!) | 0;
            }
            return h;
        },
        equals(a: Uint16Array, b: Uint16Array): boolean {
            if (a.length !== b.length) return false;
            for (let i = 0; i < a.length; i++) {
                if (a[i] !== b[i]) return false;
            }
            return true;
        },
    };

    export const uint32Array: HashEq<Uint32Array> = {
        hash(a: Uint32Array): number {
            let h = 0;
            for (let i = 0; i < a.length; i++) {
                h = (Math.imul(31, h) + a[i]!) | 0;
            }
            return h;
        },
        equals(a: Uint32Array, b: Uint32Array): boolean {
            if (a.length !== b.length) return false;
            for (let i = 0; i < a.length; i++) {
                if (a[i] !== b[i]) return false;
            }
            return true;
        },
    };

    export const bigUint64Array: HashEq<BigUint64Array> = {
        hash(a: BigUint64Array): number {
            let h = 0;
            for (let i = 0; i < a.length; i++) {
                const val = a[i]!;
                const low = Number(val & 0xffffffffn);
                const high = Number(val >> 32n);
                h = (Math.imul(31, h) + low) | 0;
                h = (Math.imul(31, h) + high) | 0;
            }
            return h;
        },
        equals(a: BigUint64Array, b: BigUint64Array): boolean {
            if (a.length !== b.length) return false;
            for (let i = 0; i < a.length; i++) {
                if (a[i] !== b[i]) return false;
            }
            return true;
        },
    };

    export function list<T>(itemHashEq: HashEq<T>): HashEq<readonly T[]> {
        return {
            hash(a: readonly T[]): number {
                let h = 0;
                for(const item of a) {
                    h = (Math.imul(31, h) + itemHashEq.hash(item)) | 0;
                }
                return h;
            },
            equals(a: readonly T[], b: readonly T[]): boolean {
                if(a.length !== b.length) return false;
                for (let i = 0; i < a.length; i++) {
                    if(!itemHashEq.equals(a[i]!, b[i]!)) return false;
                }
                return true;
            },
        };
    }

    export function option<T>(itemHashEq: HashEq<T>): HashEq<Option<T>> {
        return {
            hash(a: Option<T>): number {
                if(a === null) return 0;
                
                return (itemHashEq.hash(Option.get(a)) + 1) | 0;
            },
            equals(a: Option<T>, b: Option<T>): boolean {
                if(a === null) return b === null;
                if(b === null) return false;
                return itemHashEq.equals(Option.get(a), Option.get(b));
            },
        };
    }

}
