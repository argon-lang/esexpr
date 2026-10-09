import type { ESExpr } from "./index.js";

/** Invalid ESExpr text, including truncated input. */
export class ESExprFormatError extends Error {}

export interface PrintOptions {
	/** Omit for compact output; use spaces or tabs for each nesting level. */
	indentation?: string;
}

type Width = 16 | 32 | 64;

function floatLayout(width: Width): { sign: bigint, exponent: bigint, payload: bigint } {
	const fraction = width === 16 ? 10n : width === 32 ? 23n : 52n;
	const sign = 1n << BigInt(width - 1);
	const payload = (1n << fraction) - 1n;
	return { sign, exponent: (sign - 1n) ^ payload, payload };
}

function floatFromBits(width: Width, bits: bigint): ESExpr {
	const data = new DataView(new ArrayBuffer(width / 8));
	if(width === 16) {
		data.setUint16(0, Number(bits));
		const value = data.getFloat16(0);
		return Number.isNaN(value) ? { type: "float16-nan", bits: Number(bits) } : { type: "float16", value };
	}
	else if(width === 32) {
		data.setUint32(0, Number(bits));
		const value = data.getFloat32(0);
		return Number.isNaN(value) ? { type: "float32-nan", bits: Number(bits) } : { type: "float32", value };
	}
	else {
		data.setBigUint64(0, bits);
		const value = data.getFloat64(0);
		return Number.isNaN(value) ? { type: "float64-nan", bits } : value;
	}
}

function floatValue(width: Width, value: number): ESExpr {
	if(width === 16) return { type: "float16", value: Math.f16round(value) };
	if(width === 32) return { type: "float32", value: Math.fround(value) };
	return value;
}

// Convert a hexadecimal significand times a power of two without overflowing an
// intermediate Number or rounding away bits before the target precision is known.
function hexFloat(digits: string, exponent: bigint, width: Width, negative: boolean): ESExpr {
	let significand = BigInt("0x" + digits);
	const precision = width === 16 ? 11 : width === 32 ? 24 : 53;
	const maxExponent = width === 16 ? 15 : width === 32 ? 127 : 1023;
	const minExponent = 1 - maxExponent;
	const bitLength = significand === 0n ? 0 : significand.toString(2).length;
	const leadingExponent = BigInt(bitLength - 1) + exponent;
	if(significand === 0n || leadingExponent < BigInt(minExponent - precision)) {
		return floatValue(width, negative ? -0 : 0);
	}
	if(leadingExponent > BigInt(maxExponent)) {
		return floatValue(width, negative ? -Infinity : Infinity);
	}
	const unitExponent = leadingExponent < BigInt(minExponent)
		? BigInt(minExponent - precision + 1)
		: leadingExponent - BigInt(precision - 1);
	const shift = unitExponent - exponent;
	if(shift > 0n) {
		const truncated = significand >> shift;
		const remainder = significand - (truncated << shift);
		const half = 1n << (shift - 1n);
		significand = truncated + (remainder > half || (remainder === half && (truncated & 1n) !== 0n) ? 1n : 0n);
	}
	else {
		significand <<= -shift;
	}
	const value = Number(significand) * 2 ** Number(unitExponent);
	return floatValue(width, negative ? -value : value);
}

function parseInteger(token: string): bigint | undefined {
	if(!/^[+-]?(?:0[xX][0-9a-fA-F]+|0[bB][01]+|0o[0-7]+|[0-9]+)$/.test(token)) return undefined;
	const negative = token.startsWith("-");
	const unsigned = token.replace(/^[+-]/, "");
	const value = BigInt(/^[0-9]+$/.test(unsigned) ? unsigned : unsigned.toLowerCase());
	return negative ? -value : value;
}

