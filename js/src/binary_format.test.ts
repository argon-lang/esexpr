import { expect, test } from "vitest";
import { ESExpr } from "./index.js";
import * as esxb from "./binary_format.js"

import * as fs from "node:fs/promises";
import * as path from "node:path";

import { valuesEqual } from "./util.js";


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
    | { float32: number }
    | { float64: number }
    | { base64: string }
    | { null: bigint }

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
        return { type: "float32", value: json.float32 };
    }
    else if("float64" in json) {
        return json.float64;
    }
    else if("base64" in json) {
        return Buffer.from(json.base64, "base64");
    }
    else if("null" in json) {
        return { type: "null", level: json.null };
    }
    else {
        console.error(json);
        return absurd(json, "Invalid ESExpr JSON");
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


async function* encodeBin(expr: ESExpr): AsyncIterable<Uint8Array> {
    const spb = new esxb.StringPoolBuilder();
    const encoded = await Array.fromAsync(esxb.writeExpr(expr, spb.adapter()));

    const sp = spb.toStringPool().toEncoded();

    yield* esxb.writeExpr(esxb.StringPoolEncoded.codec.encode(sp), new esxb.ArrayStringPool([]));
    yield* encoded;
}

async function decodeBin(data: AsyncIterable<Uint8Array>): Promise<ESExpr> {
    const esxbArray = await Array.fromAsync({
        [Symbol.asyncIterator]() {
            return esxb.readExprStreamEmbeddedStringPool(data);
        }
    });

    if(esxbArray.length !== 1) {
        throw new Error("Expected a single expr");
    }

    return esxbArray[0]!;
}


async function run_test_case(esxbFile: string): Promise<void> {
    const esxbData: Uint8Array = await fs.readFile(esxbFile);
    const expr = await decodeBin(arrayToAsyncIterable([ esxbData ]));

    const json: ESExprJson = JSON.parse(
        await fs.readFile(esxbFile.substring(0, esxbFile.length - 4) + "json", { encoding: "utf-8" })
    );

    expect(valuesEqual(json2esexpr(json), expr));
    expect(valuesEqual(await decodeBin(encodeBin(expr)), expr));
    
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




