
export const hashSymbol: symbol = Symbol.for("esexpr-hash");
export const equalsSymbol: symbol = Symbol.for("esexpr-equals");

export function hash(value: unknown): number {
    switch(typeof value) {
        case "boolean":
            return hashBoolean(value);

        case "number":
            return hashNumber(value);

        case "bigint":
            return hashBigInt(value);

        case "string":
            return hashString(value);

        case "object":
            if(value === null) return 0;
            else if(Array.isArray(value)) {
                return hashArray(value);
            }
            else if(ArrayBuffer.isView(value)) {
                return hashBufferView(value);
            }
            else if(hashSymbol in value) {
                return (value as any)[hashSymbol]();
            }
            else if(isSimpleObject(value)) {
                return hashObject(value);
            }
            else {
                return hashIdentity(value);
            }

        case "function":
        case "symbol":
            return hashIdentity(value);

        case "undefined":
            return 0;
    }
}

export function equals(a: unknown, b: unknown): boolean {
    if(Object.is(a, b)) {
        return true;
    }
    else if(a === null || b === null) {
        return false;
    }
    else if(typeof a !== "object" || typeof b !== "object") {
        return false;
    }
    else if(Array.isArray(a)) {
        return Array.isArray(b) && arrayEquals(a, b);
    }
    else if(ArrayBuffer.isView(a)) {
        return ArrayBuffer.isView(b) &&
            Object.getPrototypeOf(a) === Object.getPrototypeOf(b) &&
            arrayBufferViewEquals(a, b);
    }
    else if(equalsSymbol in a) {
        return (a as any)[equalsSymbol](b);
    }
    else if(isSimpleObject(a)) {
        return isSimpleObject(b) && objectEquals(a, b);
    }
    else {
        return false;
    }
}

function hashBoolean(b: boolean): number {
    return b ? 1231 : 1237;
}

function hashNumber(x: number): number {
    if (x === (x | 0) || x === (x >>> 0)) return x | 0;

    const buf = new ArrayBuffer(8);
    const view = new DataView(buf);
    view.setFloat64(0, x, true);

    let h = view.getUint32(0) ^ view.getUint32(4);

    h ^= h >>> 16;
    h = Math.imul(h, 0x85ebca6b);
    h ^= h >>> 13;
    h = Math.imul(h, 0xc2b2ae35);
    h ^= h >>> 16;

    return h >>> 0;
}


function hashBigInt(x: bigint): number {
    if (x >= -0x80000000n && x <= 0xffffffffn) {
        return Number(BigInt.asIntN(32, x));
    }

    let h = 0;
    let v = x < 0n ? -x : x; // ignore sign initially
    let sign = x < 0n ? 1 : 0;

    while (v !== 0n) {
        const limb = Number(BigInt.asUintN(32, v));
        v >>= 32n;

        // mix limb
        h ^= limb;
        h = Math.imul(h, 0x85ebca6b);
        h ^= h >>> 13;
    }

    if (sign) {
        h ^= 0x9e3779b9;
    }

    h ^= h >>> 16;
    h = Math.imul(h, 0xc2b2ae35);
    h ^= h >>> 16;

    return h >>> 0;
}


function hashString(str: string): number {
    let h = 0;
    const len = str.length;

    let i = 0;
    while (i + 1 < len) {
        const k = str.charCodeAt(i) | (str.charCodeAt(i + 1) << 16);
        i += 2;

        h ^= k;
        h = Math.imul(h, 0x85ebca6b);
        h ^= h >>> 13;
    }

    if (i < len) {
        h ^= str.charCodeAt(i);
        h = Math.imul(h, 0x85ebca6b);
    }

    h ^= h >>> 16;
    h = Math.imul(h, 0xc2b2ae35);
    h ^= h >>> 16;

    return h >>> 0;
}

const identityHashMap = new WeakMap<symbol | object, number>();
function hashIdentity(x: symbol | object): number {
    let hash = identityHashMap.get(x);
    if(hash === undefined) {
        hash = (Math.random() * 0xFFFFFFFF) | 0;
        identityHashMap.set(x, hash);
    }
    return hash;
}

function hashArray(arr: Readonly<ArrayLike<unknown>>) {
    let h = 0x811c9dc5;

    for (let i = 0; i < arr.length; i++) {
        const eh = hash(arr[i]);

        h ^= eh;
        h = Math.imul(h, 0x85ebca6b);
        h ^= h >>> 13;
    }

    h ^= arr.length;
    h = Math.imul(h, 0xc2b2ae35);
    h ^= h >>> 16;

    return h >>> 0;
}

function hashBufferView(bv: ArrayBufferView): number {
    // View raw bytes
    const bytes = new Uint8Array(
        bv.buffer,
        bv.byteOffset,
        bv.byteLength
    );

    let h = hashBytes(bytes);

    h ^= hashString(bv.constructor.name);
    h = Math.imul(h, 0x85ebca6b);

    return h >>> 0;
}

function hashBytes(bytes: Uint8Array) {
    let h = 0;

    const len = bytes.length;
    let i = 0;

    // process 4 bytes at a time
    while (i + 3 < len) {
        const k =
            bytes[i]! |
            (bytes[i + 1]! << 8) |
            (bytes[i + 2]! << 16) |
            (bytes[i + 3]! << 24);
        i += 4;

        h ^= k;
        h = Math.imul(h, 0x85ebca6b);
        h ^= h >>> 13;
    }

    // tail
    while (i < len) {
        h ^= bytes[i++]!;
        h = Math.imul(h, 0x85ebca6b);
    }

    return h | 0;
}

function isSimpleObject(obj: object): boolean {
    const proto = Object.getPrototypeOf(obj);
    return proto === Object.prototype || proto === null;
}

function hashObject(obj: any): number {
    let h = 0;

    const keys = Reflect.ownKeys(obj);

    for (const k of keys) {
        const kh = hash(k);
        const vh = hash(obj[k]);

        // order-independent pair mixing
        let ph = kh ^ vh;
        ph = Math.imul(ph, 0x9e3779b9);
        ph ^= ph >>> 16;

        h += ph;
    }

    // mix in key count
    h ^= keys.length;
    h = Math.imul(h, 0x85ebca6b);
    h ^= h >>> 16;

    return h | 0;
}


function arrayEquals(a: Readonly<ArrayLike<unknown>>, b: Readonly<ArrayLike<unknown>>): boolean {
    if(a.length !== b.length) return false;
    for(let i = 0; i < a.length; ++i) {
        if(!equals(a[i], b[i])) return false;
    }
    return true;
}

function arrayBufferViewEquals(a: Readonly<ArrayBufferView>, b: Readonly<ArrayBufferView>): boolean {
    if(a.byteLength !== b.byteLength) return false;
    if(Object.getPrototypeOf(a) !== Object.getPrototypeOf(b)) return false;

    const aBytes = new Uint8Array(a.buffer, a.byteOffset, a.byteLength);
    const bBytes = new Uint8Array(b.buffer, b.byteOffset, b.byteLength);

    for(let i = 0; i < a.byteLength; ++i) {
        if(aBytes[i]! !== bBytes[i]!) return false;
    }

    return true;
}

function objectEquals(a: any, b: any): boolean {
    const aKeys = Reflect.ownKeys(a);
    const bKeys = Reflect.ownKeys(b);

    for(const k of aKeys) {
        if(!equals(a[k], b[k])) return false;
    }

    for(const k of bKeys) {
        if(b[k] === undefined) continue;
        if(a[k] === undefined) return false;
    }

    return true;
}

