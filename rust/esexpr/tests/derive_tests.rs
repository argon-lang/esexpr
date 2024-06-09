
#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
struct ConstructorName123Conversion {
    a: i32,
}

#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
enum ConstructorNameEnum {
    MyName123Test,

    #[constructor = "my-ctor"]
    CustomName,
}


#[test]
fn constructor_name_conversion() {
    use std::collections::{HashMap, HashSet};
    use num_bigint::BigInt;
    use esexpr::{ESExpr, ESExprTag, ESExprCodec};

    let expr = ESExpr::Constructor {
        name: "constructor-name123-conversion".to_owned(),
        args: vec!(ESExpr::Int(BigInt::from(5))),
        kwargs: HashMap::new(),
    };

    let value = ConstructorName123Conversion {
        a: 5,
    };

    assert_eq!(HashSet::from([ESExprTag::Constructor("constructor-name123-conversion".to_owned())]), ConstructorName123Conversion::tags());
    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, ConstructorName123Conversion::decode_esexpr(expr).unwrap());


    let expr = ESExpr::Constructor {
        name: "bad-name".to_owned(),
        args: vec!(ESExpr::Int(BigInt::from(5))),
        kwargs: HashMap::new(),
    };

    assert!(ConstructorName123Conversion::decode_esexpr(expr).is_err());


    let expr = ESExpr::Constructor {
        name: "my-name123-test".to_owned(),
        args: vec!(),
        kwargs: HashMap::new(),
    };

    let value = ConstructorNameEnum::MyName123Test;

    assert_eq!(HashSet::from([ESExprTag::Constructor("my-name123-test".to_owned()), ESExprTag::Constructor("my-ctor".to_owned())]), ConstructorNameEnum::tags());
    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, ConstructorNameEnum::decode_esexpr(expr).unwrap());


    let expr = ESExpr::Constructor {
        name: "bad-name".to_owned(),
        args: vec!(),
        kwargs: HashMap::new(),
    };

    assert!(ConstructorNameEnum::decode_esexpr(expr).is_err());


}

#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
#[constructor = "my-ctor"]
struct CustomConstructorName {
    a: i32,
}

#[test]
fn custom_constructor_name() {
    use std::collections::HashMap;
    use num_bigint::BigInt;
    use esexpr::{ESExpr, ESExprCodec};

    let expr = ESExpr::Constructor {
        name: "my-ctor".to_owned(),
        args: vec!(ESExpr::Int(BigInt::from(5))),
        kwargs: HashMap::new(),
    };

    let value = CustomConstructorName {
        a: 5,
    };

    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, CustomConstructorName::decode_esexpr(expr).unwrap());

    let expr = ESExpr::Constructor {
        name: "bad-name".to_owned(),
        args: vec!(ESExpr::Int(BigInt::from(5))),
        kwargs: HashMap::new(),
    };

    assert!(ConstructorName123Conversion::decode_esexpr(expr).is_err());


    let expr = ESExpr::Constructor {
        name: "my-ctor".to_owned(),
        args: vec!(),
        kwargs: HashMap::new(),
    };

    let value = ConstructorNameEnum::CustomName;

    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, ConstructorNameEnum::decode_esexpr(expr).unwrap());
}



#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
enum InlineValueTest {
    #[inline_value]
    Flag(bool),

    NormalCase(bool),
}


#[test]
fn inline_value_case() {
    use std::collections::{HashMap, HashSet};
    use esexpr::{ESExpr, ESExprTag, ESExprCodec};

    let expr = ESExpr::Bool(true);
    let value = InlineValueTest::Flag(true);

    assert_eq!(HashSet::from([ESExprTag::Bool, ESExprTag::Constructor("normal-case".to_owned())]), InlineValueTest::tags());
    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, InlineValueTest::decode_esexpr(expr).unwrap());


    let expr = ESExpr::Constructor {
        name: "flag".to_owned(),
        args: vec!(ESExpr::Bool(true)),
        kwargs: HashMap::new(),
    };

    assert!(InlineValueTest::decode_esexpr(expr).is_err());

    let expr = ESExpr::Constructor {
        name: "normal-case".to_owned(),
        args: vec!(ESExpr::Bool(true)),
        kwargs: HashMap::new(),
    };
    let value = InlineValueTest::NormalCase(true);

    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, InlineValueTest::decode_esexpr(expr).unwrap());


}



#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
#[constructor = "keywords"]
struct KeywordStruct {
    #[keyword]
    a: bool,

    #[keyword = "b2"]
    b: bool,

    #[keyword(name = "c2")]
    c: bool,

    #[keyword(name = "d2", required = false)]
    d: Option<bool>,

    #[keyword(required = false, name = "e2")]
    e: Option<bool>,

    #[keyword(required = false)]
    f: Option<bool>,

    #[keyword]
    #[default_value = false]
    g: bool,

    #[keyword]
    h: Option<bool>,
}

#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
enum KeywordEnum {
    #[constructor = "keywords"]
    Value {
        #[keyword]
        a: bool,

        #[keyword = "b2"]
        b: bool,

        #[keyword(name = "c2")]
        c: bool,

        #[keyword(name = "d2", required = false)]
        d: Option<bool>,

        #[keyword(required = false, name = "e2")]
        e: Option<bool>,

        #[keyword(required = false)]
        f: Option<bool>,

        #[keyword]
        #[default_value = false]
        g: bool,

        #[keyword]
        h: Option<bool>,
    },
}


