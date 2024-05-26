import { expect, test } from "vitest";
import * as esexpr from "./index.js";
import { ESExpr } from "./index.js";
var BasicRecord;
(function (BasicRecord) {
    BasicRecord.codec = esexpr.recordCodec("basic-record", {
        a: esexpr.positionalFieldCodec(esexpr.float64Codec),
    });
})(BasicRecord || (BasicRecord = {}));
test("Basic record", () => {
    expect(BasicRecord.codec.tags).toEqual(new Set(["basic-record"]));
    expectCodecMatch(BasicRecord.codec, { type: "constructor", name: "basic-record", args: [4], kwargs: new Map() }, { a: 4 });
});
var BasicEnum;
(function (BasicEnum) {
    BasicEnum.codec = esexpr.enumCodec({
        "my-name123-test": esexpr.caseCodec({
            a: esexpr.positionalFieldCodec(esexpr.float64Codec),
        }),
        "my-name456-test": esexpr.caseCodec({
            b: esexpr.positionalFieldCodec(esexpr.float64Codec),
        }),
    });
})(BasicEnum || (BasicEnum = {}));
test("Basic enum", () => {
    expect(BasicEnum.codec.tags).toEqual(new Set(["my-name123-test", "my-name456-test"]));
    expectCodecMatch(BasicEnum.codec, { type: "constructor", name: "my-name123-test", args: [4], kwargs: new Map() }, { $type: "my-name123-test", a: 4 });
    expectCodecMatch(BasicEnum.codec, { type: "constructor", name: "my-name456-test", args: [4], kwargs: new Map() }, { $type: "my-name456-test", b: 4 });
});
var InlineValueTest;
(function (InlineValueTest) {
    InlineValueTest.codec = esexpr.enumCodec({
        "flag": esexpr.inlineCaseCodec("value", esexpr.boolCodec),
        "normal-case": esexpr.caseCodec({
            value: esexpr.positionalFieldCodec(esexpr.boolCodec),
        }),
    });
})(InlineValueTest || (InlineValueTest = {}));
function expectCodecMatch(codec, expr, value, encExpr) {
    expect(codec.encode(value)).toEqual(encExpr ?? expr);
    expect(codec.decode(expr)).toEqual({ success: true, value });
}
function expectDecodeFailure(codec, expr) {
    expect(codec.decode(expr).success).toBe(false);
}
test("Inline value", () => {
    expect(InlineValueTest.codec.tags).toEqual(new Set([Boolean, "normal-case"]));
    expectCodecMatch(InlineValueTest.codec, true, { $type: "flag", value: true });
    expectCodecMatch(InlineValueTest.codec, { type: "constructor", name: "normal-case", args: [true], kwargs: new Map() }, { $type: "normal-case", value: true });
});
var KeywordStruct;
(function (KeywordStruct) {
    KeywordStruct.codec = esexpr.recordCodec("keywords", {
        a: esexpr.keywordFieldCodec("a1", esexpr.boolCodec),
        b: esexpr.optionalKeywordFieldCodec("b2", esexpr.boolCodec),
        c: esexpr.defaultKeywordFieldCodec("c3", () => false, esexpr.boolCodec),
    });
})(KeywordStruct || (KeywordStruct = {}));
var KeywordEnum;
(function (KeywordEnum) {
    KeywordEnum.codec = esexpr.enumCodec({
        value: esexpr.caseCodec({
            a: esexpr.keywordFieldCodec("a1", esexpr.boolCodec),
            b: esexpr.optionalKeywordFieldCodec("b2", esexpr.boolCodec),
            c: esexpr.defaultKeywordFieldCodec("c3", () => false, esexpr.boolCodec),
        }),
    });
})(KeywordEnum || (KeywordEnum = {}));
test("Keywords", () => {
    expectCodecMatch(KeywordStruct.codec, { type: "constructor", name: "keywords", args: [], kwargs: new Map([["a1", true], ["b2", true], ["c3", true]]) }, { a: true, b: true, c: true });
    expectCodecMatch(KeywordEnum.codec, { type: "constructor", name: "value", args: [], kwargs: new Map([["a1", true], ["b2", true], ["c3", true]]) }, { $type: "value", a: true, b: true, c: true });
    expectCodecMatch(KeywordStruct.codec, { type: "constructor", name: "keywords", args: [], kwargs: new Map([["a1", true]]) }, { a: true, b: undefined, c: false }, { type: "constructor", name: "keywords", args: [], kwargs: new Map([["a1", true], ["c3", false]]) });
    expectCodecMatch(KeywordEnum.codec, { type: "constructor", name: "value", args: [], kwargs: new Map([["a1", true]]) }, { $type: "value", a: true, b: undefined, c: false }, { type: "constructor", name: "value", args: [], kwargs: new Map([["a1", true], ["c3", false]]) });
});
var SimpleEnum;
(function (SimpleEnum) {
    SimpleEnum.codec = esexpr.simpleEnumCodec({
        a: "a",
        b: "b",
        c: "c",
    });
})(SimpleEnum || (SimpleEnum = {}));
test("Simple enum", () => {
    expectCodecMatch(SimpleEnum.codec, "a", "a");
    expectCodecMatch(SimpleEnum.codec, "b", "b");
    expectCodecMatch(SimpleEnum.codec, "c", "c");
    expectDecodeFailure(SimpleEnum.codec, "d");
});
var ManyArgs;
(function (ManyArgs) {
    ManyArgs.codec = esexpr.recordCodec("many", {
        args: esexpr.varargFieldCodec(esexpr.boolCodec),
        kwargs: esexpr.dictFieldCodec(esexpr.boolCodec),
    });
})(ManyArgs || (ManyArgs = {}));
test("Many args", () => {
    expectCodecMatch(ManyArgs.codec, { type: "constructor", name: "many", args: [true, true, false], kwargs: new Map([["a", true], ["b", true], ["z", false]]) }, { args: [true, true, false], kwargs: new Map([["a", true], ["b", true], ["z", false]]) });
});
//# sourceMappingURL=index.test.js.map