import { unreachable, valuesEqual } from "./util.js";

export type ESExpr =
    | ESExpr.Constructor
    | boolean
    | bigint
    | string
    | ESExpr.Float16
    | ESExpr.Float16NaN
    | ESExpr.Float32
    | ESExpr.Float32NaN
    | number
    | ESExpr.Float64NaN
    | Uint8Array
    | Uint16Array
    | Uint32Array
    | BigUint64Array
    | ESExpr.Array128
    | null
    | ESExpr.NestedNull
;


export namespace ESExpr {
    export function tagOf(e: ESExpr): ESExprTag {
        switch(typeof e) {
            case "boolean":
                return Boolean;

            case "bigint":
                return BigInt;

            case "string":
                return String;

            case "number":
                return Number;

            case "object":
                if(e === null) {
                    return null;
                }

                if(e instanceof Uint8Array) {
                    return Uint8Array;
                }
                if(e instanceof Uint16Array) {
                    return Uint16Array;
                }
                if(e instanceof Uint32Array) {
                    return Uint32Array;
                }
                if(e instanceof BigUint64Array) {
                    return BigUint64Array;
                }

                switch(e.type) {
                    case "constructor":
                        return e.name;

                    case "float16":
                    case "float16-nan":
                        return Float16Symbol;

                    case "float32":
                    case "float32-nan":
                        return Float32Symbol;

                    case "float64-nan":
                        return Number;

                    case "array128":
                        return Array128Symbol;

                    case "null":
                        return null;
                }
        }
    }

    export function isConstructor(e: ESExpr): e is ESExpr.Constructor {
        return typeof e === "object" && e !== null && "type" in e && e.type === "constructor";
    }

    export function isFloat16(e: ESExpr): e is ESExpr.Float16 {
        return typeof e === "object" && e !== null && "type" in e && e.type === "float16";
    }

    export function isFloat16NaN(e: ESExpr): e is ESExpr.Float16NaN {
        return typeof e === "object" && e !== null && "type" in e && e.type === "float16-nan";
    }

    export function isFloat32(e: ESExpr): e is ESExpr.Float32 {
        return typeof e === "object" && e !== null && "type" in e && e.type === "float32";
    }

    export function isFloat32NaN(e: ESExpr): e is ESExpr.Float32NaN {
        return typeof e === "object" && e !== null && "type" in e && e.type === "float32-nan";
    }

    export function isFloat64NaN(e: ESExpr): e is ESExpr.Float64NaN {
        return typeof e === "object" && e !== null && "type" in e && e.type === "float64-nan";
    }

    export function isNestedNull(e: ESExpr): e is ESExpr.NestedNull {
        return typeof e === "object" && e !== null && "type" in e && e.type === "null";
    }

    export interface Constructor {
        readonly type: "constructor";
        readonly name: string;
        readonly args: readonly ESExpr[];
        readonly kwargs: ReadonlyMap<string, ESExpr>;
    }

    export interface Float16 {
        readonly type: "float16";
        readonly value: number;
    }

    export interface Float16NaN {
        readonly type: "float16-nan";
        readonly bits: number;
    }

    export interface Float32 {
        readonly type: "float32";
        readonly value: number;
    }

    export interface Float32NaN {
        readonly type: "float32-nan";
        readonly bits: number;
    }

    export interface Float64NaN {
        readonly type: "float64-nan";
        readonly bits: bigint;
    }

    export interface NestedNull {
        readonly type: "null";
        readonly level: bigint;
    }

    export interface Array128 {
        readonly type: "array128";
        readonly value: Uint8Array;
    }

    export const codec: ESExprCodec<ESExpr> = {
        get tags() {
            return ESExprTagSet.All;
        },

        isEncodedEqual(a, b) {
            if(typeof a !== "object") {
                return typeof b !== "object" && Object.is(a, b);
            }
            
            if(typeof b !== "object") {
                return false;
            }

            if(a === null) {
                return b === null;
            }
            if(b === null) {
                return false;
            }

            if(a instanceof Uint8Array) {
                if(b instanceof Uint8Array) {
                    return array8Codec.isEncodedEqual(a, b);
                }
                else {
                    return false;
                }
            }
            if(b instanceof Uint8Array) {
                return false;
            }

            if(a instanceof Uint16Array) {
                if(b instanceof Uint16Array) {
                    return array16Codec.isEncodedEqual(a, b);
                }
                else {
                    return false;
                }
            }
            if(b instanceof Uint16Array) {
                return false;
            }

            if(a instanceof Uint32Array) {
                if(b instanceof Uint32Array) {
                    return array32Codec.isEncodedEqual(a, b);
                }
                else {
                    return false;
                }
            }
            if(b instanceof Uint32Array) {
                return false;
            }

            if(a instanceof BigUint64Array) {
                if(b instanceof BigUint64Array) {
                    return array64Codec.isEncodedEqual(a, b);
                }
                else {
                    return false;
                }
            }
            if(b instanceof BigUint64Array) {
                return false;
            }

            switch(a.type) {
                case "constructor":
                {
                    if(b.type !== "constructor") {
                        return false;
                    }

                    if(a.name !== b.name) {
                        return false;
                    }

                    if(!listCodec(this).isEncodedEqual(a.args, b.args)) {
                        return false;
                    }

                    if(!mapMappedValueCodec(this).isEncodedEqual(a.kwargs, b.kwargs)) {
                        return false;
                    }

                    return true;
                }
                
                case "float16":
                    return b.type === "float16" && Object.is(a.value, b.value);
                
                case "float16-nan":
                    return b.type === "float16-nan" && a.bits === b.bits;

                case "float32":
                    return b.type === "float32" && Object.is(a.value, b.value);
                
                case "float32-nan":
                    return b.type === "float32-nan" && a.bits === b.bits;
                
                case "float64-nan":
                    return b.type === "float64-nan" && a.bits === b.bits;

                case "null":
                    return b.type === "null" && a.level === b.level;

                case "array128":
                    return b.type === "array128" && array8Codec.isEncodedEqual(a.value, b.value);

                default:
                    unreachable(a, "Unexpected ESExpr value");
            }
        },

        encode(value: ESExpr): ESExpr {
            return value;
        },

        decode(expr: ESExpr): DecodeResult<ESExpr> {
            return { success: true, value: expr };
        }
    };
}

export const Float16Symbol: unique symbol = Symbol.for("esexpr-float16");
export const Float32Symbol: unique symbol = Symbol.for("esexpr-float32");
export const Array128Symbol: unique symbol = Symbol.for("esexpr-array128");

export type ESExprTag =
    | string // constructor name
    | typeof Boolean
    | typeof BigInt
    | typeof String
    | typeof Float16Symbol
    | typeof Float32Symbol
    | typeof Number
    | typeof Uint8Array
    | typeof Uint16Array
    | typeof Uint32Array
    | typeof BigUint64Array
    | typeof Array128Symbol
    | null
;

