import * as esexpr from "./index.js";
import type { ESExpr, ESExprCodec } from "./index.js";

type Token =
    | { type: "constructor_start", index: number }
    | { type: "constructor_start_known", value: string }
    | { type: "constructor_end" }
    | { type: "keyword", index: number }
    | { type: "int_value", value: bigint }
    | { type: "string_value", s: string }
    | { type: "string_pool_value", index: number }
    | { type: "binary_value", value: Uint8Array }
    | { type: "float32_value", value: number }
    | { type: "float64_value", value: number }
    | { type: "boolean_value", value: boolean }
    | { type: "null_value" }
;

const TAG_VARINT_MASK = 0xE0;
const TAG_VARINT_CONSTRUCTOR_START = 0x00;
const TAG_VARINT_NON_NEG_INT = 0x20;
const TAG_VARINT_NEG_INT = 0x40;
const TAG_VARINT_STRING_LENGTH = 0x60;
const TAG_VARINT_STRING_POOL = 0x80;
const TAG_VARINT_BYTES_LENGTH = 0xA0;
const TAG_VARINT_KEYWORD = 0xC0;


const TAG_CONSTRUCTOR_END = 0xE0;
const TAG_TRUE = 0xE1;
const TAG_FALSE = 0xE2;
const TAG_NULL = 0xE3;
const TAG_FLOAT32 = 0xE4;
const TAG_FLOAT64 = 0xE5;
const TAG_CONSTRUCTOR_START_STRING_TABLE = 0xE6;
const TAG_CONSTRUCTOR_START_LIST = 0xE7;

class ByteReader {
    constructor(iter: AsyncIterator<Uint8Array>) {
        this.#iter = iter;
    }

    readonly #iter: AsyncIterator<Uint8Array>;
    #current: Uint8Array | null = null;
    #index: number = 0;

