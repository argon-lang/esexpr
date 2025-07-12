import * as esexpr from "./index.js";
import type { ESExpr, ESExprCodec } from "./index.js";
import { unreachable } from "./util.js";


export class ESExprFormatError extends Error {}


type Token =
    | { type: "constructor_start", index: number }
    | { type: "constructor_start_known", value: string }
    | { type: "constructor_end" }
    | { type: "keyword", index: number }
    | { type: "int_value", value: bigint }
    | { type: "string_value", value: string }
    | { type: "string_pool_value", index: number }
    | { type: "array8_value", value: Uint8Array }
    | { type: "array16_value", value: Uint16Array }
    | { type: "array32_value", value: Uint32Array }
    | { type: "array64_value", value: BigUint64Array }
    | { type: "array128_value", value: Uint8Array }
    | { type: "float16_value", value: number }
    | { type: "float16_nan", bits: number }
    | { type: "float32_value", value: number }
    | { type: "float32_nan", bits: number }
    | { type: "float64_value", value: number }
    | { type: "float64_nan", bits: bigint }
    | { type: "boolean_value", value: boolean }
    | { type: "null_value", level: bigint }
    | { type: "append_string_table" }
;

type ExprPlus =
    | ESExpr
    | { type: "keyword", index: number }
    | { type: "constructor_end" }
    | { type: "appended_to_string_table" }
    | { type: "eof" }
;

const TAG_VARINT_MASK = 0xE0;
const TAG_VARINT_CONSTRUCTOR_START = 0x00;
const TAG_VARINT_NON_NEG_INT = 0x20;
const TAG_VARINT_NEG_INT = 0x40;
const TAG_VARINT_STRING_LENGTH = 0x60;
const TAG_VARINT_STRING_POOL = 0x80;
const TAG_VARINT_ARRAY8_LENGTH = 0xA0;
const TAG_VARINT_KEYWORD = 0xC0;


const TAG_CONSTRUCTOR_END = 0xE0;
const TAG_TRUE = 0xE1;
const TAG_FALSE = 0xE2;
const TAG_NULL0 = 0xE3;
const TAG_NULL1 = 0xE8;
const TAG_NULL2 = 0xE9;
const TAG_NULLN = 0xEA;
const TAG_FLOAT16 = 0xEC;
const TAG_FLOAT32 = 0xE4;
const TAG_FLOAT64 = 0xE5;
const TAG_CONSTRUCTOR_START_STRING_TABLE = 0xE6;
const TAG_CONSTRUCTOR_START_LIST = 0xE7;
const TAG_APPEND_STRING_TABLE = 0xEB;
const TAG_ARRAY16 = 0xED;
const TAG_ARRAY32 = 0xEE;
const TAG_ARRAY64 = 0xEF;
const TAG_ARRAY128 = 0xF0;


const isBigEndian: boolean = (() => {
    const u32 = new Uint32Array([0x12345678]);
    const u8 = new Uint8Array(u32.buffer);
    return u8[0]! !== 0x78;
})();

const u8ToU16: (u8: Uint8Array, len: number) => Uint16Array = isBigEndian
    ? ((u8, len) => {
        const buff = new DataView(u8.buffer, u8.byteOffset, u8.byteLength);
        for(let i = 0; i < len; ++i) {
            buff.setUint16(i * 2, buff.getUint16(i * 2, false), true);
        }
        return new Uint16Array(u8.buffer, u8.byteOffset, len);
    })
    : ((u8, len) => new Uint16Array(u8.buffer, u8.byteOffset, len));

const u8ToU32: (u8: Uint8Array, len: number) => Uint32Array = isBigEndian
    ? ((u8, len) => {
        const buff = new DataView(u8.buffer, u8.byteOffset, u8.byteLength);
        for(let i = 0; i < len; ++i) {
            buff.setUint32(i * 4, buff.getUint32(i * 4, false), true);
        }
        return new Uint32Array(u8.buffer, u8.byteOffset, len);
    })
    : ((u8, len) => new Uint32Array(u8.buffer, u8.byteOffset, len));

const u8ToU64: (u8: Uint8Array, len: number) => BigUint64Array = isBigEndian
    ? ((u8, len) => {
        const buff = new DataView(u8.buffer, u8.byteOffset, u8.byteLength);
        for(let i = 0; i < len; ++i) {
            buff.setBigUint64(i * 8, buff.getBigUint64(i * 8, false), true);
        }
        return new BigUint64Array(u8.buffer, u8.byteOffset, len);
    })
    : ((u8, len) => new BigUint64Array(u8.buffer, u8.byteOffset, len));