export namespace ESExprTag {
    export function show(tag: ESExprTag): string {
        if(typeof tag === "string") {
            return "constructor " + tag;
        }
        else if(tag === Boolean) {
            return "bool";
        }
        else if(tag === BigInt) {
            return "int";
        }
        else if(tag === String) {
            return "string";
        }
        else if(tag === Float16Symbol) {
            return "float16";
        }
        else if(tag === Float32Symbol) {
            return "float32";
        }
        else if(tag === Number) {
            return "float64";
        }
        else if(tag === Uint8Array) {
            return "array8";
        }
        else if(tag === Uint16Array) {
            return "array16";
        }
        else if(tag === Uint32Array) {
            return "array32";
        }
        else if(tag === BigUint64Array) {
            return "array64";
        }
        else if(tag === Array128Symbol) {
            return "array128";
        }
        else if(tag === null) {
            return "null";
        }
        else {
            throw new Error("Invalid tag");
        }
    }
}

export type ESExprTagSet =
    | ReadonlySet<ESExprTag>
    | "all-tags"
;

export namespace ESExprTagSet {
    export const All: ESExprTagSet = "all-tags";

    export function isEmpty(a: ESExprTagSet): boolean {
        return a instanceof Set && a.size === 0;
    }

    export function has(a: ESExprTagSet, b: ESExprTag): boolean {
        if(a === "all-tags") {
            return true;
        }

        return a.has(b);
    }

    export function disjoint(a: ESExprTagSet, b: ESExprTagSet) {
        if(a === "all-tags") {
            return isEmpty(b);
        }

        if(b === "all-tags") {
            return a.size === 0;
        }

        return a.isDisjointFrom(b);
    }

    export function union(a: ESExprTagSet, b: ESExprTagSet): ESExprTagSet {
        if(a === "all-tags") {
            return a;
        }
        if(b === "all-tags") {
            return b;
        }

        return a.union(b);
    }

    export function show(a: ESExprTagSet): string {
        if(a === "all-tags") {
            return "all-tags";
        }
        else {
            return "[ " + Array.from(a).map(ESExprTag.show).join(", ") + " ]";
        }
    }
}

export type DecodeErrorPath =
    | { readonly type: "current" }
    | { readonly type: "constructor"; readonly constructor: string; }
    | { readonly type: "positional"; readonly constructor: string; readonly index: number; readonly next: DecodeErrorPath; }
    | { readonly type: "keyword"; readonly constructor: string; readonly keyword: string; readonly next: DecodeErrorPath; }
;

export type DecodeResult<T> =
    | { readonly success: true; readonly value: T; }
    | { readonly success: false; readonly message: string; readonly path: DecodeErrorPath; }
;

export interface ESExprCodec<T> {
    readonly tags: ESExprTagSet;
    isEncodedEqual(a: T, b: T): boolean;
    encode(value: T): ESExpr;
    decode(expr: ESExpr): DecodeResult<T>;
}

export interface FieldDecodeState {
    readonly constructor: string;
    positionalIndex: number;
    readonly args: ESExpr[];
    readonly kwargs: Map<string, ESExpr>;
}

export interface RecordCodecValidationState {
    previousOptionalPositionalTags: ESExprTagSet;
    readonly keywords: Set<string>;
    hasDict: boolean;
}

export interface ESExprFieldCodec<T> {
    readonly tags: ESExprTagSet;
    validate(state: RecordCodecValidationState, fieldName: string): void;
    isEncodedEqual(a: T, b: T): boolean;
    encode(value: T, args: ESExpr[], kwargs: Map<string, ESExpr>): void;
    decode(state: FieldDecodeState): DecodeResult<T>;
}

export interface ESExprCaseCodec<Name extends string, T extends { readonly $type: Name }> {
    readonly tags: ESExprTagSet;
    isEncodedEqual(a: T, b: T): boolean;
    encode(value: T): ESExpr;
    decode(caseName: Name, expr: ESExpr): DecodeResult<T>;
}



export const boolCodec: ESExprCodec<boolean> = {
    get tags(): ESExprTagSet {
        return new Set([Boolean]);
    },

    isEncodedEqual(a, b) {
        return a === b;
    },

    encode(value: boolean): ESExpr {
        return value;
    },

    decode(expr: ESExpr): DecodeResult<boolean> {
        if(typeof expr === "boolean") {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected a boolean",
                path: { type: "current" },
            };
        }
    },
};


export const intCodec: ESExprCodec<bigint> = {
    get tags(): ESExprTagSet {
        return new Set([BigInt]);
    },

    isEncodedEqual(a, b) {
        return a === b;
    },

    encode(value: bigint): ESExpr {
        return value;
    },

    decode(expr: ESExpr): DecodeResult<bigint> {
        if(typeof expr === "bigint") {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected a bigint",
                path: { type: "current" },
            };
        }
    },
};

class SmallIntCodec implements ESExprCodec<number> {
    constructor(min: number, max: number) {
        this.#min = min;
        this.#max = max;
    }

    readonly #min: number;
    readonly #max: number;

    get tags(): ESExprTagSet {
        return new Set([BigInt]);
    }

    isEncodedEqual(a: number, b: number): boolean {
        return a === b;
    }

    encode(value: number): ESExpr {
        return BigInt(value);
    }

    decode(expr: ESExpr): DecodeResult<number> {
        const n = intCodec.decode(expr);
        if(!n.success) {
            return n;
        }

        if(n.value < this.#min || n.value > this.#max) {
            return {
                success: false,
                message: "Integer value is out of range",
                path: { type: "current" },
            };
        }

        return {
            success: true,
            value: Number(n.value),
        };
    }
}

class BigIntCodec implements ESExprCodec<bigint> {
    constructor(min: bigint, max: bigint) {
        this.#min = min;
        this.#max = max;
    }

    readonly #min: bigint;
    readonly #max: bigint;

    get tags(): ESExprTagSet {
        return new Set([BigInt]);
    }

    isEncodedEqual(a: bigint, b: bigint): boolean {
        return a === b;
    }

    encode(value: bigint): ESExpr {
        return value;
    }

    decode(expr: ESExpr): DecodeResult<bigint> {
        const n = intCodec.decode(expr);
        if(!n.success) {
            return n;
        }

        if(n.value < this.#min || n.value > this.#max) {
            return {
                success: false,
                message: "Integer value is out of range",
                path: { type: "current" },
            };
        }

        return {
            success: true,
            value: n.value,
        };
    }
}