// Rust parses f32 directly, and f16 through f32. Parsing through a JS Number
// first can round twice at an f32 midpoint, so round the decimal ratio exactly.
function decimalFloat32(literal: string): number {
	const negative = literal.startsWith("-");
	const [mantissa, exponentText] = literal.replace(/^[+-]/, "").toLowerCase().split("e");
	const digits = mantissa!.replace(".", "").replace(/^0+/, "");
	const exponent = BigInt(exponentText ?? "0") - BigInt(mantissa!.length - mantissa!.indexOf(".") - 1);
	const order = BigInt(digits.length) + exponent;
	if(digits === "" || order < -46n) return negative ? -0 : 0;
	if(order > 39n) return negative ? -Infinity : Infinity;
	let numerator = BigInt(digits);
	let denominator = 1n;
	if(exponent >= 0n) numerator *= 10n ** exponent;
	else denominator = 10n ** -exponent;
	let leadingExponent = numerator.toString(2).length - denominator.toString(2).length;
	if(leadingExponent >= 0 ? numerator < (denominator << BigInt(leadingExponent)) : (numerator << BigInt(-leadingExponent)) < denominator) {
		--leadingExponent;
	}
	const unitExponent = Math.max(leadingExponent, -126) - 23;
	if(unitExponent >= 0) denominator <<= BigInt(unitExponent);
	else numerator <<= BigInt(-unitExponent);
	let significand = numerator / denominator;
	const remainder = numerator % denominator;
	if(remainder * 2n > denominator || (remainder * 2n === denominator && (significand & 1n) !== 0n)) ++significand;
	const value = Math.fround(Number(significand) * 2 ** unitExponent);
	return negative ? -value : value;
}

function parseAtom(token: string): ESExpr {
	if(token === "#true") return true;
	if(token === "#false") return false;
	if(/^#null[0-9]*$/.test(token)) {
		const level = BigInt(token.slice(5) || "0");
		return level === 0n ? null : { type: "null", level };
	}
	const special = /^#float(16|32|64):([+-]?)(nan)(?::([0-9]+))?$/.exec(token);
	// Infinity requires a sign; NaN permits an optional sign and payload.
	const infinity = /^#float(16|32|64):([+-])inf$/.exec(token);
	if(infinity) return floatValue(Number(infinity[1]) as Width, infinity[2] === "-" ? -Infinity : Infinity);
	if(special && special[3] === "nan") {
		const width = Number(special[1]) as Width;
		const layout = floatLayout(width);
		const payload = special[4] === undefined ? (layout.payload + 1n) >> 1n : BigInt(special[4]);
		if(payload > layout.payload) throw new ESExprFormatError("Invalid NaN payload");
		return floatFromBits(width, layout.exponent | payload | (special[2] === "-" ? layout.sign : 0n));
	}
	const decimal = /^[+-]?[0-9]+\.[0-9]+(?:[eE][+-]?[0-9]+)?(f16|F16|f|F|d|D)?$/.exec(token);
	if(decimal) {
		const suffix = decimal[1] ?? "";
		const width = suffix.toLowerCase() === "f16" ? 16 : suffix.toLowerCase() === "f" ? 32 : 64;
		const literal = suffix ? token.slice(0, -suffix.length) : token;
		return floatValue(width, width === 64 ? Number(literal) : decimalFloat32(literal));
	}
	const hex = /^([+-]?)0[xX]([0-9a-fA-F]+)\.([0-9a-fA-F]+)[pP]([+-]?[0-9]+)(f16|F16|f|F|d|D)?$/.exec(token);
	if(hex) {
		const suffix = (hex[5] ?? "").toLowerCase();
		const result = hexFloat(hex[2]! + hex[3]!, BigInt(hex[4]!) - BigInt(hex[3]!.length * 4), suffix === "f16" || suffix === "f" ? 32 : 64, hex[1] === "-");
		// The Rust implementation converts hexadecimal f16 literals through f32.
		return suffix === "f16" ? floatValue(16, (result as ESExpr.Float32).value) : result;
	}
	const integer = parseInteger(token);
	if(integer !== undefined) return integer;
	throw new ESExprFormatError("Invalid expression: " + token);
}

class CharacterReader {
	constructor(data: AsyncIterable<string>) {
		this.#input = data[Symbol.asyncIterator]();
	}

	readonly #input: AsyncIterator<string>;
	#chunk = "";
	#index = 0;
	#done = false;