const u16ToU8: (u16: Uint16Array) => Uint8Array = isBigEndian
    ? (u16 => {
        const buff = new DataView(u16.buffer, u16.byteOffset, u16.byteLength);
        for (let i = 0; i < u16.length; ++i) {
            buff.setUint16(i * 2, buff.getUint16(i * 2, true), false);
        }
        return new Uint8Array(u16.buffer, u16.byteOffset, u16.byteLength);
    })
    : (u16 => new Uint8Array(u16.buffer, u16.byteOffset, u16.byteLength));

const u32ToU8: (u32: Uint32Array) => Uint8Array = isBigEndian
    ? (u32 => {
        const buff = new DataView(u32.buffer, u32.byteOffset, u32.byteLength);
        for (let i = 0; i < u32.length; ++i) {
            buff.setUint32(i * 4, buff.getUint32(i * 4, true), false);
        }
        return new Uint8Array(u32.buffer, u32.byteOffset, u32.byteLength);
    })
    : (u32 => new Uint8Array(u32.buffer, u32.byteOffset, u32.byteLength));

const u64ToU8: (u64: BigUint64Array) => Uint8Array = isBigEndian
    ? (u64 => {
        const buff = new DataView(u64.buffer, u64.byteOffset, u64.byteLength);
        for (let i = 0; i < u64.length; ++i) {
            buff.setBigUint64(i * 8, buff.getBigUint64(i * 8, true), false);
        }
        return new Uint8Array(u64.buffer, u64.byteOffset, u64.byteLength);
    })
    : (u64 => new Uint8Array(u64.buffer, u64.byteOffset, u64.byteLength));
    

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
            s += decoder.decode(buff.subarray(this.#index, this.#index + len), { stream: true });
            this.#index += len;
            offset += len;
        }
         
        s += decoder.decode();
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
            throw new ESExprFormatError("Unexpected end of file");
        }
        return buff;
    }
}

export class ExprReader {
    constructor(data: AsyncIterable<Uint8Array>, stringPool?: StringPool) {
        this.#tokens = getTokens(new ByteReader(data[Symbol.asyncIterator]()));
        this.#stringPool = stringPool ?? new ArrayStringPool();
    }

    readonly #tokens: AsyncIterator<Token>;
    readonly #stringPool: StringPool;

    async tryReadExpr(): Promise<ESExpr | undefined> {
        read:
        for(;;) {
            const expr = await this.#readExprPlus();
    
            if(typeof expr === "object" && expr !== null && !ArrayBuffer.isView(expr)) {
                switch(expr.type) {
                    case "constructor_end":
                        throw new ESExprFormatError("Unexpected constructor end");
    
                    case "keyword":
                        throw new ESExprFormatError("Unexpected keyword: index=" + expr.index);
    
                    case "appended_to_string_table":
                        continue read;
    
                    case "eof":
                        return undefined;
    
                    default:
                        return expr;
                }
            }
            else {
                return expr;
            }
        }
    }

    async readExpr(): Promise<ESExpr> {
        const expr = await this.tryReadExpr();
        if(expr === undefined) {
            throw new ESExprFormatError("Unexpected end of file");
        }

        return expr;
    }

    async * readAll(): AsyncIterable<ESExpr> {
        for(;;) {
            const expr = await this.tryReadExpr();
            if(expr === undefined) {
                break;
            }

            yield expr;
        }
    }

    async #readExprPlus(): Promise<ExprPlus> {
        const startTokenRes = await this.#tokens.next();
        if(startTokenRes.done) {
            return { type: "eof" };
        }

        const startToken = startTokenRes.value;