#[test]
fn keyword_args() {
    use std::collections::{HashMap, HashSet};
    use esexpr::{ESExpr, ESExprTag, ESExprCodec};

    let expr = ESExpr::Constructor {
        name: "keywords".to_owned(),
        args: vec!(),
        kwargs: HashMap::from([
            ("a".to_owned(), ESExpr::Bool(true)),
            ("b2".to_owned(), ESExpr::Bool(true)),
            ("c2".to_owned(), ESExpr::Bool(true)),
            ("d2".to_owned(), ESExpr::Bool(true)),
            ("e2".to_owned(), ESExpr::Bool(true)),
            ("f".to_owned(), ESExpr::Bool(true)),
            ("g".to_owned(), ESExpr::Bool(true)),
            ("h".to_owned(), ESExpr::Bool(true)),
        ]),
    };

    let value = KeywordStruct {
        a: true,
        b: true,
        c: true,
        d: Some(true),
        e: Some(true),
        f: Some(true),
        g: true,
        h: Some(true),
    };

    let tags = HashSet::from([ESExprTag::Constructor("keywords".to_owned())]);

    assert_eq!(tags, KeywordStruct::tags());
    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, KeywordStruct::decode_esexpr(expr.clone()).unwrap());

    let value = KeywordEnum::Value {
        a: true,
        b: true,
        c: true,
        d: Some(true),
        e: Some(true),
        f: Some(true),
        g: true,
        h: Some(true),
    };

    assert_eq!(tags, KeywordEnum::tags());
    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, KeywordEnum::decode_esexpr(expr).unwrap());



    let expr = ESExpr::Constructor {
        name: "keywords".to_owned(),
        args: vec!(),
        kwargs: HashMap::from([
            ("a".to_owned(), ESExpr::Bool(true)),
            ("b2".to_owned(), ESExpr::Bool(true)),
            ("c2".to_owned(), ESExpr::Bool(true)),
            ("h".to_owned(), ESExpr::Null),
        ]),
    };

    let value = KeywordStruct {
        a: true,
        b: true,
        c: true,
        d: None,
        e: None,
        f: None,
        g: false,
        h: None,
    };

    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, KeywordStruct::decode_esexpr(expr.clone()).unwrap());



    let value = KeywordEnum::Value {
        a: true,
        b: true,
        c: true,
        d: None,
        e: None,
        f: None,
        g: false,
        h: None,
    };

    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, KeywordEnum::decode_esexpr(expr).unwrap());

    
    let expr = ESExpr::Constructor {
        name: "keywords".to_owned(),
        args: vec!(),
        kwargs: HashMap::from([
            ("a".to_owned(), ESExpr::Bool(true)),
            ("b2".to_owned(), ESExpr::Bool(true)),
            ("c2".to_owned(), ESExpr::Bool(true)),
        ]),
    };

    assert!(KeywordEnum::decode_esexpr(expr).is_err());

}


#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
#[simple_enum]
enum SimpleEnum {
    A,
    B,
    #[constructor = "my-c"]
    C,
}


#[test]
fn simple_enum_test() {
    use std::collections::HashSet;
    use esexpr::{ESExpr, ESExprTag, ESExprCodec};

    let expr = ESExpr::Str("a".to_owned());
    let value = SimpleEnum::A;

    assert_eq!(HashSet::from([ESExprTag::Str]), SimpleEnum::tags());
    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, SimpleEnum::decode_esexpr(expr).unwrap());

    let expr = ESExpr::Str("b".to_owned());
    let value = SimpleEnum::B;

    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, SimpleEnum::decode_esexpr(expr).unwrap());


    let expr = ESExpr::Str("my-c".to_owned());
    let value = SimpleEnum::C;

    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, SimpleEnum::decode_esexpr(expr).unwrap());


    let expr = ESExpr::Str("d".to_owned());

    assert!(ConstructorNameEnum::decode_esexpr(expr).is_err());


}

#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
#[constructor = "many"]
struct ManyArgsStruct {
    #[vararg]
    args: Vec<bool>,

    #[dict]
    kwargs: std::collections::HashMap<String, bool>,
}

#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
enum ManyArgsEnum {
    #[constructor = "many"]
    Value {
        #[vararg]
        args: Vec<bool>,

        #[dict]
        kwargs: std::collections::HashMap<String, bool>,
    },
}


#[test]
fn many_args_test() {
    use std::collections::HashMap;
    use esexpr::{ESExpr, ESExprCodec};

    let expr = ESExpr::Constructor {
        name: "many".to_owned(),
        args: vec!(ESExpr::Bool(true), ESExpr::Bool(true), ESExpr::Bool(false)),
        kwargs: HashMap::from([
            ("a".to_owned(), ESExpr::Bool(true)),
            ("b".to_owned(), ESExpr::Bool(true)),
            ("z".to_owned(), ESExpr::Bool(false)),
        ]),
    };
    let value = ManyArgsStruct {
        args: vec!(true, true, false),
        kwargs: HashMap::from([
            ("a".to_owned(), true),
            ("b".to_owned(), true),
            ("z".to_owned(), false),
        ])
    };

    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, ManyArgsStruct::decode_esexpr(expr.clone()).unwrap());

    let value = ManyArgsEnum::Value {
        args: vec!(true, true, false),
        kwargs: HashMap::from([
            ("a".to_owned(), true),
            ("b".to_owned(), true),
            ("z".to_owned(), false),
        ])
    };

    assert_eq!(expr, value.clone().encode_esexpr());
    assert_eq!(value, ManyArgsEnum::decode_esexpr(expr).unwrap());


}




