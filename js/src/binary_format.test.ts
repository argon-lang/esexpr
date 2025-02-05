import { expect, test } from "vitest";
import { ESExpr } from "./index.js";
import * as esxb from "./binary_format.js"

import * as fs from "node:fs/promises";
import * as path from "node:path";

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
    | { float32: number | "+inf" | "-inf" }
    | { float64: number | "+inf" | "-inf" }
    | { base64: string }
    | { null: string }

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
    else if("null" in json) {
        return { type: "null", level: BigInt(json.null) };
    }
    else {
        console.error(json);
        return absurd(json, "Invalid ESExpr JSON");
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

function absurd(_x: never, message: string): never {
    throw new Error(message);
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
        await run_test_case(fileName);
    });

}