        switch(startToken.type) {
            case "constructor_start":
            {
                const name = this.#stringPool.get(startToken.index);
                return await this.#readExprConstructor(name);
            }

            case "constructor_start_known":
                return await this.#readExprConstructor(startToken.value);

            case "constructor_end":
                return { type: "constructor_end" };

            case "keyword":
                return { type: "keyword", index: startToken.index };


            case "string_pool_value":
                return this.#stringPool.get(startToken.index);

            case "float16_value":
                return { type: "float16", value: startToken.value };

            case "float16_nan":
                return { type: "float16-nan", bits: startToken.bits };

            case "float32_value":
                return { type: "float32", value: startToken.value };

            case "float32_nan":
                return { type: "float32-nan", bits: startToken.bits };

            case "float64_nan":
                return { type: "float64-nan", bits: startToken.bits };

            case "int_value":
            case "float64_value":
            case "boolean_value":
            case "string_value":
            case "array8_value":
            case "array16_value":
            case "array32_value":
            case "array64_value":
                return startToken.value;

            case "null_value":
                if(startToken.level > 0) {
                    return {
                        type: "null",
                        level: startToken.level,
                    };
                }
                else {
                    return null;
                }

            case "append_string_table":
            {
                const newStringPool = await this.readExpr();

                if(typeof newStringPool === "string") {
                    this.#stringPool.append(newStringPool);
                }
                else {
                    const spRes = StringPoolEncoded.codec.decode(newStringPool);
                    if(!spRes.success) {
                        throw new ESExprFormatError("Invalid string pool");
                    }
        
                    this.#stringPool.append(spRes.value.values);
                }

                return { type: "appended_to_string_table" };
            }

            case "array128_value":
                return {
                    type: "array128",
                    value: startToken.value,
                };

            default:
                unreachable(startToken, "Unexpected expression token");
        }
    }

    async #readExprConstructor(name: string): Promise<ESExpr> {
        const args: ESExpr[] = [];
        const kwargs = new Map<string, ESExpr>();

        args:
        for(;;) {
            const expr = await this.#readExprPlus();

            if(typeof expr === "object" && expr !== null && !ArrayBuffer.isView(expr)) {
                switch(expr.type) {
                    case "constructor_end":
                        break args;

                    case "keyword":
                    {
                        const kw = this.#stringPool.get(expr.index);
                        const value = await this.readExpr();
                        kwargs.set(kw, value);
                        break;
                    }

                    case "appended_to_string_table":
                        continue args;

                    case "eof":
                        throw new ESExprFormatError("Unexpected end of file");

                    default:
                        args.push(expr);
                        break;
                }
            }
            else {
                args.push(expr);
            }
        }

        return {
            type: "constructor",
            name,
            args,
            kwargs,
        };
    }
}


async function* getTokens(reader: ByteReader): AsyncIterator<Token> {
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

                case TAG_NULL0:
                    yield { type: "null_value", level: 0n };
                    break;

                case TAG_NULL1:
                    yield { type: "null_value", level: 1n };
                    break;

                case TAG_NULL2:
                    yield { type: "null_value", level: 2n };
                    break;

                case TAG_NULLN:
                {
                    const level = await readIntFull(reader);
                    yield { type: "null_value", level: level + 3n };
                    break;
                }

                case TAG_FLOAT16:
                {
                    const buff = await reader.readFixed(2);
                    const dv = new DataView(buff.buffer, buff.byteOffset, buff.byteLength);
                    const value = dv.getFloat16(0, true);
                    if(Number.isNaN(value)) {
                        yield { type: "float16_nan", bits: dv.getUint16(0, true) };
                    }
                    else {
                        yield { type: "float16_value", value };
                    }
                    break;
                }

                case TAG_FLOAT32:
                {
                    const buff = await reader.readFixed(4);
                    const dv = new DataView(buff.buffer, buff.byteOffset, buff.byteLength);
                    const value = dv.getFloat32(0, true);
                    if(Number.isNaN(value)) {
                        yield { type: "float32_nan", bits: dv.getUint32(0, true) };
                    }
                    else {
                        yield { type: "float32_value", value };
                    }
                    break;
                }

                case TAG_FLOAT64:
                {
                    const buff = await reader.readFixed(8);
                    const dv = new DataView(buff.buffer, buff.byteOffset, buff.byteLength);
                    const value = dv.getFloat64(0, true);
                    if(Number.isNaN(value)) {
                        yield { type: "float64_nan", bits: dv.getBigUint64(0, true) };
                    }
                    else {
                        yield { type: "float64_value", value };
                    }
                    break;
                }

                case TAG_CONSTRUCTOR_START_STRING_TABLE:
                    yield { type: "constructor_start_known", value: "string-table" };
                    break;

                case TAG_CONSTRUCTOR_START_LIST:
                    yield { type: "constructor_start_known", value: "list" };
                    break;

                case TAG_APPEND_STRING_TABLE:
                    yield { type: "append_string_table" };
                    break;

                case TAG_ARRAY16:
                {
                    const n = await readIntFull(reader);
                    const value = u8ToU16(await reader.readFixed(checkIntRange(n * 2n)), Number(n));
                    yield {
                        type: "array16_value",
                        value,
                    };
                    break;
                }

                case TAG_ARRAY32:
                {
                    const n = await readIntFull(reader);
                    const value = u8ToU32(await reader.readFixed(checkIntRange(n * 4n)), Number(n));
                    yield {
                        type: "array32_value",
                        value,
                    };
                    break;
                }

                case TAG_ARRAY64:
                {
                    const n = await readIntFull(reader);
                    const value = u8ToU64(await reader.readFixed(checkIntRange(n * 8n)), Number(n));
                    yield {
                        type: "array64_value",
                        value,
                    };
                    break;
                }

                case TAG_ARRAY128:
                {
                    const n = await readIntFull(reader);
                    const value = await reader.readFixed(checkIntRange(n * 16n));
                    yield {
                        type: "array128_value",
                        value,
                    };
                    break;
                }

                default:
                    throw new ESExprFormatError("Invalid token byte");
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
                    const value = await reader.readString(checkIntRange(n));
                    yield { type: "string_value", value };
                    break;
                }

                case TAG_VARINT_STRING_POOL:
                    yield { type: "string_pool_value", index: checkIntRange(n), };
                    break;

                case TAG_VARINT_ARRAY8_LENGTH:
                    {
                        const value = await reader.readFixed(checkIntRange(n));
                        yield { type: "array8_value", value };
                        break;
                    }

                case TAG_VARINT_KEYWORD:
                    yield { type: "keyword", index: checkIntRange(n), };
            }
        }
    }
}

