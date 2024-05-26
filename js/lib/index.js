export var ESExpr;
(function (ESExpr) {
    function tagOf(e) {
        switch (typeof e) {
            case "boolean":
                return Boolean;
            case "bigint":
                return BigInt;
            case "string":
                return String;
            case "number":
                return Number;
            case "object":
                if (e === null) {
                    return null;
                }
                if (e instanceof Uint8Array) {
                    return Uint8Array;
                }
                switch (e.type) {
                    case "constructor":
                        return e.name;
                    case "float32":
                        return Math;
                }
        }
    }
    ESExpr.tagOf = tagOf;
    function isConstructor(e) {
        return typeof e === "object" && e !== null && "type" in e && e.type === "constructor";
    }
    ESExpr.isConstructor = isConstructor;
    function isFloat32(e) {
        return typeof e === "object" && e !== null && "type" in e && e.type === "float32";
    }
    ESExpr.isFloat32 = isFloat32;
})(ESExpr || (ESExpr = {}));
export const boolCodec = {
    get tags() {
        return new Set([Boolean]);
    },
    encode(value) {
        return value;
    },
    decode(expr) {
        if (typeof expr === "boolean") {
            return { success: true, value: expr };
        }
        else {
            return { success: false, message: "Expected a boolean" };
        }
    },
};
export const intCodec = {
    get tags() {
        return new Set([BigInt]);
    },
    encode(value) {
        return value;
    },
    decode(expr) {
        if (typeof expr === "bigint") {
            return { success: true, value: expr };
        }
        else {
            return { success: false, message: "Expected a bigint" };
        }
    },
};
class SmallIntCodec {
    constructor(min, max) {
        this.#min = min;
        this.#max = max;
    }
    #min;
    #max;
    get tags() {
        return new Set([BigInt]);
    }
    encode(value) {
        return BigInt(value);
    }
    decode(expr) {
        const n = intCodec.decode(expr);
        if (!n.success) {
            return n;
        }
        if (n.value < this.#min || n.value > this.#max) {
            return {
                success: false,
                message: "Integer value is out of range",
            };
        }
        return {
            success: true,
            value: Number(n.value),
        };
    }
}
class BigIntCodec {
    constructor(min, max) {
        this.#min = min;
        this.#max = max;
    }
    #min;
    #max;
    get tags() {
        return new Set([BigInt]);
    }
    encode(value) {
        return value;
    }
    decode(expr) {
        const n = intCodec.decode(expr);
        if (!n.success) {
            return n;
        }
        if (n.value < this.#min || n.value > this.#max) {
            return {
                success: false,
                message: "Integer value is out of range",
            };
        }
        return {
            success: true,
            value: n.value,
        };
    }
}
export const signedInt8Codec = new SmallIntCodec(-0x80, 0x7F);
export const unsignedInt8Codec = new SmallIntCodec(0, 0xFF);
export const signedInt16Codec = new SmallIntCodec(-0x8000, 0x7FFF);
export const unsignedInt16Codec = new SmallIntCodec(0, 0xFFFF);
export const signedInt32Codec = new SmallIntCodec(-0x80000000, 0x7FFFFFFF);
export const unsignedInt32Codec = new SmallIntCodec(0, 0xFFFFFFFF);
export const signedInt64Codec = new BigIntCodec(-0x8000000000000000n, 0x7fffffffffffffffn);
export const unsignedInt64Codec = new BigIntCodec(0n, 0xffffffffffffffffn);
export const strCodec = {
    get tags() {
        return new Set([String]);
    },
    encode: function (value) {
        return value;
    },
    decode: function (expr) {
        if (typeof expr === "string") {
            return { success: true, value: expr };
        }
        else {
            return { success: false, message: "Expected a string" };
        }
    },
};
export const binaryCodec = {
    get tags() {
        return new Set([Uint8Array]);
    },
    encode: function (value) {
        return value;
    },
    decode: function (expr) {
        if (expr instanceof Uint8Array) {
            return { success: true, value: expr };
        }
        else {
            return { success: false, message: "Expected a binary value" };
        }
    },
};
export const float32Codec = {
    get tags() {
        return new Set([Math]);
    },
    encode: function (value) {
        return value;
    },
    decode: function (expr) {
        if (ESExpr.isFloat32(expr)) {
            return { success: true, value: expr };
        }
        else {
            return { success: false, message: "Expected a float32" };
        }
    },
};
export const float64Codec = {
    get tags() {
        return new Set([Number]);
    },
    encode: function (value) {
        return value;
    },
    decode: function (expr) {
        if (typeof expr === "number") {
            return { success: true, value: expr };
        }
        else {
            return { success: false, message: "Expected a float64" };
        }
    },
};
class ListCodec {
    constructor(itemCodec) {
        this.#itemCodec = itemCodec;
    }
    #itemCodec;
    get tags() {
        return new Set(["list"]);
    }
    encode(value) {
        return {
            type: "constructor",
            name: "list",
            args: value.map(t => this.#itemCodec.encode(t)),
            kwargs: new Map(),
        };
    }
    decode(expr) {
        if (ESExpr.isConstructor(expr) && expr.name === "list") {
            if (expr.kwargs.size > 0) {
                return { success: false, message: "List must not have keyword arguments" };
            }
            const items = [];
            for (const t of expr.args) {
                const res = this.#itemCodec.decode(t);
                if (!res.success) {
                    return res;
                }
            }
            return { success: true, value: items };
        }
        else {
            return { success: false, message: "Expected a list constructor" };
        }
    }
}
export function listCodec(itemCodec) {
    return new ListCodec(itemCodec);
}
class OptionCodec {
    constructor(itemCodec) {
        this.#itemCodec = itemCodec;
    }
    #itemCodec;
    get tags() {
        const tags = new Set();
        tags.add(null);
        for (const tag of this.#itemCodec.tags) {
            tags.add(tag);
        }
        return tags;
    }
    encode(value) {
        if (value === null) {
            return null;
        }
        else {
            return this.#itemCodec.encode(value.value);
        }
    }
    decode(expr) {
        if (expr === null) {
            return { success: true, value: null };
        }
        else {
            const value = this.#itemCodec.decode(expr);
            if (!value.success) {
                return value;
            }
            return { success: true, value: { value: value.value } };
        }
    }
}
export function optionCodec(itemCodec) {
    return new OptionCodec(itemCodec);
}
;
class RecordCodec {
    constructor(constructorName, fields) {
        this.#constructorName = constructorName;
        this.#fields = fields;
    }
    #constructorName;
    #fields;
    get tags() {
        return new Set([this.#constructorName]);
    }
    encode(value) {
        let args = [];
        let kwargs = new Map();
        for (const field of Object.keys(this.#fields)) {
            this.#fields[field].encode(value[field], args, kwargs);
        }
        return {
            type: "constructor",
            name: this.#constructorName,
            args,
            kwargs,
        };
    }
    decode(expr) {
        if (!(typeof expr === "object" && expr !== null && "type" in expr && expr.type == "constructor")) {
            return {
                success: false,
                message: `Expected a constructor of name ${this.#constructorName}`,
            };
        }
        let obj = {};
        let args = [...expr.args];
        let kwargs = new Map(expr.kwargs);
        for (const field of Object.keys(this.#fields)) {
            const result = this.#fields[field].decode(args, kwargs);
            if (!result.success) {
                return result;
            }
            obj[field] = result.value;
        }
        return {
            success: true,
            value: obj,
        };
    }
}
export function recordCodec(constructorName, fields) {
    return new RecordCodec(constructorName, fields);
}
class EnumCodec {
    constructor(cases) {
        this.#cases = cases;
    }
    #cases;
    get tags() {
        const tags = new Set();
        for (const c of Object.keys(this.#cases)) {
            for (const tag of this.#cases[c].tags(c)) {
                tags.add(tag);
            }
        }
        return tags;
    }
    encode(value) {
        const t = value.$type;
        return this.#cases[t].encode(value);
    }
    decode(expr) {
        const tag = ESExpr.tagOf(expr);
        for (const c of Object.keys(this.#cases)) {
            const cc = this.#cases[c];
            if (cc.tags(c).has(tag)) {
                return cc.decode(c, expr);
            }
        }
        return {
            success: false,
            message: "Unexpected tag",
        };
    }
}
export function enumCodec(cases) {
    return new EnumCodec(cases);
}
class SimpleEnumCodec {
    constructor(names) {
        this.#names = names;
    }
    #names;
    get tags() {
        return new Set([String]);
    }
    encode(value) {
        return value;
    }
    decode(expr) {
        if (typeof expr === "string") {
            if (this.#names.indexOf(expr) >= 0) {
                return { success: true, value: expr };
            }
            else {
                return { success: false, message: "Invalid simple enum value" };
            }
        }
        else {
            return { success: false, message: "Simple enum value must be a string" };
        }
    }
}
export function simpleEnumCodec(names) {
    return new SimpleEnumCodec(Object.keys(names));
}
class PositionalFieldCodec {
    constructor(codec) {
        this.#codec = codec;
    }
    #codec;
    get tags() {
        return this.#codec.tags;
    }
    encode(value, args, _kwargs) {
        args.push(this.#codec.encode(value));
    }
    decode(args, _kwargs) {
        const expr = args.shift();
        if (expr === undefined) {
            return { success: false, message: "Not enough arguments" };
        }
        return this.#codec.decode(expr);
    }
}
export function positionalFieldCodec(codec) {
    return new PositionalFieldCodec(codec);
}
class VarargFieldCodec {
    constructor(codec) {
        this.#codec = codec;
    }
    #codec;
    get tags() {
        return this.#codec.tags;
    }
    encode(value, args, _kwargs) {
        for (const a of value) {
            args.push(this.#codec.encode(a));
        }
    }
    decode(args, _kwargs) {
        let result = [];
        for (const e of args) {
            const item = this.#codec.decode(e);
            if (!item.success) {
                return item;
            }
            result.push(item.value);
        }
        args.length = 0;
        return {
            success: true,
            value: result,
        };
    }
}
export function varargFieldCodec(codec) {
    return new VarargFieldCodec(codec);
}
class KeywordFieldCodec {
    constructor(codec, name) {
        this.#codec = codec;
        this.#name = name;
    }
    #codec;
    #name;
    get tags() {
        return this.#codec.tags;
    }
    encode(value, _args, kwargs) {
        kwargs.set(this.#name, this.#codec.encode(value));
    }
    decode(_args, kwargs) {
        const expr = kwargs.get(this.#name);
        if (expr === undefined) {
            return { success: false, message: "Not enough arguments" };
        }
        kwargs.delete(this.#name);
        return this.#codec.decode(expr);
    }
}
class OptionalKeywordFieldCodec {
    constructor(codec, name) {
        this.#codec = codec;
        this.#name = name;
    }
    #codec;
    #name;
    get tags() {
        return this.#codec.tags;
    }
    encode(value, _args, kwargs) {
        if (value !== undefined) {
            kwargs.set(this.#name, this.#codec.encode(value));
        }
    }
    decode(_args, kwargs) {
        const expr = kwargs.get(this.#name);
        if (expr === undefined) {
            return { success: true, value: undefined };
        }
        kwargs.delete(this.#name);
        return this.#codec.decode(expr);
    }
}
class DefaultKeywordFieldCodec {
    constructor(codec, name, defaultValue) {
        this.#codec = codec;
        this.#name = name;
        this.#defaultValue = defaultValue;
    }
    #codec;
    #name;
    #defaultValue;
    get tags() {
        return this.#codec.tags;
    }
    encode(value, _args, kwargs) {
        kwargs.set(this.#name, this.#codec.encode(value));
    }
    decode(_args, kwargs) {
        const expr = kwargs.get(this.#name);
        if (expr === undefined) {
            return { success: true, value: this.#defaultValue() };
        }
        kwargs.delete(this.#name);
        return this.#codec.decode(expr);
    }
}
class DictFieldCodec {
    constructor(codec) {
        this.#codec = codec;
    }
    #codec;
    get tags() {
        return this.#codec.tags;
    }
    encode(value, _args, kwargs) {
        for (const [kw, item] of value) {
            kwargs.set(kw, this.#codec.encode(item));
        }
    }
    decode(_args, kwargs) {
        let result = new Map();
        for (const [kw, item] of kwargs) {
            const decItem = this.#codec.decode(item);
            if (!decItem.success) {
                return decItem;
            }
            result.set(kw, decItem.value);
        }
        kwargs.clear();
        return {
            success: true,
            value: result,
        };
    }
}
export function keywordFieldCodec(name, codec) {
    return new KeywordFieldCodec(codec, name);
}
export function optionalKeywordFieldCodec(name, codec) {
    return new OptionalKeywordFieldCodec(codec, name);
}
export function defaultKeywordFieldCodec(name, defaultValue, codec) {
    return new DefaultKeywordFieldCodec(codec, name, defaultValue);
}
export function dictFieldCodec(codec) {
    return new DictFieldCodec(codec);
}
class CaseCodec {
    constructor(fields) {
        this.#fields = fields;
    }
    #fields;
    tags(caseName) {
        return new Set([caseName]);
    }
    encode(value) {
        return recordCodec(value.$type, this.#fields).encode(value);
    }
    decode(caseName, expr) {
        const res = recordCodec(caseName, this.#fields).decode(expr);
        if (!res.success) {
            return res;
        }
        return {
            success: true,
            value: {
                $type: caseName,
                ...res.value,
            }
        };
    }
}
export function caseCodec(fields) {
    return new CaseCodec(fields);
}
class InlineCaseCodec {
    constructor(field, codec) {
        this.#field = field;
        this.#codec = codec;
    }
    #field;
    #codec;
    tags(_caseName) {
        return this.#codec.tags;
    }
    encode(value) {
        return this.#codec.encode(value[this.#field]);
    }
    decode(caseName, expr) {
        const res = this.#codec.decode(expr);
        if (!res.success) {
            return res;
        }
        const value = {
            $type: caseName,
            [this.#field]: res.value,
        };
        return {
            success: true,
            value,
        };
    }
}
export function inlineCaseCodec(field, codec) {
    return new InlineCaseCodec(field, codec);
}
//# sourceMappingURL=index.js.map