export const signedInt8Codec: ESExprCodec<number> = new SmallIntCodec(-0x80, 0x7F);
export const unsignedInt8Codec: ESExprCodec<number> = new SmallIntCodec(0, 0xFF);
export const signedInt16Codec: ESExprCodec<number> = new SmallIntCodec(-0x8000, 0x7FFF);
export const unsignedInt16Codec: ESExprCodec<number> = new SmallIntCodec(0, 0xFFFF);
export const signedInt32Codec: ESExprCodec<number> = new SmallIntCodec(-0x80000000, 0x7FFFFFFF);
export const unsignedInt32Codec: ESExprCodec<number> = new SmallIntCodec(0, 0xFFFFFFFF);
export const signedInt64Codec: ESExprCodec<bigint> = new BigIntCodec(-0x8000000000000000n, 0x7FFFFFFFFFFFFFFFn);
export const unsignedInt64Codec: ESExprCodec<bigint> = new BigIntCodec(0n, 0xFFFFFFFFFFFFFFFFn);

export const strCodec: ESExprCodec<string> = {
    get tags(): ESExprTagSet {
        return new Set([String]);
    },

    isEncodedEqual(a, b) {
        return a === b;
    },

    encode: function (value: string): ESExpr {
        return value;
    },

    decode: function (expr: ESExpr): DecodeResult<string> {
        if(typeof expr === "string") {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected a string",
                path: { type: "current" },
            };
        }
    },
};

export const array8Codec: ESExprCodec<Uint8Array> = {
    get tags(): ESExprTagSet {
        return new Set([Uint8Array]);
    },

    isEncodedEqual(a, b) {
        if(a.length !== b.length) {
            return false;
        }

        for(let i = 0; i < a.length; ++i) {
            if(a[i] !== b[i]) {
                return false;
            }
        }

        return true;
    },

    encode: function (value: Uint8Array): ESExpr {
        return value;
    },

    decode: function (expr: ESExpr): DecodeResult<Uint8Array> {
        if(expr instanceof Uint8Array) {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected an array8 value",
                path: { type: "current" },
            };
        }
    },
};

export const array16Codec: ESExprCodec<Uint16Array> = {
    get tags(): ESExprTagSet {
        return new Set([Uint16Array]);
    },

    isEncodedEqual(a, b) {
        if(a.length !== b.length) {
            return false;
        }

        for(let i = 0; i < a.length; ++i) {
            if(a[i] !== b[i]) {
                return false;
            }
        }

        return true;
    },

    encode: function (value: Uint16Array): ESExpr {
        return value;
    },

    decode: function (expr: ESExpr): DecodeResult<Uint16Array> {
        if(expr instanceof Uint16Array) {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected an array16 value",
                path: { type: "current" },
            };
        }
    },
};

export const array32Codec: ESExprCodec<Uint32Array> = {
    get tags(): ESExprTagSet {
        return new Set([Uint16Array]);
    },

    isEncodedEqual(a, b) {
        if(a.length !== b.length) {
            return false;
        }

        for(let i = 0; i < a.length; ++i) {
            if(a[i] !== b[i]) {
                return false;
            }
        }

        return true;
    },

    encode: function (value: Uint32Array): ESExpr {
        return value;
    },

    decode: function (expr: ESExpr): DecodeResult<Uint32Array> {
        if(expr instanceof Uint32Array) {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected an array32 value",
                path: { type: "current" },
            };
        }
    },
};

export const array64Codec: ESExprCodec<BigUint64Array> = {
    get tags(): ESExprTagSet {
        return new Set([BigUint64Array]);
    },

    isEncodedEqual(a, b) {
        if(a.length !== b.length) {
            return false;
        }

        for(let i = 0; i < a.length; ++i) {
            if(a[i] !== b[i]) {
                return false;
            }
        }

        return true;
    },

    encode: function (value: BigUint64Array): ESExpr {
        return value;
    },

    decode: function (expr: ESExpr): DecodeResult<BigUint64Array> {
        if(expr instanceof BigUint64Array) {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected an array64 value",
                path: { type: "current" },
            };
        }
    },
};

export const float16Codec: ESExprCodec<number> = {
    get tags(): ESExprTagSet {
        return new Set([Float16Symbol]);
    },

    isEncodedEqual(a, b) {
        return Object.is(a, b);
    },

    encode: function (value: number): ESExpr {
        return { type: "float16", value };
    },

    decode: function (expr: ESExpr): DecodeResult<number> {
        if(ESExpr.isFloat16(expr)) {
            return { success: true, value: expr.value };
        }
        else if(ESExpr.isFloat16NaN(expr)) {
            return { success: true, value: Number.NaN };
        }
        else {
            return {
                success: false,
                message: "Expected a float16",
                path: { type: "current" },
            };
        }
    },
};

export const float16NaNCodec: ESExprCodec<number | ESExpr.Float16NaN> = {
    get tags(): ESExprTagSet {
        return new Set([Float16Symbol]);
    },

    isEncodedEqual(a, b) {
        if(typeof a === "number") {
            return Object.is(a, b);
        }
        else {
            return ESExpr.isFloat16NaN(b) && a.bits === b.bits;
        }
    },

    encode: function (value: number | ESExpr.Float16NaN): ESExpr {
        if(typeof value === "number") {
            return { type: "float16", value };
        }
        else {
            return value;
        }
    },

    decode: function (expr: ESExpr): DecodeResult<number | ESExpr.Float16NaN> {
        if(ESExpr.isFloat16(expr)) {
            return { success: true, value: expr.value };
        }
        else if(ESExpr.isFloat16NaN(expr)) {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected a float16",
                path: { type: "current" },
            };
        }
    },
};

export const float32Codec: ESExprCodec<number> = {
    get tags(): ESExprTagSet {
        return new Set([Float32Symbol]);
    },

    isEncodedEqual(a, b) {
        return Object.is(a, b);
    },

    encode: function (value: number): ESExpr {
        return { type: "float32", value };
    },

    decode: function (expr: ESExpr): DecodeResult<number> {
        if(ESExpr.isFloat32(expr)) {
            return { success: true, value: expr.value };
        }
        else if(ESExpr.isFloat32NaN(expr)) {
            return { success: true, value: Number.NaN };
        }
        else {
            return {
                success: false,
                message: "Expected a float32",
                path: { type: "current" },
            };
        }
    },
};

export const float32NaNCodec: ESExprCodec<number | ESExpr.Float32NaN> = {
    get tags(): ESExprTagSet {
        return new Set([Float32Symbol]);
    },

    isEncodedEqual(a, b) {
        if(typeof a === "number") {
            return Object.is(a, b);
        }
        else {
            return ESExpr.isFloat32NaN(b) && a.bits === b.bits;
        }
    },

    encode: function (value: number | ESExpr.Float32NaN): ESExpr {
        if(typeof value === "number") {
            return { type: "float32", value };
        }
        else {
            return value;
        }
    },

    decode: function (expr: ESExpr): DecodeResult<number | ESExpr.Float32NaN> {
        if(ESExpr.isFloat32(expr)) {
            return { success: true, value: expr.value };
        }
        else if(ESExpr.isFloat32NaN(expr)) {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected a float32",
                path: { type: "current" },
            };
        }
    },
};