function readInt(reader: ByteReader, b: number): Promise<bigint> {
    let n = BigInt(b & 0x0F);
    let bitOffset = 4n;
    let hasNext = (b & 0x10) == 0x10;

    return readIntRest(reader, n, bitOffset, hasNext)
}

function readIntFull(reader: ByteReader): Promise<bigint> {
    let n = 0n;
    let bitOffset = 0n;
    let hasNext = true;

    return readIntRest(reader, n, bitOffset, hasNext)
}

async function readIntRest(reader: ByteReader, n: bigint, bitOffset: bigint, hasNext: boolean): Promise<bigint> {
    while(hasNext) {
        const b = await reader.readByte();
        n |= BigInt(b & 0x7F) << bitOffset;
        bitOffset += 7n;
        hasNext = (b & 0x80) == 0x80;
    }

    return n;
}



export function readExprStream(data: AsyncIterable<Uint8Array>, stringPool?: StringPool): AsyncIterable<ESExpr> {
    const reader = new ExprReader(data, stringPool);
    return reader.readAll();
}


function checkIntRange(n: bigint): number {
    if(n > Number.MAX_SAFE_INTEGER) {
        throw new ESExprFormatError("Integer is too large");
    }

    return Number(n);
}



async function writeByte(b: number): Promise<Uint8Array> {
    return new Uint8Array([b]);
}

async function* writeInt(tag: number, value: bigint): AsyncIterable<Uint8Array> {
    let bits = Number(value & 0x0Fn);
    value >>= 4n;

    let hasNext = value > 0;

    yield writeByte(tag | (hasNext ? 0x10 : 0x00) | bits);

    yield* writeIntRest(value, hasNext);
}

async function* writeIntFull(value: bigint): AsyncIterable<Uint8Array> {
    yield* writeIntRest(value, true);
}