	async peek(): Promise<string | undefined> {
		while(this.#index === this.#chunk.length && !this.#done) {
			const next = await this.#input.next();
			this.#done = next.done === true;
			this.#chunk = next.done ? "" : next.value;
			this.#index = 0;
		}
		return this.#done ? undefined : this.#chunk[this.#index];
	}

	async take(): Promise<string> {
		const char = await this.peek();
		if(char === undefined) throw new ESExprFormatError("Unexpected end of input");
		++this.#index;
		return char;
	}

	async expect(expected: string): Promise<void> {
		if(await this.take() !== expected) throw new ESExprFormatError("Expected " + expected);
	}
}

/** Incremental text reader. Input chunks may split tokens, escapes, or surrogate pairs. */
export class ExprReader {
	constructor(data: AsyncIterable<string>) {
		this.#chars = new CharacterReader(data);
	}

	readonly #chars: CharacterReader;

	async #skipWhitespace(): Promise<void> {
		for(;;) {
			const char = await this.#chars.peek();
			if(char !== undefined && /[ \t\r\n]/.test(char)) {
				await this.#chars.take();
			}
			else if(char === "/") {
				await this.#chars.take();
				await this.#chars.expect("/");
				while(await this.#chars.take() !== "\n") {
					// Rust comments end at a newline, including at the end of a file.
				}
			}
			else return;
		}
	}

	async #quoted(quote: string): Promise<string> {
		await this.#chars.expect(quote);
		let result = "";
		for(;;) {
			const char = await this.#chars.take();
			if(char === quote) {
				if(!result.isWellFormed()) throw new ESExprFormatError("Lone surrogate in string");
				return result;
			}
			if(char !== "\\") {
				result += char;
				continue;
			}
			const escape = await this.#chars.take();
			const escapes: Record<string, string> = { "f": "\f", "n": "\n", "r": "\r", "t": "\t", "\\": "\\", "\"": "\"", "'": "'" };
			if(escape in escapes) result += escapes[escape];
			else if(escape === "u") {
				await this.#chars.expect("{");
				let digits = "";
				while(await this.#chars.peek() !== "}") digits += await this.#chars.take();
				await this.#chars.take();
				if(!/^[0-9a-fA-F]+$/.test(digits)) throw new ESExprFormatError("Invalid Unicode escape");
				const codepoint = Number.parseInt(digits, 16);
				if(codepoint > 0x10FFFF || (codepoint >= 0xD800 && codepoint <= 0xDFFF)) {
					throw new ESExprFormatError("Invalid Unicode codepoint");
				}
				result += String.fromCodePoint(codepoint);
			}
			else throw new ESExprFormatError("Invalid escape: " + escape);
		}
	}

	async #token(colons = false): Promise<string> {
		let token = "";
		for(;;) {
			const char = await this.#chars.peek();
			if(char === undefined || /[ \t\r\n()[\]"'/#]/.test(char) || (!colons && char === ":")) return token;
			token += await this.#chars.take();
		}
	}

	async #identifier(): Promise<string> {
		await this.#skipWhitespace();
		if(await this.#chars.peek() === "'") return this.#quoted("'");
		const name = await this.#token();
		if(!/^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(name)) throw new ESExprFormatError("Invalid identifier: " + name);
		return name;
	}

	async #array(width: number): Promise<ESExpr> {
		await this.#chars.expect("[");
		const values: bigint[] = [];
		for(;;) {
			await this.#skipWhitespace();
			if(await this.#chars.peek() === "]") {
				await this.#chars.take();
				break;
			}
			const value = parseInteger(await this.#token());
			if(value === undefined || value < 0n || value >= (1n << BigInt(width))) {
				throw new ESExprFormatError("Invalid unsigned array element");
			}
			values.push(value);
		}
		if(width === 8) return new Uint8Array(values.map(Number));
		if(width === 16) return new Uint16Array(values.map(Number));
		if(width === 32) return new Uint32Array(values.map(Number));
		if(width === 64) return new BigUint64Array(values);
		const bytes = new Uint8Array(values.length * 16);
		for(const [index, value] of values.entries()) {
			for(let byte = 0; byte < 16; ++byte) bytes[index * 16 + byte] = Number((value >> BigInt(byte * 8)) & 255n);
		}
		return { type: "array128", value: bytes };
	}

	async #expression(): Promise<ESExpr> {
		await this.#skipWhitespace();
		const char = await this.#chars.peek();
		if(char === undefined) throw new ESExprFormatError("Unexpected end of input");
		if(char === "\"") return this.#quoted("\"");
		if(char === "(") {
			await this.#chars.take();
			const name = await this.#identifier();
			const args: ESExpr[] = [];
			const kwargs = new Map<string, ESExpr>();
			for(;;) {
				await this.#skipWhitespace();
				const next = await this.#chars.peek();
				if(next === ")") {
					await this.#chars.take();
					return { type: "constructor", name, args, kwargs };
				}
				if(next === "'" || (next !== undefined && /^[a-z]$/.test(next))) {
					const key = await this.#identifier();
					await this.#skipWhitespace();
					await this.#chars.expect(":");
					kwargs.set(key, await this.#expression());
				}
				else args.push(await this.#expression());
			}
		}
		if(char === "#") {
			await this.#chars.take();
			if(await this.#chars.peek() === "\"") {
				await this.#chars.take();
				let hex = "";
				while(await this.#chars.peek() !== "\"") hex += await this.#chars.take();
				await this.#chars.take();
				if(!/^(?:[0-9a-fA-F]{2})*$/.test(hex)) throw new ESExprFormatError("Invalid hexadecimal byte array");
				return new Uint8Array(hex.match(/../g)?.map(byte => Number.parseInt(byte, 16)) ?? []);
			}
			const token = "#" + await this.#token(true);
			const array = /^#u(8|16|32|64|128)$/.exec(token);
			if(array) return this.#array(Number(array[1]));
			return parseAtom(token);
		}
		return parseAtom(await this.#token());
	}

	async tryReadExpr(): Promise<ESExpr | undefined> {
		await this.#skipWhitespace();
		return await this.#chars.peek() === undefined ? undefined : this.#expression();
	}

	async readExpr(): Promise<ESExpr> {
		const expr = await this.tryReadExpr();
		if(expr === undefined) throw new ESExprFormatError("Unexpected end of input");
		return expr;
	}

	async* readAll(): AsyncIterable<ESExpr> {
		for(;;) {
			const expr = await this.tryReadExpr();
			if(expr === undefined) return;
			yield expr;
		}
	}
}

export function readExprStream(data: AsyncIterable<string>): AsyncIterable<ESExpr> {
	return new ExprReader(data).readAll();
}

function quoted(value: string, quote: string): string {
	let output = quote;
	for(const char of value) {
		const codepoint = char.codePointAt(0)!;
		if(codepoint >= 0xD800 && codepoint <= 0xDFFF) throw new ESExprFormatError("Lone surrogate in string");
		if(char === quote || char === "\\") output += "\\" + char;
		else if(char === "\n") output += "\\n";
		else if(char === "\r") output += "\\r";
		else if(char === "\t") output += "\\t";
		else if(char === "\f") output += "\\f";
		else if(codepoint < 32 || (codepoint >= 127 && codepoint <= 159)) output += "\\u{" + codepoint.toString(16) + "}";
		else output += char;
	}
	return output + quote;
}

function identifier(name: string): string {
	return /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(name) ? name : quoted(name, "'");
}

function printFloat(width: Width, value: number): string {
	if(!Number.isFinite(value)) {
		if(Number.isNaN(value)) return `#float${width}:+nan:${(floatLayout(width).payload + 1n) >> 1n}`;
		return `#float${width}:${value < 0 ? "-" : "+"}inf`;
	}
	let text = Object.is(value, -0) ? "-0" : String(value);
	if(!text.includes(".")) text = text.replace(/(?=e|$)/, ".0");
	return text + (width === 16 ? "f16" : width === 32 ? "f" : "d");
}

function printNaN(width: Width, bits: bigint): string {
	const layout = floatLayout(width);
	if(bits < 0n || bits >= (1n << BigInt(width)) || (bits & layout.exponent) !== layout.exponent || (bits & layout.payload) === 0n) {
		throw new ESExprFormatError("Invalid NaN bits");
	}
	return `#float${width}:${(bits & layout.sign) === 0n ? "+" : "-"}nan:${bits & layout.payload}`;
}

/** Write a single expression, with Rust-compatible syntax and sorted keywords. */
export async function* writeExpr(expr: ESExpr, options: PrintOptions = {}): AsyncIterable<string> {
	const indentation = options.indentation;
	if(indentation !== undefined && !/^(?: *|\t*)$/.test(indentation)) throw new ESExprFormatError("Indentation must be spaces or tabs");
	function* write(expr: ESExpr, depth: number): Iterable<string> {
		const array = (width: number, values: Iterable<number | bigint>) => `#u${width}[${[...values].join(" ")}]`;
		if(expr === null) yield "#null";
		else if(typeof expr === "boolean") yield expr ? "#true" : "#false";
		else if(typeof expr === "bigint") yield String(expr);
		else if(typeof expr === "string") yield quoted(expr, "\"");
		else if(typeof expr === "number") yield printFloat(64, expr);
		else if(expr instanceof Uint8Array) yield "#\"" + [...expr].map(byte => byte.toString(16).padStart(2, "0")).join("") + "\"";
		else if(expr instanceof Uint16Array) yield array(16, expr);
		else if(expr instanceof Uint32Array) yield array(32, expr);
		else if(expr instanceof BigUint64Array) yield array(64, expr);
		else {
			switch(expr.type) {
				case "constructor": {
					yield "(" + identifier(expr.name);
					const separator = indentation === undefined ? " " : "\n" + indentation.repeat(depth + 1);
					for(const arg of expr.args) {
						yield separator;
						yield* write(arg, depth + 1);
					}
					// Rust compares Unicode scalar values, rather than UTF-16 code units.
					const keys = [...expr.kwargs.keys()].sort(compareNames);
					for(const key of keys) {
						yield separator + identifier(key) + ": ";
						yield* write(expr.kwargs.get(key)!, depth + 1);
					}
					if(indentation !== undefined && (expr.args.length > 0 || keys.length > 0)) yield "\n" + indentation.repeat(depth);
					yield ")";
					break;
				}
				case "float16": yield printFloat(16, Math.f16round(expr.value));
					break;
				case "float32": yield printFloat(32, Math.fround(expr.value));
					break;
				case "float16-nan": yield printNaN(16, BigInt(expr.bits));
					break;
				case "float32-nan": yield printNaN(32, BigInt(expr.bits));
					break;
				case "float64-nan": yield printNaN(64, expr.bits);
					break;
				case "null":
					if(expr.level < 0n) throw new ESExprFormatError("Negative null level");
					yield expr.level === 0n ? "#null" : "#null" + expr.level;
					break;
				case "array128": {
					if(expr.value.length % 16 !== 0) throw new ESExprFormatError("Invalid array128 byte length");
					const values: bigint[] = [];
					for(let offset = 0; offset < expr.value.length; offset += 16) {
						let value = 0n;
						for(let byte = 15; byte >= 0; --byte) value = (value << 8n) | BigInt(expr.value[offset + byte]!);
						values.push(value);
					}
					yield array(128, values);
					break;
				}
			}
		}
	}
	yield* write(expr, 0);
}

function compareNames(a: string, b: string): number {
	const left = [...a];
	const right = [...b];
	for(let i = 0; i < Math.min(left.length, right.length); ++i) {
		const diff = left[i]!.codePointAt(0)! - right[i]!.codePointAt(0)!;
		if(diff !== 0) return diff;
	}
	return left.length - right.length;
}

/** Write an expression stream, separating expressions with newlines. */
export async function* writeExprs(exprs: AsyncIterable<ESExpr> | Iterable<ESExpr>, options: PrintOptions = {}): AsyncIterable<string> {
	for await (const expr of exprs) {
		yield* writeExpr(expr, options);
		yield "\n";
	}
}
