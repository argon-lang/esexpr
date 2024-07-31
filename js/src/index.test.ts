import { expect, test } from "vitest";
import * as esexpr from "./index.js";
import { ESExpr, type ESExprCodec } from "./index.js";



function expectCodecMatch<T>(codec: ESExprCodec<T>, expr: ESExpr, value: T): void {
    expect(codec.encode(value)).toEqual(expr);
    expect(codec.decode(expr)).toEqual({ success: true, value });
}

function expectDecodeFailure<T>(codec: ESExprCodec<T>, expr: ESExpr): void {
    expect(codec.decode(expr).success).toBe(false);
}

interface BasicRecord {
    a: number;
}

namespace BasicRecord {
    export const codec: ESExprCodec<BasicRecord> = esexpr.recordCodec("basic-record", {
        a: esexpr.positionalFieldCodec(esexpr.float64Codec),
    });
}

test("Basic record", () => {
    expect(BasicRecord.codec.tags).toEqual(new Set(["basic-record"]));
    expectCodecMatch(
        BasicRecord.codec,
        { type: "constructor", name: "basic-record", args: [ 4 ], kwargs: new Map() },
        { a: 4 },
    );

    expectDecodeFailure(
        BasicRecord.codec,
        { type: "constructor", name: "bad-name", args: [ 4 ], kwargs: new Map() },
    );
});

type BasicEnum =
    | { readonly $type: "my-name123-test", a: number }
    | { readonly $type: "my-name456-test", b: number }
;

namespace BasicEnum {
    export const codec: ESExprCodec<BasicEnum> = esexpr.enumCodec({
        "my-name123-test": esexpr.caseCodec("my-name123-test", {
            a: esexpr.positionalFieldCodec(esexpr.float64Codec),
        }),
        "my-name456-test": esexpr.caseCodec("my-name456-test2", {
            b: esexpr.positionalFieldCodec(esexpr.float64Codec),
        }),
    });
}

test("Basic enum", () => {
    expect(BasicEnum.codec.tags).toEqual(new Set(["my-name123-test", "my-name456-test2"]));
    expectCodecMatch(
        BasicEnum.codec,
        { type: "constructor", name: "my-name123-test", args: [ 4 ], kwargs: new Map() },
        { $type: "my-name123-test", a: 4 },
    );
    expectCodecMatch(
        BasicEnum.codec,
        { type: "constructor", name: "my-name456-test2", args: [ 4 ], kwargs: new Map() },
        { $type: "my-name456-test", b: 4 },
    );
});




type InlineValueTest =
    | { readonly $type: "flag", readonly value: boolean }
    | { readonly $type: "normal-case", readonly value: boolean }
;

namespace InlineValueTest {
    export const codec: ESExprCodec<InlineValueTest> = esexpr.enumCodec({
        "flag": esexpr.inlineCaseCodec("value", esexpr.boolCodec),
        "normal-case": esexpr.caseCodec("normal-case", {
            value: esexpr.positionalFieldCodec(esexpr.boolCodec),
        }),
    });
}


test("Inline value", () => {
    expect(InlineValueTest.codec.tags).toEqual(new Set([Boolean, "normal-case"]));
    expectCodecMatch(
        InlineValueTest.codec,
        true,
        { $type: "flag", value: true },
    );

    expectCodecMatch(
        InlineValueTest.codec,
        { type: "constructor", name: "normal-case", args: [ true ], kwargs: new Map() },
        { $type: "normal-case", value: true },
    );
});


interface Positional1 {
    readonly a: boolean,
    readonly b: boolean,
    readonly c?: boolean | undefined,
}

namespace Positional1 {
    export const codec: ESExprCodec<Positional1> = esexpr.recordCodec("args", {
        a: esexpr.positionalFieldCodec(esexpr.boolCodec),
        b: esexpr.positionalFieldCodec(esexpr.boolCodec),
        c: esexpr.optionalPositionalFieldCodec(esexpr.undefinedOptionalCodec(esexpr.boolCodec)),
    });
}

interface Positional2 {
    readonly a: boolean,
    readonly b?: boolean | undefined,
    readonly c?: boolean | undefined,
}

namespace Positional2 {
    export const codec: ESExprCodec<Positional2> = esexpr.recordCodec("args", {
        a: esexpr.positionalFieldCodec(esexpr.boolCodec),
        b: esexpr.optionalPositionalFieldCodec(esexpr.undefinedOptionalCodec(esexpr.boolCodec)),
        c: esexpr.optionalPositionalFieldCodec(esexpr.undefinedOptionalCodec(esexpr.boolCodec)),
    });
}

