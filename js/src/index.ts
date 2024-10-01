import { valuesEqual } from "./util.js";

export type ESExpr =
    | ESExpr.Constructor
    | boolean
    | bigint
    | string
    | Uint8Array
    | ESExpr.Float32
    | number
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

                switch(e.type) {
                    case "constructor":
                        return e.name;

                    case "float32":
                        return Math;

                    case "null":
                        return null;
                }
        }
    }

    export function isConstructor(e: ESExpr): e is ESExpr.Constructor {
        return typeof e === "object" && e !== null && "type" in e && e.type === "constructor";
    }

    export function isFloat32(e: ESExpr): e is ESExpr.Float32 {
        return typeof e === "object" && e !== null && "type" in e && e.type === "float32";
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

    export interface Float32 {
        readonly type: "float32";
        readonly value: number;
    }

    export interface NestedNull {
        readonly type: "null";
        readonly level: bigint;
    }

    export const codec: ESExprCodec<ESExpr> = {
        tags: new Set(),

        encode(value: ESExpr): ESExpr {
            return value;
        },

        decode(expr: ESExpr): DecodeResult<ESExpr> {
            return { success: true, value: expr };
        }
    };
}

export type ESExprTag =
    | string // constructor name
    | typeof Boolean
    | typeof BigInt
    | typeof String
    | typeof Uint8Array
    | typeof Math // used for float32
    | typeof Number
    | null
;

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
    readonly tags: ReadonlySet<ESExprTag>;
    encode(value: T): ESExpr;
    decode(expr: ESExpr): DecodeResult<T>;
}

export interface FieldDecodeState {
    readonly constructor: string;
    positionalIndex: number;
    readonly args: ESExpr[];
    readonly kwargs: Map<string, ESExpr>;
}

export interface ESExprFieldCodec<T> {
    readonly tags: ReadonlySet<ESExprTag>;
    encode(value: T, args: ESExpr[], kwargs: Map<string, ESExpr>): void;
    decode(state: FieldDecodeState): DecodeResult<T>;
}

export interface ESExprCaseCodec<Name extends string, T extends { readonly $type: Name }> {
    readonly tags: ReadonlySet<ESExprTag>;
    encode(value: T): ESExpr;
    decode(caseName: Name, expr: ESExpr): DecodeResult<T>;
}