export const float64Codec: ESExprCodec<number> = {
    get tags(): ESExprTagSet {
        return new Set([Number]);
    },

    isEncodedEqual(a, b) {
        return Object.is(a, b);
    },

    encode: function (value: number): ESExpr {
        return value;
    },

    decode: function (expr: ESExpr): DecodeResult<number> {
        if(typeof expr === "number") {
            return { success: true, value: expr };
        }
        else if(ESExpr.isFloat64NaN(expr)) {
            return { success: true, value: Number.NaN };
        }
        else {
            return {
                success: false,
                message: "Expected a float64",
                path: { type: "current" },
            };
        }
    },
};

export const float64NaNCodec: ESExprCodec<number | ESExpr.Float64NaN> = {
    get tags(): ESExprTagSet {
        return new Set([Float16Symbol]);
    },

    isEncodedEqual(a, b) {
        if(typeof a === "number") {
            return Object.is(a, b);
        }
        else {
            return ESExpr.isFloat64NaN(b) && a.bits === b.bits;
        }
    },

    encode: function (value: number | ESExpr.Float64NaN): ESExpr {
        return value;
    },

    decode: function (expr: ESExpr): DecodeResult<number | ESExpr.Float64NaN> {
        if(typeof expr === "number" || ESExpr.isFloat64NaN(expr)) {
            return { success: true, value: expr };
        }
        else {
            return {
                success: false,
                message: "Expected a float16",
                path: { type: "current" },
            };
        }
    },
};

class ListCodec<T> implements ESExprCodec<readonly T[]> {
    constructor(itemCodec: ESExprCodec<T>) {
        this.#itemCodec = itemCodec;
    }

    readonly #itemCodec: ESExprCodec<T>;

    get tags(): ESExprTagSet {
        return new Set(["list"]);
    }

    isEncodedEqual(a: readonly T[], b: readonly T[]): boolean {
        if(a.length !== b.length) {
            return false;
        }

        for(let i = 0; i < a.length; ++i) {
            if(!this.#itemCodec.isEncodedEqual(a[i]!, b[i]!)) {
                return false;
            }
        }

        return true;
    }

    encode(value: readonly T[]): ESExpr {
        return {
            type: "constructor",
            name: "list",
            args: value.map(t => this.#itemCodec.encode(t)),
            kwargs: new Map(),
        };
    }

    decode(expr: ESExpr): DecodeResult<readonly T[]> {
        if(ESExpr.isConstructor(expr) && expr.name === "list") {
            if(expr.kwargs.size > 0) {
                return {
                    success: false,
                    message: "List must not have keyword arguments",
                    path: { type: "current" },
                };
            }

            const items: T[] = [];
            let i = 0;
            for(const t of expr.args) {
                const res = this.#itemCodec.decode(t);
                if(!res.success) {
                    return {
                        success: false,
                        message: res.message,
                        path: { type: "positional", constructor: "list", index: i, next: res.path }
                    };
                }
                items.push(res.value);
                ++i;
            }

            return { success: true, value: items };
        }
        else {
            return {
                success: false,
                message: "Expected a list constructor",
                path: { type: "current" },
            };
        }
    }   
}

export function listCodec<T>(itemCodec: ESExprCodec<T>): ESExprCodec<readonly T[]> {
    return new ListCodec(itemCodec);
}

export const wrappedNullLevelSymbol: unique symbol = Symbol.for("esexpr-wrapped-null-level");

export interface WrappedNull {
    readonly [wrappedNullLevelSymbol]: number;
}

const wrappedNullMemo: WrappedNull[] = [];
function getWrappedNull(level: number): WrappedNull {
    let wn = wrappedNullMemo[level];
    if(wn === undefined) {
       wn = { [wrappedNullLevelSymbol]: level };
       wrappedNullMemo[level] = wn;
    }
    return wn;
}

export type Option<A> = Option.Some<A> | null;

export namespace Option {
    export type Some<A> = (A & ({} | undefined)) | (A extends null ? WrappedNull : never);

    export function some<A>(value: A): Some<A> {
        if(value === null) {
            return getWrappedNull(1) as Some<A>;
        }
        else if(typeof value === "object" && wrappedNullLevelSymbol in value) {
            const wrappedNull = value as WrappedNull;
            return getWrappedNull(wrappedNull[wrappedNullLevelSymbol] + 1) as Some<A>;
        }
        else {
            return value;
        }
    }

    export function get<A>(value: Some<A>): A {
        if(typeof value === "object" && wrappedNullLevelSymbol in value) {
            const wrappedNull = value as WrappedNull;
            if(wrappedNull[wrappedNullLevelSymbol] > 1) {
                return getWrappedNull(wrappedNull[wrappedNullLevelSymbol] - 1) as A;
            }
            else {
                return null as A;
            }
        }
        else {
            return value;
        }
    }
}


class OptionCodec<T> implements ESExprCodec<Option<T>> {
    constructor(itemCodec: ESExprCodec<T>) {
        this.#itemCodec = itemCodec;
    }

    readonly #itemCodec: ESExprCodec<T>;

    get tags(): ESExprTagSet {
        const itemTags = this.#itemCodec.tags;
        if(!(itemTags instanceof Set)) {
            return itemTags;
        }

        const tags = new Set<ESExprTag>();
        tags.add(null);
        for(const tag of itemTags) {
            tags.add(tag);
        }
        return tags;
    }

    isEncodedEqual(a: Option<T>, b: Option<T>): boolean {
        if(a === null) {
            return b === null;
        }
        
        if(b === null) {
            return false;
        }

        return this.#itemCodec.isEncodedEqual(Option.get(a), Option.get(b));
    }

    encode(value: Option<T>): ESExpr {
        if(value === null) {
            return null;
        }
        else {
            const result = this.#itemCodec.encode(Option.get(value));
            if(result === null) {
                return { type: "null", level: 1n };
            }
            else if(ESExpr.isNestedNull(result)) {
                return { type: "null", level: result.level + 1n };
            }
            else {
                return result;
            }
        }
    }