test("Optional Positional", () => {
    expectCodecMatch(
        Positional1.codec,
        { type: "constructor", name: "args", args: [ true, false ], kwargs: new Map(), },
        { a: true, b: false, c: undefined }
    );
    expectCodecMatch(
        Positional1.codec,
        { type: "constructor", name: "args", args: [ true, false, true ], kwargs: new Map(), },
        { a: true, b: false, c: true }
    );


    expectCodecMatch(
        Positional2.codec,
        { type: "constructor", name: "args", args: [ true ], kwargs: new Map(), },
        { a: true, b: undefined, c: undefined }
    );
    expectCodecMatch(
        Positional2.codec,
        { type: "constructor", name: "args", args: [ true, false ], kwargs: new Map(), },
        { a: true, b: false, c: undefined }
    );
    expectCodecMatch(
        Positional2.codec,
        { type: "constructor", name: "args", args: [ true, false, true ], kwargs: new Map(), },
        { a: true, b: false, c: true }
    );
});



interface KeywordStruct {
    readonly a: boolean;
    readonly b?: boolean | undefined;
    readonly c: boolean;
}

namespace KeywordStruct {
    export const codec: ESExprCodec<KeywordStruct> = esexpr.recordCodec("keywords", {
        a: esexpr.keywordFieldCodec("a1", esexpr.boolCodec),
        b: esexpr.optionalKeywordFieldCodec("b2", esexpr.undefinedOptionalCodec(esexpr.boolCodec)),
        c: esexpr.defaultKeywordFieldCodec("c3", () => false, esexpr.boolCodec),
    });
}


type KeywordEnum =
    | { readonly $type: "value" } & KeywordStruct
;

namespace KeywordEnum {
    export const codec: ESExprCodec<KeywordEnum> = esexpr.enumCodec<KeywordEnum>({
        value: esexpr.caseCodec("value", {
            a: esexpr.keywordFieldCodec("a1", esexpr.boolCodec),
            b: esexpr.optionalKeywordFieldCodec("b2", esexpr.undefinedOptionalCodec(esexpr.boolCodec)),
            c: esexpr.defaultKeywordFieldCodec("c3", () => false, esexpr.boolCodec),
        }),
    });
}

test("Keywords", () => {
    expectCodecMatch(
        KeywordStruct.codec,
        { type: "constructor", name: "keywords", args: [], kwargs: new Map([ ["a1", true], ["b2", true], ["c3", true] ]) },
        { a: true, b: true, c: true },
    );

    expectCodecMatch(
        KeywordEnum.codec,
        { type: "constructor", name: "value", args: [], kwargs: new Map([ ["a1", true], ["b2", true], ["c3", true] ]) },
        { $type: "value", a: true, b: true, c: true },
    );

    expectCodecMatch(
        KeywordStruct.codec,
        { type: "constructor", name: "keywords", args: [], kwargs: new Map([ ["a1", true] ]) },
        { a: true, b: undefined, c: false },
    );

    expectCodecMatch(
        KeywordEnum.codec,
        { type: "constructor", name: "value", args: [], kwargs: new Map([ ["a1", true] ]) },
        { $type: "value", a: true, b: undefined, c: false },
    );
});

type SimpleEnum = "a" | "b" | "c";

namespace SimpleEnum {
    export const codec: ESExprCodec<SimpleEnum> = esexpr.simpleEnumCodec<SimpleEnum>({
        a: "a",
        b: "b",
        c: "c",
    });
}


test("Simple enum", () => {
    expectCodecMatch(
        SimpleEnum.codec,
        "a",
        "a",
    );
    expectCodecMatch(
        SimpleEnum.codec,
        "b",
        "b",
    );
    expectCodecMatch(
        SimpleEnum.codec,
        "c",
        "c",
    );
    expectDecodeFailure(
        SimpleEnum.codec,
        "d",
    );
});


interface ManyArgs {
    readonly args: readonly boolean[];
    readonly kwargs: ReadonlyMap<string, boolean>;
}

namespace ManyArgs {
    export const codec: ESExprCodec<ManyArgs> = esexpr.recordCodec("many", {
        args: esexpr.varargFieldCodec(esexpr.arrayRepeatedValuesCodec(esexpr.boolCodec)),
        kwargs: esexpr.dictFieldCodec(esexpr.mapMappedValueCodec(esexpr.boolCodec)),
    });
}

test("Many args", () => {
    expectCodecMatch(
        ManyArgs.codec,
        { type: "constructor", name: "many", args: [ true, true, false ], kwargs: new Map([ ["a", true], ["b", true], ["z", false] ]) },
        { args: [ true, true, false ], kwargs: new Map([ ["a", true], ["b", true], ["z", false] ]) },
    );
});


test("list codec", () => {
    expectCodecMatch(
        esexpr.listCodec(esexpr.strCodec),
        { type: "constructor", name: "list", args: [ "a", "b", "c" ], kwargs: new Map(), },
        [ "a", "b", "c" ],
    )
});