export const boolCodec: ESExprCodec<boolean> = {
    get tags(): ReadonlySet<ESExprTag> {
        return new Set([Boolean]);
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
    get tags(): ReadonlySet<ESExprTag> {
        return new Set([BigInt]);
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

    get tags(): ReadonlySet<ESExprTag> {
        return new Set([BigInt]);
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

    get tags(): ReadonlySet<ESExprTag> {
        return new Set([BigInt]);
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
    get tags(): ReadonlySet<ESExprTag> {
        return new Set([String]);
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

export const binaryCodec: ESExprCodec<Uint8Array> = {
    get tags(): ReadonlySet<ESExprTag> {
        return new Set([Uint8Array]);
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
                message: "Expected a binary value",
                path: { type: "current" },
            };
        }
    },
};

export const float32Codec: ESExprCodec<number> = {
    get tags(): ReadonlySet<ESExprTag> {
        return new Set([Math]);
    },

    encode: function (value: number): ESExpr {
        return { type: "float32", value };
    },

    decode: function (expr: ESExpr): DecodeResult<number> {
        if(ESExpr.isFloat32(expr)) {
            return { success: true, value: expr.value };
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
    get tags(): ReadonlySet<ESExprTag> {
        return new Set([Number]);
    },

    encode: function (value: number): ESExpr {
        return value;
    },

    decode: function (expr: ESExpr): DecodeResult<number> {
        if(typeof expr === "number") {
            return { success: true, value: expr };
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

class ListCodec<T> implements ESExprCodec<readonly T[]> {
    constructor(itemCodec: ESExprCodec<T>) {
        this.#itemCodec = itemCodec;
    }

    readonly #itemCodec: ESExprCodec<T>;

    get tags(): ReadonlySet<ESExprTag> {
        return new Set(["list"]);
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


export class WrappedNull {
    constructor(level: number) {
        this.#level = level;
    }

    readonly #level: number;

    get level(): number {
        return this.#level;
    }
}

const wrappedNullMemo: WrappedNull[] = [];
function getWrappedNull(level: number): WrappedNull {
    let wn = wrappedNullMemo[level];
    if(wn === undefined) {
       wn = new WrappedNull(level);
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
        else if(value instanceof WrappedNull) {
            return getWrappedNull(value.level + 1) as Some<A>;
        }
        else {
            return value;
        }
    }

    export function get<A>(value: Some<A>): A {
        if(value instanceof WrappedNull) {
            if(value.level > 1n) {
                return getWrappedNull(value.level - 1) as A;
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

    get tags(): ReadonlySet<ESExprTag> {
        const tags = new Set<ESExprTag>();
        tags.add(null);
        for(const tag of this.#itemCodec.tags) {
            tags.add(tag);
        }
        return tags;
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


class RecordCodec<T> implements ESExprCodec<T> {
    constructor(constructorName: string, fields: RecordFieldCodecs<T>) {
        this.#constructorName = constructorName;
        this.#fields = fields;
    }

    readonly #constructorName: string;
    readonly #fields: RecordFieldCodecs<T>;

    
    get tags(): ReadonlySet<ESExprTag> {
        return new Set([this.#constructorName]);
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
    }

    readonly #cases: EnumCaseCodecs<T>;

    get tags(): ReadonlySet<ESExprTag> {
        const tags = new Set<ESExprTag>();
        for(const c of Object.keys(this.#cases) as T["$type"][]) {
            for(const tag of this.#cases[c].tags) {
                tags.add(tag);
            }
        }
        return tags;
    }

    encode(value: T): ESExpr {
        const t: T["$type"] = value.$type;
        return this.#cases[t].encode(value);
    }

    decode(expr: ESExpr): DecodeResult<T> {
        const tag = ESExpr.tagOf(expr);
        for(const c of Object.keys(this.#cases) as T["$type"][]) {
            const cc = this.#cases[c];
            if(cc.tags.has(tag)) {
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
    }

    readonly #names: SimpleEnumNames<T>;

    get tags(): ReadonlySet<ESExprTag> {
        return new Set([String]);
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


class PositionalFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: ESExprCodec<T>) {
        this.#codec = codec;
    }

    readonly #codec: ESExprCodec<T>;

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
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

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
    }

    encode(value: T, args: ESExpr[], _kwargs: Map<string, ESExpr>): void {
        const encoded = this.#codec.encodeOptional(value);
        if(encoded !== undefined) {
            args.push(encoded);
        }
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        const expr = state.args.shift();

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


export interface RepeatedValuesCodec<T> {
    readonly tags: ReadonlySet<ESExprTag>;
    encodeMany(value: T): readonly ESExpr[];
    decodeMany(exprs: readonly ESExpr[]): RepeatedDecodeResult<T>;
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

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
    }

    encodeMany(value: readonly T[]): readonly ESExpr[] {
        return value.map(v => this.#codec.encode(v));
    }

    decodeMany(exprs: readonly ESExpr[]): RepeatedDecodeResult<readonly T[]> {
        let result: T[] = [];
        let i = 0;
        for(const e of exprs) {
            const item = this.#codec.decode(e);
            if(!item.success) {
                return {
                    success: false,
                    message: item.message,
                    path: item.path,
                    index: i,
                };
            }

            result.push(item.value);
            ++i;
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

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
    }

    encode(value: T, args: ESExpr[], _kwargs: Map<string, ESExpr>): void {
        const items = this.#codec.encodeMany(value);
        for(const a of items) {
            args.push(a);
        }
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        const result = this.#codec.decodeMany(state.args);
        state.args.length = 0;

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


class KeywordFieldCodec<T> implements ESExprFieldCodec<T> {
    constructor(codec: ESExprCodec<T>, name: string) {
        this.#codec = codec;
        this.#name = name;
    }

    readonly #codec: ESExprCodec<T>;
    readonly #name: string;

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
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
    readonly tags: ReadonlySet<ESExprTag>;
    encodeOptional(value: T): ESExpr | undefined;
    decodeOptional(expr: ESExpr | undefined): DecodeResult<T>;
}

class UndefinedOptionalValueCodec<T> implements OptionalValueCodec<T | undefined> {
    constructor(codec: ESExprCodec<T>) {
        this.#codec = codec;
    }

    readonly #codec: ESExprCodec<T>;

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
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

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
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

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
    }

    encode(value: T, _args: ESExpr[], kwargs: Map<string, ESExpr>): void {
        if(!valuesEqual(value, this.#defaultValue())) {
            kwargs.set(this.#name, this.#codec.encode(value));
        }
    }

    decode(state: FieldDecodeState): DecodeResult<T> {
        const expr = state.kwargs.get(this.#name);
        if(expr === undefined) {
            return { success: true, value: this.#defaultValue() };
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
    readonly tags: ReadonlySet<ESExprTag>;
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

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
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

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags;
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

    get tags(): ReadonlySet<ESExprTag> {
        return new Set([this.#constructor_name]);
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

    get tags(): ReadonlySet<ESExprTag> {
        return this.#codec.tags
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
            this.#inner = this.#create();
        }

        return this.#inner;
    }

    get tags(): ReadonlySet<ESExprTag> {
        return this.#getInner().tags;
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