    decode(expr: ESExpr): DecodeResult<Option<T>> {
        if(expr === null) {
            return { success: true, value: null };
        }
        else {
            let expr2: ESExpr;
            if(ESExpr.isNestedNull(expr)) {
                if(expr.level > 0n) {
                    expr2 = null;
                }
                else {
                    expr2 = { type: "null", level: expr.level - 1n };
                }
            }
            else {
                expr2 = expr;
            }

            const value = this.#itemCodec.decode(expr2);
            if(!value.success) {
                return value;
            }

            return { success: true, value: Option.some(value.value) };
        }
    }
    
}

export function optionCodec<T>(itemCodec: ESExprCodec<T>): ESExprCodec<Option<T>> {
    return new OptionCodec(itemCodec);
};





export type RecordFieldCodecs<T> = {
    readonly [Key in keyof T]-?: ESExprFieldCodec<T[Key]>;
};

function checkRecordValueEqual<T>(fields: RecordFieldCodecs<T>, a: T, b: T): boolean {
    for(const field of Object.keys(fields) as (keyof T)[]) {
        if(!fields[field].isEncodedEqual(a[field], b[field])) {
            return false;
        }
    }

    return true;
}

class RecordCodec<T> implements ESExprCodec<T> {
    constructor(constructorName: string, fields: RecordFieldCodecs<T>) {
        this.#constructorName = constructorName;
        this.#fields = fields;

        const state: RecordCodecValidationState = {
            previousOptionalPositionalTags: new Set(),
            keywords: new Set(),
            hasDict: false,
        };

        for(const field of Object.keys(this.#fields) as (keyof T & string)[]) {
            this.#fields[field].validate(state, field);
        }
    }

    readonly #constructorName: string;
    readonly #fields: RecordFieldCodecs<T>;

    
    get tags(): ESExprTagSet {
        return new Set([this.#constructorName]);
    }

    isEncodedEqual(a: T, b: T): boolean {
        return checkRecordValueEqual(this.#fields, a, b);
    }

    encode(value: T): ESExpr {
        let args: ESExpr[] = [];
        let kwargs = new Map<string, ESExpr>();
        for(const field of Object.keys(this.#fields) as (keyof T)[]) {
            this.#fields[field].encode(value[field], args, kwargs);
        }

        return {
            type: "constructor",
            name: this.#constructorName,
            args,
            kwargs,
        };
    }

    decode(expr: ESExpr): DecodeResult<T> {
        if(!(typeof expr === "object" && expr !== null && "type" in expr && expr.type == "constructor") || expr.name !== this.#constructorName) {
            return {
                success: false,
                message: `Expected a constructor of name ${this.#constructorName}`,
                path: { type: "current" },
            };
        }

        let obj: any = {};
        let args = [...expr.args];
        let kwargs = new Map<string, ESExpr>(expr.kwargs);

        const state: FieldDecodeState = {
            constructor: expr.name,
            positionalIndex: 0,
            args,
            kwargs,
        };

        for(const field of Object.keys(this.#fields) as (keyof T)[]) {
            const result = this.#fields[field].decode(state);
            if(!result.success) {
                return result;
            }
            obj[field] = result.value;
        }

        if(state.args.length > 0) {
            return {
                success: false,
                message: "Additional positional arguments",
                path: {
                    type: "positional",
                    constructor: this.#constructorName,
                    index: state.positionalIndex,
                    next: { type: "current"  },
                },
            };  
        }

        let firstRemainingKeyword: string | undefined = undefined;
        for(const kw of state.kwargs.keys()) {
            firstRemainingKeyword = kw;
            break;
        }

        if(firstRemainingKeyword !== undefined) {
            return {
                success: false,
                message: "Additional keyword arguments",
                path: {
                    type: "keyword",
                    constructor: this.#constructorName,
                    keyword: firstRemainingKeyword,
                    next: { type: "current"  },
                },
            };
        }

        return {
            success: true,
            value: obj,
        };
    }

}


export function recordCodec<T>(constructorName: string, fields: RecordFieldCodecs<T>): ESExprCodec<T> {
    return new RecordCodec(constructorName, fields);
}

export type EnumCaseCodecs<T extends { readonly $type: string }> = {
    readonly [Key in T["$type"]]: ESExprCaseCodec<Key, T & { readonly $type: Key; }>;
};

class EnumCodec<T extends { readonly $type: string }> implements ESExprCodec<T> {
    constructor(cases: EnumCaseCodecs<T>) {
        this.#cases = cases;

        this.#tags = (() => {
            let tags: ESExprTagSet = new Set();
            for(const c of Object.keys(this.#cases) as T["$type"][]) {
                const caseTags = this.#cases[c].tags;
                
                if(!ESExprTagSet.disjoint(tags, caseTags)) {
                    throw new Error(`Overlapping tags: ${ESExprTagSet.show(tags)} and ${ESExprTagSet.show(caseTags)}`);
                }

                tags = ESExprTagSet.union(tags, caseTags);
            }
            return tags;
        })();
    }

    readonly #cases: EnumCaseCodecs<T>;
    readonly #tags: ESExprTagSet;

    get tags(): ESExprTagSet {
        return this.#tags;
    }

    isEncodedEqual(a: T, b: T): boolean {
        if(a.$type !== b.$type) {
            return false;
        }

        const t: T["$type"] = a.$type;
        return this.#cases[t].isEncodedEqual(a, b);
    }

    encode(value: T): ESExpr {
        const t: T["$type"] = value.$type;
        return this.#cases[t].encode(value);
    }

    decode(expr: ESExpr): DecodeResult<T> {
        const tag = ESExpr.tagOf(expr);
        for(const c of Object.keys(this.#cases) as T["$type"][]) {
            const cc = this.#cases[c];
            if(ESExprTagSet.has(cc.tags, tag)) {
                return cc.decode(c, expr) as DecodeResult<T>;
            }
        }

        return {
            success: false,
            message: "Unexpected tag",
            path: { type: "current" },
        };
    }
    
}

export function enumCodec<T extends { readonly $type: string }>(cases: EnumCaseCodecs<T>): ESExprCodec<T> {
    return new EnumCodec(cases);
}


export type SimpleEnumNames<T extends string> = {
    readonly [K in T]: string;
};

class SimpleEnumCodec<T extends string> implements ESExprCodec<T> {
    constructor(names: SimpleEnumNames<T>) {
        this.#names = names;

        const prevNames = new Set<string>();

        for(const name of Object.values(names) as string[]) {
            if(prevNames.has(name)) {
                throw new Error("Simple enum has duplicate value: " + name);
            }

            prevNames.add(name);
        }
    }

    readonly #names: SimpleEnumNames<T>;

    get tags(): ESExprTagSet {
        return new Set([String]);
    }

    isEncodedEqual(a: T, b: T): boolean {
        return a === b;
    }

    encode(value: T): ESExpr {
        return this.#names[value];
    }

    decode(expr: ESExpr): DecodeResult<T> {
        if(typeof expr === "string") {

            for(const [name, value] of Object.entries(this.#names)) {
                if(expr === value) {
                    return { success: true, value: name as T };
                }
            }

            return {
                success: false,
                message: "Invalid simple enum value",
                path: { type: "current" },
            };
        }
        else {
            return {
                success: false,
                message: "Simple enum value must be a string",
                path: { type: "current" },
            };
        }
    }

}

export function simpleEnumCodec<T extends string>(names: SimpleEnumNames<T>): ESExprCodec<T> {
    return new SimpleEnumCodec(names);
}


function validatePositionalFieldTags(state: RecordCodecValidationState, codec: { readonly tags: ESExprTagSet }, fieldName: string): void {
    if(!(state.previousOptionalPositionalTags instanceof Set)) {
        throw new Error(`Field '${fieldName}' cannot follow optional positional arguments with all tags`);
    }

    if(!ESExprTagSet.disjoint(state.previousOptionalPositionalTags, codec.tags)) {
        throw new Error(`Field '${fieldName}' must have distinct tags from immediately preceding optional positional arguments`);
    }
}


class PositionalFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: ESExprCodec<T>) {
        this.#codec = codec;
    }

    readonly #codec: ESExprCodec<T>;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    validate(state: RecordCodecValidationState, fieldName: string): void {
        validatePositionalFieldTags(state, this.#codec, fieldName);
        state.previousOptionalPositionalTags = new Set();
    }

    isEncodedEqual(a: T, b: T): boolean {
        return this.#codec.isEncodedEqual(a, b);
    }

    encode(value: T, args: ESExpr[], _kwargs: Map<string, ESExpr>): void {
        args.push(this.#codec.encode(value));
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        const expr = state.args.shift();
        if(expr === undefined) {
            return {
                success: false,
                message: "Not enough arguments",
                path: { type: "constructor", constructor: state.constructor },
            };
        }

        const result = this.#codec.decode(expr);
        if(!result.success) {
            return {
                success: false,
                message: result.message,
                path: {
                    type: "positional",
                    constructor: state.constructor,
                    index: state.positionalIndex,
                    next: result.path,
                },
            };
        }

        ++state.positionalIndex;

        return result;
    }
}

export function positionalFieldCodec<T>(codec: ESExprCodec<T>): ESExprFieldCodec<T> {
    return new PositionalFieldCodec<T>(codec);
}

class OptionalPositionalFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: OptionalValueCodec<T>) {
        this.#codec = codec;
    }

    readonly #codec: OptionalValueCodec<T>;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }


    validate(state: RecordCodecValidationState, fieldName: string): void {
        validatePositionalFieldTags(state, this.#codec, fieldName);
        state.previousOptionalPositionalTags = ESExprTagSet.union(state.previousOptionalPositionalTags, this.#codec.tags);
    }

    isEncodedEqual(a: T, b: T): boolean {
        return this.#codec.isEncodedEqual(a, b);
    }

    encode(value: T, args: ESExpr[], _kwargs: Map<string, ESExpr>): void {
        const encoded = this.#codec.encodeOptional(value);
        if(encoded !== undefined) {
            args.push(encoded);
        }
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        let expr = state.args[0];
        if(expr !== undefined) {
            if(ESExprTagSet.has(this.#codec.tags, ESExpr.tagOf(expr))) {
                state.args.shift();
            }
            else {
                expr = undefined;
            }
        }

        const result = this.#codec.decodeOptional(expr);
        if(!result.success) {
            return {
                success: false,
                message: result.message,
                path: {
                    type: "positional",
                    constructor: state.constructor,
                    index: state.positionalIndex,
                    next: result.path,
                },
            };
        }

        ++state.positionalIndex;

        return result;
    }
}

export function optionalPositionalFieldCodec<T>(codec: OptionalValueCodec<T>): ESExprFieldCodec<T> {
    return new OptionalPositionalFieldCodec(codec);
}

class DefaultPositionalFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: ESExprCodec<T>, defaultValue: () => T) {
        this.#codec = codec;
        this.#defaultValue = defaultValue;
    }

    readonly #codec: ESExprCodec<T>;
    readonly #defaultValue: () => T;


    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    isEncodedEqual(a: T, b: T): boolean {
        return this.#codec.isEncodedEqual(a, b);
    }

    validate(state: RecordCodecValidationState, fieldName: string): void {
        validatePositionalFieldTags(state, this.#codec, fieldName);
        state.previousOptionalPositionalTags = ESExprTagSet.union(state.previousOptionalPositionalTags, this.#codec.tags);
    }

    encode(value: T, args: ESExpr[], _kwargs: Map<string, ESExpr>): void {
        if(!this.#codec.isEncodedEqual(value, this.#defaultValue.call(null))) {
            args.push(this.#codec.encode(value));
        }
    }
    
    decode(state: FieldDecodeState): DecodeResult<T> {
        let expr = state.args[0];
        if(expr !== undefined) {
            if(ESExprTagSet.has(this.#codec.tags, ESExpr.tagOf(expr))) {
                state.args.shift();
            }
            else {
                expr = undefined;
            }
        }

        if(expr === undefined) {
            return {
                success: true,
                value: this.#defaultValue.call(null),
            };
        }

        return this.#codec.decode(expr);
    }
}

export function defaultPositionalFieldCodec<T>(codec: ESExprCodec<T>, defaultValue: () => T): ESExprFieldCodec<T> {
    return new DefaultPositionalFieldCodec(codec, defaultValue);
}

export interface RepeatedValuesCodec<T> {
    readonly tags: ESExprTagSet;
    isEncodedEqual(a: T, b: T): boolean;
    encodeMany(value: T, exprs: ESExpr[]): void;
    decodeMany(exprs: ESExpr[]): RepeatedDecodeResult<T>;
}

export type RepeatedDecodeResult<T> =
    | { readonly success: true; readonly value: T; }
    | { readonly success: false; readonly message: string; readonly path: DecodeErrorPath; readonly index: number; }
;

class ArrayRepeatedValuesCodec<T> implements RepeatedValuesCodec<readonly T[]> {
    constructor(codec: ESExprCodec<T>) {
        this.#codec = codec;
    }

    readonly #codec: ESExprCodec<T>;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    isEncodedEqual(a: readonly T[], b: readonly T[]): boolean {
        if(a.length !== b.length) {
            return false;
        }

        for(let i = 0; i < a.length; ++i) {
            if(!this.#codec.isEncodedEqual(a[i]!, b[i]!)) {
                return false;
            }
        }

        return true;
    }

    encodeMany(value: readonly T[], exprs: ESExpr[]): void {
        for(const v of value) {
            exprs.push(this.#codec.encode(v));
        }
    }

    decodeMany(exprs: ESExpr[]): RepeatedDecodeResult<readonly T[]> {
        let result: T[] = [];
        for(let i = 0; exprs.length > 0; ++i) {
            const expr = exprs[0];
            if(expr === undefined) {
                break;
            }

            if(!ESExprTagSet.has(this.#codec.tags, ESExpr.tagOf(expr))) {
                break;
            }

            exprs.shift();

            const item = this.#codec.decode(expr);
            if(!item.success) {
                return {
                    success: false,
                    message: item.message,
                    path: item.path,
                    index: i,
                };
            }

            result.push(item.value);
        }

        return {
            success: true,
            value: result,
        };
    }
}

export function arrayRepeatedValuesCodec<T>(codec: ESExprCodec<T>): RepeatedValuesCodec<readonly T[]> {
    return new ArrayRepeatedValuesCodec(codec);
}


class VarargFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: RepeatedValuesCodec<T>) {
        this.#codec = codec;
    }

    readonly #codec: RepeatedValuesCodec<T>;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    validate(state: RecordCodecValidationState, fieldName: string): void {
        validatePositionalFieldTags(state, this.#codec, fieldName);
        state.previousOptionalPositionalTags = ESExprTagSet.union(state.previousOptionalPositionalTags, this.#codec.tags);
    }

    isEncodedEqual(a: T, b: T): boolean {
        return this.#codec.isEncodedEqual(a, b);
    }

    encode(value: T, args: ESExpr[], _kwargs: Map<string, ESExpr>): void {
        this.#codec.encodeMany(value, args);
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        const result = this.#codec.decodeMany(state.args);

        if(result.success) {
            return result;
        }

        return {
            success: false,
            message: result.message,
            path: {
                type: "positional",
                constructor: state.constructor,
                index: state.positionalIndex + result.index,
                next: result.path,
            },
        };
    }
}


export function varargFieldCodec<T>(codec: RepeatedValuesCodec<T>): ESExprFieldCodec<T> {
    return new VarargFieldCodec(codec);
}


function validateKeywordField(state: RecordCodecValidationState, name: string) {
    if(state.hasDict) {
        throw new Error("Keyword arguments cannot be used with dict arguments");
    }

    if(state.keywords.has(name)) {
        throw new Error(`Duplicate keyword argument \"${name}\"`);
    }
}

class KeywordFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: ESExprCodec<T>, name: string) {
        this.#codec = codec;
        this.#name = name;
    }

    readonly #codec: ESExprCodec<T>;
    readonly #name: string;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    validate(state: RecordCodecValidationState, _fieldName: string): void {
        validateKeywordField(state, this.#name);
        state.keywords.add(this.#name);
    }

    isEncodedEqual(a: T, b: T): boolean {
        return this.#codec.isEncodedEqual(a, b);
    }

    encode(value: T, _args: ESExpr[], kwargs: Map<string, ESExpr>): void {
        kwargs.set(this.#name, this.#codec.encode(value));
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        const expr = state.kwargs.get(this.#name);
        if(expr === undefined) {
            return {
                success: false,
                message: "Not enough arguments",
                path: { type: "constructor", constructor: state.constructor },
            };
        }

        state.kwargs.delete(this.#name);

        const result = this.#codec.decode(expr);
        if(!result.success) {
            return {
                success: false,
                message: result.message,
                path: {
                    type: "keyword",
                    constructor: state.constructor,
                    keyword: this.#name,
                    next: result.path,
                },
            };
        }

        return result;
    }
}

export function keywordFieldCodec<T>(name: string, codec: ESExprCodec<T>): ESExprFieldCodec<T> {
    return new KeywordFieldCodec<T>(codec, name);
}


export interface OptionalValueCodec<T> {
    readonly tags: ESExprTagSet;
    isEncodedEqual(a: T, b: T): boolean;
    encodeOptional(value: T): ESExpr | undefined;
    decodeOptional(expr: ESExpr | undefined): DecodeResult<T>;
}

class UndefinedOptionalValueCodec<T> implements OptionalValueCodec<T | undefined> {
    constructor(codec: ESExprCodec<T>) {
        this.#codec = codec;
    }

    readonly #codec: ESExprCodec<T>;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    isEncodedEqual(a: T | undefined, b: T | undefined): boolean {
        if(a === undefined) {
            return b === undefined;
        }
        if(b === undefined) {
            return false;
        }
        return this.#codec.isEncodedEqual(a, b);
    }

    encodeOptional(value: T | undefined): ESExpr | undefined {
        if(value === undefined) {
            return undefined;
        }

        return this.#codec.encode(value);
    }

    decodeOptional(expr: ESExpr | undefined): DecodeResult<T | undefined> {
        if(expr === undefined) {
            return { success: true, value: undefined };
        }

        return this.#codec.decode(expr);
    }
}

export function undefinedOptionalCodec<T>(codec: ESExprCodec<T>): OptionalValueCodec<T | undefined> {
    return new UndefinedOptionalValueCodec<T>(codec);
}


class OptionalKeywordFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: OptionalValueCodec<T>, name: string) {
        this.#codec = codec;
        this.#name = name;
    }

    readonly #codec: OptionalValueCodec<T>;
    readonly #name: string;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    isEncodedEqual(a: T, b: T): boolean {
        return this.#codec.isEncodedEqual(a, b);
    }

    validate(state: RecordCodecValidationState, _fieldName: string): void {
        validateKeywordField(state, this.#name);
        state.keywords.add(this.#name);
    }

    encode(value: T, _args: ESExpr[], kwargs: Map<string, ESExpr>): void {
        const expr = this.#codec.encodeOptional(value);
        if(expr !== undefined) {
            kwargs.set(this.#name, expr);
        }
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        const expr = state.kwargs.get(this.#name);
        if(expr !== undefined) {
            state.kwargs.delete(this.#name);
            
        }

        return this.#codec.decodeOptional(expr);
    }
}

export function optionalKeywordFieldCodec<T>(name: string, codec: OptionalValueCodec<T>): ESExprFieldCodec<T> {
    return new OptionalKeywordFieldCodec(codec, name);
}




class DefaultKeywordFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: ESExprCodec<T>, name: string, defaultValue: () => T) {
        this.#codec = codec;
        this.#name = name;
        this.#defaultValue = defaultValue;
    }

    readonly #codec: ESExprCodec<T>;
    readonly #name: string;
    readonly #defaultValue: () => T;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    validate(state: RecordCodecValidationState, _fieldName: string): void {
        validateKeywordField(state, this.#name);
        state.keywords.add(this.#name);
    }

    isEncodedEqual(a: T, b: T): boolean {
        return this.#codec.isEncodedEqual(a, b);
    }

    encode(value: T, _args: ESExpr[], kwargs: Map<string, ESExpr>): void {
        if(!valuesEqual(value, this.#defaultValue.call(null))) {
            kwargs.set(this.#name, this.#codec.encode(value));
        }
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        const expr = state.kwargs.get(this.#name);
        if(expr === undefined) {
            return { success: true, value: this.#defaultValue.call(null) };
        }

        state.kwargs.delete(this.#name);

        const result = this.#codec.decode(expr);
        if(!result.success) {
            return {
                success: false,
                message: result.message,
                path: {
                    type: "keyword",
                    constructor: state.constructor,
                    keyword: this.#name,
                    next: result.path,
                },
            };
        }
        
        return result;
    }
}

export function defaultKeywordFieldCodec<T>(name: string, defaultValue: () => T, codec: ESExprCodec<T>): ESExprFieldCodec<T> {
    return new DefaultKeywordFieldCodec(codec, name, defaultValue);
}



export interface MappedValueCodec<T> {
    readonly tags: ESExprTagSet;
    isEncodedEqual(a: T, b: T): boolean;
    encodeMapped(value: T): ReadonlyMap<string, ESExpr>;
    decodeMapped(expr: ReadonlyMap<string, ESExpr>): MappedValueDecodeResult<T>;
}

export type MappedValueDecodeResult<T> =
    | { readonly success: true; readonly value: T; }
    | { readonly success: false; readonly message: string; readonly path: DecodeErrorPath; readonly key: string; }
;



class MapMappedValueCodec<T> implements MappedValueCodec<ReadonlyMap<string, T>> {
    constructor(codec: ESExprCodec<T>) {
        this.#codec = codec;
    }

    readonly #codec: ESExprCodec<T>;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    isEncodedEqual(a: ReadonlyMap<string, T>, b: ReadonlyMap<string, T>): boolean {
        if(a.size != b.size) {
            return false;
        }

        for(const [k, v1] of a) {
            if(!b.has(k)) {
                return false;
            }

            if(!this.#codec.isEncodedEqual(v1, b.get(k)!)) {
                return false;
            }
        }

        return true;
    }

    encodeMapped(value: ReadonlyMap<string, T>): ReadonlyMap<string, ESExpr> {
        const m = new Map<string, ESExpr>();
        for(const [k, v] of value) {
            m.set(k, this.#codec.encode(v));
        }
        return m;
    }

    decodeMapped(expr: ReadonlyMap<string, ESExpr>): MappedValueDecodeResult<ReadonlyMap<string, T>> {
        const m = new Map<string, T>();

        for(const [kw, item] of expr) {
            const decItem = this.#codec.decode(item);
            if(!decItem.success) {
                return {
                    success: false,
                    message: decItem.message,
                    path: decItem.path,
                    key: kw,
                };
            }

            m.set(kw, decItem.value);
        }

        return {
            success: true,
            value: m,
        };
    }
}

export function mapMappedValueCodec<T>(codec: ESExprCodec<T>): MappedValueCodec<ReadonlyMap<string, T>> {
    return new MapMappedValueCodec<T>(codec);
}

class DictFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: MappedValueCodec<T>) {
        this.#codec = codec;
    }

    readonly #codec: MappedValueCodec<T>;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    validate(state: RecordCodecValidationState, _fieldName: string): void {
        if(state.hasDict) {
            throw new Error("Only a single dict argument is allowed");
        }

        if(state.keywords.size > 0) {
            throw new Error("Keyword arguments cannot be used with dict arguments");
        }

        state.hasDict = true;
    }

    isEncodedEqual(a: T, b: T): boolean {
        return this.#codec.isEncodedEqual(a, b);
    }

    encode(value: T, _args: ESExpr[], kwargs: Map<string, ESExpr>): void {
        const encoded = this.#codec.encodeMapped(value);
        for(const [k, v] of encoded) {
            kwargs.set(k, v);
        }
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        const result = this.#codec.decodeMapped(state.kwargs);
        state.kwargs.clear();
        if(result.success) {
            return result;
        }

        return {
            success: false,
            message: result.message,
            path: {
                type: "keyword",
                constructor: state.constructor,
                keyword: result.key,
                next: result.path,
            },
        }
    }
}

export function dictFieldCodec<T>(codec: MappedValueCodec<T>): ESExprFieldCodec<T> {
    return new DictFieldCodec(codec);
}

function recombine<A, B>(value: A & Omit<B, keyof A>): B {
    return value as B;
}

class CaseCodec<Name extends string, T extends { readonly $type: Name }> implements ESExprCaseCodec<Name, T> {
    constructor(constructor_name: string, fields: RecordFieldCodecs<Omit<T, "$type">>) {
        this.#constructor_name = constructor_name;
        this.#fields = fields;
    }

    readonly #constructor_name: string;
    readonly #fields: RecordFieldCodecs<Omit<T, "$type">>;

    get tags(): ESExprTagSet {
        return new Set([this.#constructor_name]);
    }
    
    isEncodedEqual(a: T, b: T): boolean {
        return checkRecordValueEqual(this.#fields, a, b);
    }

    encode(value: T): ESExpr {
        return recordCodec(this.#constructor_name, this.#fields).encode(value);
    }

    decode(caseName: Name, expr: ESExpr): DecodeResult<T> {
        const res = recordCodec(this.#constructor_name, this.#fields).decode(expr);
        if(!res.success) {
            return res;
        }

        return {
            success: true,
            value: recombine<{ readonly $type: Name }, T>({
                $type: caseName,
                ...res.value,
            }),
        };
    }

}

export function caseCodec<Name extends string, T extends { readonly $type: Name }>(constructor_name: string, fields: RecordFieldCodecs<Omit<T, "$type">>): ESExprCaseCodec<Name, T> {
    return new CaseCodec(constructor_name, fields);
}


class InlineCaseCodec<Field extends string, Name extends string, T> implements ESExprCaseCodec<Name, { readonly $type: Name; } & { readonly [F in Field]: T; }> {
    constructor(field: Field, codec: ESExprCodec<T>) {
        this.#field = field;
        this.#codec = codec;
    }

    readonly #field: Field;
    readonly #codec: ESExprCodec<T>;

    get tags(): ESExprTagSet {
        return this.#codec.tags;
    }

    isEncodedEqual(a: { readonly $type: Name; } & { readonly [F in Field]: T; }, b: { readonly $type: Name; } & { readonly [F in Field]: T; }): boolean {
        return this.#codec.isEncodedEqual(a[this.#field], b[this.#field]);
    }
    
    encode(value: { readonly $type: Name; } & { [F in Field]: T; }): ESExpr {
        return this.#codec.encode(value[this.#field]);
    }

    decode(caseName: Name, expr: ESExpr): DecodeResult<{ readonly $type: Name; } & { [F in Field]: T; }> {
        const res = this.#codec.decode(expr);
        if(!res.success) {
            return res;
        }

        const value = {
            $type: caseName,
            [this.#field]: res.value,
        } as { readonly $type: Name; } & { [F in Field]: T; };

        return {
            success: true,
            value,
        };
    }

}

export function inlineCaseCodec<Field extends string, Name extends string, T>(field: Field, codec: ESExprCodec<T>): ESExprCaseCodec<Name, { readonly $type: Name; } & { [F in Field]: T; }> {
    return new InlineCaseCodec(field, codec)
}


class LazyCodec<A> implements ESExprCodec<A> {
    constructor(create: () => ESExprCodec<A>) {
        this.#create = create;
        this.#inner = null;
    }

    readonly #create: () => ESExprCodec<A>;
    #inner: ESExprCodec<A> | null;

    #getInner(): ESExprCodec<A> {
        if(this.#inner === null) {
            this.#inner = this.#create.call(null);
        }

        return this.#inner;
    }

    get tags(): ESExprTagSet {
        return this.#getInner().tags;
    }

    isEncodedEqual(a: A, b: A): boolean {
        return this.#getInner().isEncodedEqual(a, b);
    }

    encode(value: A): ESExpr {
        return this.#getInner().encode(value);
    }
    decode(expr: ESExpr): DecodeResult<A> {
        return this.#getInner().decode(expr);
    }
}

export function lazyCodec<A>(inner: () => ESExprCodec<A>): ESExprCodec<A> {
    return new LazyCodec(inner);
}

