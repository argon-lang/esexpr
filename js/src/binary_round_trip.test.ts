import { test } from "node:test";
import assert from "node:assert/strict";
import fc from "fast-check";
import { ArrayStringPool, readExprStream, writeExpr, writeExprs } from "./binary_format.js";

import { primitives, expressions } from "./expression_arbitraries.js";

async function* rechunk(data: AsyncIterable<Uint8Array>, size: number): AsyncIterable<Uint8Array> {
	const chunks = await Array.fromAsync(data);
	const bytes = new Uint8Array(Buffer.concat(chunks.map(chunk =>
		Buffer.from(chunk.buffer, chunk.byteOffset, chunk.byteLength),
	)));
	// Empty transport chunks should also be harmless.
	yield new Uint8Array();
	for (let offset = 0; offset < bytes.length; offset += size) {
		yield bytes.subarray(offset, offset + size);
	}
	yield new Uint8Array();
}

for (const [name, arbitrary] of Object.entries(primitives)) {
	test(`binary ${name} round trips`, async () => {
		await fc.assert(fc.asyncProperty(arbitrary, async expected => {
			const actual = await Array.fromAsync(readExprStream(writeExprs([expected])));
			assert.deepStrictEqual(actual, [expected]);
		}), { numRuns: 200 });
	});
}

for (const writer of ["writeExpr", "writeExprs"] as const) {
	for (const singleByte of [false, true]) {
		test(`binary recursive streams round trip using ${writer} with ${singleByte ? "single-byte" : "variable-size"} chunks`, async () => {
			await fc.assert(fc.asyncProperty(
				fc.array(expressions(3), { maxLength: 16 }),
				fc.integer({ min: 1, max: 128 }),
				async (expected, chunkSize) => {
					async function* encode(): AsyncIterable<Uint8Array> {
						if (writer === "writeExprs") {
							yield* writeExprs(expected);
						}
						else {
							const pool = new ArrayStringPool();
							for (const expr of expected) {
								yield* writeExpr(expr, pool);
							}
						}
					}
					const actual = await Array.fromAsync(readExprStream(rechunk(encode(), singleByte ? 1 : chunkSize)));
					assert.deepStrictEqual(actual, expected);
				},
			), { numRuns: 200 });
		});
	}
}