    async tryReadByte(): Promise<number | null> {
        const buff = await this.#tryLoadBuffer();
        if(buff === null) {
            return null;
        }

        const result = buff[this.#index]!;
        ++this.#index;
        return result;
    }

    async readByte(): Promise<number> {
        const buff = await this.#ensureBuffer();

        const result = buff[this.#index]!;
        ++this.#index;
        return result;
    }

    async readFixed(size: number): Promise<Uint8Array> {
        let offset = 0;
        let res = new Uint8Array(size);
        while(offset < size) {
            const buff = await this.#ensureBuffer();
            const len = Math.min(size - offset, buff.length - this.#index);
            res.set(buff.subarray(this.#index, this.#index + len));
            this.#index += len;
            offset += len;
        }
        return res;
    }

    async readString(size: number): Promise<string> {
        let s = "";
        const decoder = new TextDecoder();
        let offset = 0;
        while(offset < size) {
            const buff = await this.#ensureBuffer();
            const len = Math.min(size - offset, buff.length - this.#index);
            s += decoder.decode(buff.subarray(this.#index, this.#index + len));
            this.#index += len;
            offset += len;
        }
        return s;
    }

    async #tryLoadBuffer(): Promise<Uint8Array | null> {
        while(this.#current == null || this.#index >= this.#current.length) {
            const res = await this.#iter.next();
            if(res.done) {
                return null;
            }

            this.#current = res.value;
            this.#index = 0;
        }

        return this.#current;
    }

    async #ensureBuffer(): Promise<Uint8Array> {
        const buff = await this.#tryLoadBuffer();
        if(buff === null) {
            throw new Error("Unexpected end of file");
        }
        return buff;
    }
}

async function* getTokens(reader: ByteReader): AsyncIterable<Token> {
    for(;;) {
        const b = await reader.tryReadByte();
        if(b === null) {
            break;
        }


        if((b & TAG_VARINT_MASK) == TAG_VARINT_MASK) {
            switch(b) {
                case TAG_CONSTRUCTOR_END:
                    yield { type: "constructor_end" };
                    break;

                case TAG_TRUE:
                    yield { type: "boolean_value", value: true };
                    break;

                case TAG_FALSE:
                    yield { type: "boolean_value", value: false };
                    break;

                case TAG_NULL:
                    yield { type: "null_value" };
                    break;


                case TAG_FLOAT32:
                {
                    const buff = await reader.readFixed(4);
                    const value = new Float32Array(buff.buffer, buff.byteOffset, 1)[0]!;
                    yield { type: "float32_value", value };
                    break;
                }

                case TAG_FLOAT64:
                {
                    const buff = await reader.readFixed(8);
                    const value = new Float64Array(buff.buffer, buff.byteOffset, 1)[0]!;
                    yield { type: "float64_value", value };
                    break;
                }

                case TAG_CONSTRUCTOR_START_STRING_TABLE:
                    yield { type: "constructor_start_known", value: "string-table" };
                    break;

                case TAG_CONSTRUCTOR_START_LIST:
                    yield { type: "constructor_start_known", value: "list" };
                    break;

                default:
                    throw new Error("Invalid token byte");
            }
        }
        else {
            const n = await readInt(reader, b);
            switch(b & TAG_VARINT_MASK) {
                case TAG_VARINT_CONSTRUCTOR_START:
                    yield { type: "constructor_start", index: checkIntRange(n), };
                    break;

                case TAG_VARINT_NON_NEG_INT:
                    yield { type: "int_value", value: n };
                    break;

                case TAG_VARINT_NEG_INT:
                    yield { type: "int_value", value: -(n + 1n) };
                    break;

                case TAG_VARINT_STRING_LENGTH:
                {
                    const s = await reader.readString(checkIntRange(n));
                    yield { type: "string_value", s };
                    break;
                }

                case TAG_VARINT_STRING_POOL:
                    yield { type: "string_pool_value", index: checkIntRange(n), };
                    break;

                case TAG_VARINT_BYTES_LENGTH:
                    {
                        const value = await reader.readFixed(checkIntRange(n));
                        yield { type: "binary_value", value };
                        break;
                    }

                case TAG_VARINT_KEYWORD:
                    yield { type: "keyword", index: checkIntRange(n), };

            }
        }
    }
}

async function readInt(reader: ByteReader, b: number): Promise<bigint> {
    let n = BigInt(b & 0x0F);
    let bitOffset = 4n;
    let hasNext = (b & 0x10) == 0x10;

    while(hasNext) {
        b = await reader.readByte();
        n |= BigInt(b & 0x7F) << bitOffset;
        bitOffset += 7n;
        hasNext = (b & 0x80) == 0x80;
    }

    return n;
}

function checkIntRange(n: bigint): number {
    if(n > Number.MAX_SAFE_INTEGER) {
        throw new Error("Integer is too large");
    }

    return Number(n);
}

async function* readExprs(tokens: AsyncIterator<Token>, stringPool: StringPool): AsyncIterable<ESExpr> {
    for(;;) {
        const token = await tokens.next();
        if(token.done) {
            break;
        }

        yield readExprWith(tokens, token.value, stringPool);
    }
}

async function readExpr(tokens: AsyncIterator<Token>, stringPool: StringPool): Promise<ESExpr> {
    const token = await tokens.next();
    if(token.done) {
        throw new Error("Unexpected end of file");
    }

    return await readExprWith(tokens, token.value, stringPool);
}

async function readExprWith(tokens: AsyncIterator<Token>, startToken: Token, stringPool: StringPool): Promise<ESExpr> {
    switch(startToken.type) {
        case "constructor_start":
        {
            const name = stringPool.get(startToken.index);
            return await readExprConstructor(tokens, stringPool, name);
        }

        case "constructor_start_known":
            return await readExprConstructor(tokens, stringPool, startToken.value);

        case "constructor_end":
            throw new Error("Unexpected constructor end");

        case "keyword":
            throw new Error("Unexpected constructor end");

        case "int_value":
            return startToken.value;

        case "string_value":
            return startToken.s;

        case "string_pool_value":
            return stringPool.get(startToken.index);

        case "binary_value":
            return startToken.value;

        case "float32_value":
            return { type: "float32", value: startToken.value };

        case "float64_value":
            return startToken.value;

        case "boolean_value":
            return startToken.value;

        case "null_value":
            return null;
    }
}

async function readExprConstructor(tokens: AsyncIterator<Token>, stringPool: StringPool, name: string): Promise<ESExpr> {
    const args: ESExpr[] = [];
    const kwargs = new Map<string, ESExpr>();

    args:
    for(;;) {
        const token = await tokens.next();
        if(token.done) {
            throw new Error("Missing constructor end");
        }

        switch(token.value.type) {
            case "constructor_end":
                break args;

            case "keyword":
            {
                const kw = stringPool.get(token.value.index);
                const value = await readExpr(tokens, stringPool);
                kwargs.set(kw, value);
                break;
            }
            
            default:
                args.push(await readExprWith(tokens, token.value, stringPool));
                break;
        }
    }

    return {
        type: "constructor",
        name,
        args,
        kwargs,
    };
}


export async function* readExprStream(data: AsyncIterable<Uint8Array>, stringPool: StringPool): AsyncIterator<ESExpr> {
    const dataIter = data[Symbol.asyncIterator]();
    try {
        const reader = new ByteReader(dataIter);
        const tokens = getTokens(reader);
        const tokenIter = tokens[Symbol.asyncIterator]();
        try {
            yield* readExprs(tokenIter, stringPool);
        }
        catch(e) {
            if(tokenIter.return) await tokenIter.return();
            throw e;
        }
    }
    catch(e) {
        if(dataIter.return) await dataIter.return();
        throw e;
    }
}

export async function* readExprStreamEmbeddedStringPool(data: AsyncIterable<Uint8Array>): AsyncIterator<ESExpr> {
    const dataIter = data[Symbol.asyncIterator]();
    try {
        const reader = new ByteReader(dataIter);
        const tokens = getTokens(reader);
        const tokenIter = tokens[Symbol.asyncIterator]();
        try {
            const spRes = StringPoolEncoded.codec.decode(await readExpr(tokenIter, new ArrayStringPool([])));
            if(!spRes.success) {
                throw new Error("Invalid string pool");
            }

            const sp = ArrayStringPool.fromEncoded(spRes.value);

            yield* readExprs(tokenIter, sp);
        }
        catch(e) {
            if(tokenIter.return) await tokenIter.return();
            throw e;
        }
    }
    catch(e) {
        if(dataIter.return) await dataIter.return();
        throw e;
    }
}



async function writeByte(b: number): Promise<Uint8Array> {
    return new Uint8Array([b]);
}

async function* writeInt(tag: number, value: bigint): AsyncIterable<Uint8Array> {
    let bits = Number(value & 0x0Fn);
    value >>= 4n;

    let hasNext = value > 0;

    yield writeByte(tag | (hasNext ? 0x10 : 0x00) | bits);

    while(hasNext) {
        bits = Number(value & 0x7Fn);
        value >>= 7n;
        hasNext = value > 0;

        yield writeByte((hasNext ? 0x80 : 0x00) | bits);
    }
}

export async function* writeExpr(e: ESExpr, stringPool: StringPool): AsyncIterable<Uint8Array> {
    switch(typeof e) {
        case "boolean":
            yield writeByte(e ? TAG_TRUE : TAG_FALSE);
            break;

        case "bigint":
            if(e >= 0n) {
                yield* writeInt(TAG_VARINT_NON_NEG_INT, e);
            }
            else {
                yield* writeInt(TAG_VARINT_NEG_INT, -e + 1n);
            }
            break;

        case "string":
        {
            const buff = new TextEncoder().encode(e);
            yield* writeInt(TAG_VARINT_STRING_LENGTH, BigInt(buff.length));
            yield buff;
            break;
        }
        
        case "number":
        {
            const data = new Uint8Array(9);
            data[0] = TAG_FLOAT64;
            new Float64Array(data, data.byteOffset, 1)[0] = e;
            yield data;
            break;
        }

        case "object":
            if(e === null) {
                yield writeByte(TAG_NULL);
            }
            else if(e instanceof Uint8Array) {
                yield* writeInt(TAG_VARINT_BYTES_LENGTH, BigInt(e.length));
                yield e;
            }
            else {
                switch(e.type) {
                    case "constructor":
                    {
                        switch(e.name) {
                            case "string-table":
                                yield writeByte(0xE6);
                                break;

                            case "list":
                                yield writeByte(0xE7);
                                break;

                            default:
                                const index = stringPool.lookup(e.name);
                                yield* writeInt(TAG_VARINT_CONSTRUCTOR_START, BigInt(index));
                        }

                        for(const arg of e.args) {
                            yield* writeExpr(arg, stringPool);
                        }

                        for(const [kw, value] of e.kwargs) {
                            const index = stringPool.lookup(kw);
                            yield* writeInt(TAG_VARINT_KEYWORD, BigInt(index));
                            
                            yield* writeExpr(value, stringPool);
                        }

                        yield writeByte(TAG_CONSTRUCTOR_END);
                        break;
                    }

                    case "float32":
                    {
                        const data = new Uint8Array(5);
                        data[0] = TAG_FLOAT32;
                        new Float32Array(data, data.byteOffset, 1)[0] = e.value;
                        yield data;
                        break;   
                    }
                }
            }
            break;
    }
}




export interface StringPool {
    get(i: number): string;
    lookup(s: string): number;
}


export namespace StringPool {
    export function fromArray(values: readonly string[]): StringPool {
        return new ArrayStringPool(values);
    }
}

export class ArrayStringPool implements StringPool {
    constructor(values: readonly string[]) {
        this.#values = values;
    }

    readonly #values: readonly string[];

    
    get(i: number): string {
        const s = this.#values[i];
        if(s === undefined) {
            throw new Error("Invalid string pool index");
        }

        return s;
    }

    lookup(s: string): number {
        const i = this.#values.indexOf(s);
        if(i < 0) {
            throw new Error("String not present in string pool");
        }

        return i;
    }

    static fromEncoded(encoded: StringPoolEncoded): ArrayStringPool {
        return new ArrayStringPool(encoded.values);
    }

    toEncoded(): StringPoolEncoded {
        return {
            values: this.#values,
        };
    }
}


export interface StringPoolEncoded {
    values: readonly string[],
}

export namespace StringPoolEncoded {
    export const codec: ESExprCodec<StringPoolEncoded> = esexpr.recordCodec(
        "string-table",
        {
            values: esexpr.varargFieldCodec(esexpr.arrayRepeatedValuesCodec(esexpr.strCodec)),
        },
    );
}


export class StringPoolBuilder {
    constructor() {
        this.#values = [];
    }

    adapter(): StringPool {
        const spb = this;
        return {
            get(_i: number): string {
                throw new Error("Not supported");
            },
        
            lookup(s: string): number {
                const index = spb.#values.indexOf(s);
                if(index >= 0) {
                    return index;
                }

                const index2 = spb.#values.length;
                spb.#values.push(s);
                return index2;
            },
        };
    }


    #values: string[];

    toStringPool(): ArrayStringPool {
        const values = this.#values;
        this.#values = [];
        return new ArrayStringPool(values);
    }
}