async function* writeIntRest(value: bigint, hasNext: boolean): AsyncIterable<Uint8Array> {
    while(hasNext) {
        const bits = Number(value & 0x7Fn);
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
                yield* writeInt(TAG_VARINT_NEG_INT, -e - 1n);
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
            new DataView(data.buffer, 1, 8).setFloat64(0, e, true);
            yield data;
            break;
        }

        case "object":
            if(e === null) {
                yield writeByte(TAG_NULL0);
            }
            else if(e instanceof Uint8Array) {
                yield* writeInt(TAG_VARINT_ARRAY8_LENGTH, BigInt(e.length));
                yield e;
            }
            else if(e instanceof Uint16Array) {
                yield writeByte(TAG_ARRAY16);
                yield* writeIntFull(BigInt(e.length));
                yield u16ToU8(e);
            }
            else if(e instanceof Uint32Array) {
                yield writeByte(TAG_ARRAY32);
                yield* writeIntFull(BigInt(e.length));
                yield u32ToU8(e);
            }
            else if(e instanceof BigUint64Array) {
                yield writeByte(TAG_ARRAY64);
                yield* writeIntFull(BigInt(e.length));
                yield u64ToU8(e);
            }
            else {
                async function* writeStringTag(tag: number, s: string): AsyncIterable<Uint8Array> {
                    let index = stringPool.lookupIndex(s);
                    if(index === undefined) {
                        yield writeByte(TAG_APPEND_STRING_TABLE);
                        yield* writeExpr(s, stringPool);
                        index = stringPool.append(s);
                    }
                    
                    yield* writeInt(tag, BigInt(index));
                }

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
                                yield* writeStringTag(TAG_VARINT_CONSTRUCTOR_START, e.name);
                        }

                        for(const arg of e.args) {
                            yield* writeExpr(arg, stringPool);
                        }

                        for(const [kw, value] of e.kwargs) {
                            yield* writeStringTag(TAG_VARINT_KEYWORD, kw);                            
                            yield* writeExpr(value, stringPool);
                        }

                        yield writeByte(TAG_CONSTRUCTOR_END);
                        break;
                    }

                    case "float16":
                    {
                        const data = new Uint8Array(3);
                        data[0] = TAG_FLOAT16;
                        new DataView(data.buffer, 1, 2).setFloat16(0, e.value, true);
                        yield data;
                        break;
                    }

                    case "float16-nan":
                    {
                        const data = new Uint8Array(3);
                        data[0] = TAG_FLOAT16;
                        new DataView(data.buffer, 1, 2).setUint16(0, e.bits, true);
                        yield data;
                        break;
                    }

                    case "float32":
                    {
                        const data = new Uint8Array(5);
                        data[0] = TAG_FLOAT32;
                        new DataView(data.buffer, 1, 4).setFloat32(0, e.value, true);
                        yield data;
                        break;   
                    }

                    case "float32-nan":
                    {
                        const data = new Uint8Array(5);
                        data[0] = TAG_FLOAT32;
                        new DataView(data.buffer, 1, 4).setUint32(0, e.bits, true);
                        yield data;
                        break;   
                    }
        
                    case "float64-nan":
                    {
                        const data = new Uint8Array(9);
                        data[0] = TAG_FLOAT64;
                        new DataView(data.buffer, 1, 8).setBigUint64(0, e.bits, true);
                        yield data;
                        break;
                    }

                    case "null":
                    {
                        if(e.level === 1n) {
                            yield writeByte(TAG_NULL1);
                        }
                        else if(e.level === 2n) {
                            yield writeByte(TAG_NULL2);
                        }
                        else {
                            yield writeByte(TAG_NULLN);
                            yield* writeIntFull(e.level - 3n);
                        }
                        break;
                    }

                    case "array128":
                    {
                        yield writeByte(TAG_ARRAY128);
                        yield* writeIntFull(BigInt(e.value.length / 16));
                        yield e.value;
                        break;
                    }

                    default:
                        unreachable(e, "Unexpected esexpr value");
                }
            }
            break;
    }
}

async function drain(iter: AsyncIterable<unknown>): Promise<void> {
    for await(const _ of iter) {}
}

export async function* writeExprs(exprs: AsyncIterable<ESExpr> | Iterable<ESExpr>): AsyncIterable<Uint8Array> {
    const sp = new ArrayStringPool();
    for await(const expr of exprs) {
        const oldLength = sp.length;

        await drain(writeExpr(expr, sp));

        const newLength = sp.length;

        if(newLength > oldLength) {
            yield writeByte(TAG_APPEND_STRING_TABLE);

            const n = newLength - oldLength;
            if(n == 1) {
                yield* writeExpr(sp.get(oldLength), new ArrayStringPool());
            }
            else {
                const values: string[] = [];
                for(let i = oldLength; i < newLength; ++i) {
                    values.push(sp.get(i));
                }

                const spExpr = StringPoolEncoded.codec.encode({ values });
                yield* writeExpr(spExpr, new ArrayStringPool());
            }
        }

        yield* writeExpr(expr, sp);
    }
}




export interface StringPool {
    get(i: number): string;
    lookupIndex(s: string): number | undefined;
    append(s: string | readonly string[]): number;
}


export namespace StringPool {
    export function fromArray(values: readonly string[]): StringPool {
        return new ArrayStringPool(values);
    }
}

export class ArrayStringPool implements StringPool {
    constructor(values?: readonly string[]) {
        this.#values = values !== undefined ? [...values] : [];
    }

    readonly #values: string[];

    get length(): number {
        return this.#values.length;
    }

    
    get(i: number): string {
        const s = this.#values[i];
        if(s === undefined) {
            throw new ESExprFormatError("Invalid string pool index");
        }

        return s;
    }

    lookupIndex(s: string): number | undefined {
        const i = this.#values.indexOf(s);
        if(i < 0) {
            return undefined;
        }

        return i;
    }

    append(s: string | readonly string[]): number {
        const index = this.#values.length;

        if(typeof s === "string") {
            this.#values.push(s);
        }
        else {
            this.#values.push(...s);
        }

        return index;
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
