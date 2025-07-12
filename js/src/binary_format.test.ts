import { expect, test } from "vitest";
import { ESExpr } from "./index.js";
import * as esxb from "./binary_format.js"

import * as fs from "node:fs/promises";
import * as path from "node:path";
import { unreachable } from "./util.js";

type KWArgs = {
    [K: string]: ESExprJson,
};

type ESExprJson =
    | string
    | boolean
    | null
    | { constructor_name: string, args?: readonly ESExprJson[], kwargs?: KWArgs }
    | readonly ESExprJson[]
    | { int: string }
    | { float16: number | "+inf" | "-inf" }
    | { float32: number | "+inf" | "-inf" }
    | { float64: number | "+inf" | "-inf" }
    | { base64: string }
    | { array8: readonly number[] }
    | { array16: readonly number[] }
    | { array32: readonly number[] }
    | { array64: readonly (number | string)[] }
    | { array128: readonly (number | string)[] }
    | { null: string }
;

function json2esexpr(json: ESExprJson): ESExpr {
    if(typeof json === "string" ||  typeof json === "boolean" || json === null) {
        return json;
    }
    else if(json instanceof Array) {
        return json2esexpr({
            constructor_name: "list",
            args: json,
        });
    }
    else if("constructor_name" in json) {
        const kwargs = new Map<string, ESExpr>();
        if(json.kwargs !== undefined) {
            for(const [k, v] of Object.entries(json.kwargs)) {
                kwargs.set(k, json2esexpr(v));
            }
        }

        return {
            type: "constructor",
            name: json.constructor_name,
            args: (json.args ?? []).map(json2esexpr),
            kwargs,
        }
    }
    else if("int" in json) {
        return BigInt(json.int);
    }
    else if("float16" in json) {
        if(json.float16 === "+inf") {
            return { type: "float16", value: Number.POSITIVE_INFINITY };
        }
        else if(json.float16 === "-inf") {
            return { type: "float16", value: Number.NEGATIVE_INFINITY };
        }
        else {
            return { type: "float16", value: Math.f16round(json.float16) };
        }
    }
    else if("float32" in json) {
        if(json.float32 === "+inf") {
            return { type: "float32", value: Number.POSITIVE_INFINITY };
        }
        else if(json.float32 === "-inf") {
            return { type: "float32", value: Number.NEGATIVE_INFINITY };
        }
        else {
            return { type: "float32", value: Math.fround(json.float32) };
        }
    }
    else if("float64" in json) {
        if(json.float64 === "+inf") {
            return Number.POSITIVE_INFINITY;
        }
        else if(json.float64 === "-inf") {
            return Number.NEGATIVE_INFINITY;
        }
        else {
            return json.float64;
        }
    }
    else if("base64" in json) {
        return new Uint8Array(Buffer.from(json.base64, "base64"));
    }
    else if("array8" in json) {
        return new Uint8Array(json.array8);
    }
    else if("array16" in json) {
        return new Uint16Array(json.array16);
    }
    else if("array32" in json) {
        return new Uint32Array(json.array32);
    }
    else if("array64" in json) {
        return new BigUint64Array(json.array64.map(n => BigInt(n)));
    }
    else if("array128" in json) {
        return {
            type: "array128",
            value: new Uint8Array(json.array128.flatMap(n => {
                let n2 = BigInt(n);
                const bytes: number[] = [];
                for(let i = 0; i < 16; ++i) {
                    bytes.push(Number(BigInt.asUintN(8, n2)));
                    n2 >>= 8n;
                }
                return bytes;
            })),
        };
    }
    else if("null" in json) {
        return { type: "null", level: BigInt(json.null) };
    }
    else {
        console.error(json);
        return unreachable(json, "Invalid ESExpr JSON");
    }
}

function json2esexprMany(json: ESExprJson): ESExpr[] {
    if(json instanceof Array) {
        return json.map(json2esexpr);
    }
    else {
        return [ json2esexpr(json) ];
    }
}

async function* arrayToAsyncIterable<A>(arr: readonly A[]): AsyncIterable<A> {
    for(const a of arr) {
        yield a;
    }
}

async function arrayFromAsync<T>(iter: AsyncIterable<T>): Promise<T[]> {
    const res: T[] = [];
    for await(const x of iter) {
        res.push(x);
    }
    return res;
}


async function* encodeBin(expr: ESExpr): AsyncIterable<Uint8Array> {
    yield* esxb.writeExprs([ expr ]);
}

async function decodeBinMany(data: AsyncIterable<Uint8Array>): Promise<ESExpr[]> {
    const reader = new esxb.ExprReader(data);
    return await arrayFromAsync(reader.readAll());
}

async function decodeBin1(data: AsyncIterable<Uint8Array>): Promise<ESExpr> {
    const esxbArray = await decodeBinMany(data);

    if(esxbArray.length !== 1) {
        throw new Error("Expected a single expr");
    }

    return esxbArray[0]!;
}

async function* singleByteChunks(data: AsyncIterable<Uint8Array>): AsyncIterable<Uint8Array> {
    for await(const arr of data) {
        for(const b of arr) {
            yield new Uint8Array([ b ]);
        }
    }
}


async function run_test_case(esxbFile: string): Promise<void> {
    const esxbData: Uint8Array = await fs.readFile(esxbFile);
    const exprs = await decodeBinMany(arrayToAsyncIterable([ esxbData ]));

    const json: ESExprJson = JSON.parse(
        await fs.readFile(esxbFile.substring(0, esxbFile.length - 4) + "json", { encoding: "utf-8" })
    );

    const reencoded: ESExpr[] = [];
    for(const expr of exprs) {
        reencoded.push(await decodeBin1(encodeBin(expr)))
    }

    expect(json2esexprMany(json)).toEqual(exprs);
    expect(reencoded).toEqual(exprs);
}

const dir = path.join(import.meta.dirname, "../../tests/");
for(const file of await fs.readdir(dir, { withFileTypes: true })) {
    if(file.isDirectory()) {
        continue;
    }

    if(path.extname(file.name) !== ".esxb") {
        continue;
    }
    
    const fileName = path.join(dir, file.name);
    
    test("Binary Format " + file.name, async () => {
        console.log("Test case", fileName);
        await run_test_case(fileName);
    });

}

test("Multi byte strings", async () => {
    const strings = [
        "ñ",     // Latin-1 Supplement (U+00F1)
        "Δ",     // Greek (U+0394)
        "Я",     // Cyrillic (U+042F)
        "ש",     // Hebrew (U+05E9)
        "ك",     // Arabic (U+0643)
        "漢",    // CJK (Chinese/Japanese/Korean, U+6F22)
        "❤",    // Emoji (U+2764)
        "😊",    // Emoji (U+1F60A)
        "𐎀",    // Historic scripts (U+10380, Ugaritic letter)
        "𝕏"      // Mathematical symbols (U+1D54F)
    ];
      
    for(const s of strings) {
        const converted = await decodeBin1(singleByteChunks(encodeBin(s)))
        expect(converted).toEqual(s);
    }
}) 

