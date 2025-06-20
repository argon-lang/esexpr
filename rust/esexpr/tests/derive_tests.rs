use std::borrow::Cow;

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
    use std::collections::HashSet;
    use esexpr::{esexpr, ESExprTag, ESExprCodec};

    let expr = esexpr! {
        ("constructor-name123-conversion" 5)
    };

    let value = ConstructorName123Conversion {
        a: 5,
    };

    assert_eq!(HashSet::from([ESExprTag::Constructor(Cow::Borrowed("constructor-name123-conversion"))]), ConstructorName123Conversion::tags());
    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, ConstructorName123Conversion::decode_esexpr(expr).unwrap());


    let expr = esexpr! { ("bad-name" 5) };

    assert!(ConstructorName123Conversion::decode_esexpr(expr).is_err());


    let expr = esexpr! { ("my-name123-test") };

    let value = ConstructorNameEnum::MyName123Test;

    assert_eq!(HashSet::from([ESExprTag::Constructor(Cow::Borrowed("my-name123-test")), ESExprTag::Constructor(Cow::Borrowed("my-ctor"))]), ConstructorNameEnum::tags());
    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, ConstructorNameEnum::decode_esexpr(expr).unwrap());


    let expr = esexpr! { ("bad-name") };

    assert!(ConstructorNameEnum::decode_esexpr(expr).is_err());


}

#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
#[constructor = "my-ctor"]
struct CustomConstructorName {
    a: i32,
}

#[test]
fn custom_constructor_name() {
    use esexpr::{esexpr, ESExprCodec};

    let expr = esexpr! { ("my-ctor" 5) };

    let value = CustomConstructorName {
        a: 5,
    };

    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, CustomConstructorName::decode_esexpr(expr).unwrap());

    let expr = esexpr! { ("bad-name" 5) };

    assert!(ConstructorName123Conversion::decode_esexpr(expr).is_err());


    let expr = esexpr! { ("my-ctor") };

    let value = ConstructorNameEnum::CustomName;

    assert_eq!(expr, value.encode_esexpr());
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
    use std::collections::{HashSet};
    use esexpr::{esexpr, ESExprTag, ESExprCodec};

    let expr = esexpr!(true);
    let value = InlineValueTest::Flag(true);

    assert_eq!(HashSet::from([ESExprTag::Bool, ESExprTag::Constructor(Cow::Borrowed("normal-case"))]), InlineValueTest::tags());
    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, InlineValueTest::decode_esexpr(expr).unwrap());


    let expr = esexpr! { (flag true) };

    assert!(InlineValueTest::decode_esexpr(expr).is_err());

    let expr = esexpr! { ("normal-case" true) };
    let value = InlineValueTest::NormalCase(true);

    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, InlineValueTest::decode_esexpr(expr).unwrap());


}


#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
#[constructor = "optional-args"]
struct PositionalArgsOptional1(bool, bool, #[optional] Option<bool>);


#[test]
fn positional_optional_args() {
    use esexpr::{esexpr, ESExprCodec};


    let expr2 = esexpr! { ("optional-args" true false) };
    let expr3 = esexpr! { ("optional-args" true false true) };


    let value = PositionalArgsOptional1(true, false, None);
    assert_eq!(expr2, value.encode_esexpr());
    assert_eq!(value, PositionalArgsOptional1::decode_esexpr(expr2.clone()).unwrap());

    let value = PositionalArgsOptional1(true, false, Some(true));
    assert_eq!(expr3, value.encode_esexpr());
    assert_eq!(value, PositionalArgsOptional1::decode_esexpr(expr3.clone()).unwrap());    
}




#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
#[constructor = "keywords"]
struct KeywordStruct {
    #[keyword]
    a: bool,

    #[keyword = "b2"]
    b: bool,

    #[keyword = "c2"]
    #[optional]
    c: Option<bool>,

    #[keyword]
    #[optional]
    d: Option<bool>,

    #[keyword]
    #[default_value = "false"]
    e: bool,

    #[keyword]
    f: Option<bool>,
}

#[derive(esexpr::ESExprCodec, Debug, PartialEq, Clone)]
enum KeywordEnum {
    #[constructor = "keywords"]
    Value {
        #[keyword]
        a: bool,

        #[keyword = "b2"]
        b: bool,

        #[keyword = "c2"]
        #[optional]
        c: Option<bool>,

        #[keyword]
        #[optional]
        d: Option<bool>,

        #[keyword]
        #[default_value = "false"]
        e: bool,

        #[keyword]
        f: Option<bool>,
    },
}


#[test]
fn keyword_args() {
    use std::collections::HashSet;
    use esexpr::{esexpr, ESExprTag, ESExprCodec};

    let expr = esexpr! { (keywords a: true b2: true c2: true d: true e: true f: true) };

    let value = KeywordStruct {
        a: true,
        b: true,
        c: Some(true),
        d: Some(true),
        e: true,
        f: Some(true),
    };

    let tags = HashSet::from([ESExprTag::Constructor(Cow::Borrowed("keywords"))]);

    assert_eq!(tags, KeywordStruct::tags());
    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, KeywordStruct::decode_esexpr(expr.clone()).unwrap());

    let value = KeywordEnum::Value {
        a: true,
        b: true,
        c: Some(true),
        d: Some(true),
        e: true,
        f: Some(true),
    };

    assert_eq!(tags, KeywordEnum::tags());
    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, KeywordEnum::decode_esexpr(expr).unwrap());



    let expr = esexpr! { (keywords a: true b2: true f: #null) };

    let value = KeywordStruct {
        a: true,
        b: true,
        c: None,
        d: None,
        e: false,
        f: None,
    };

    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, KeywordStruct::decode_esexpr(expr.clone()).unwrap());



    let value = KeywordEnum::Value {
        a: true,
        b: true,
        c: None,
        d: None,
        e: false,
        f: None,
    };

    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, KeywordEnum::decode_esexpr(expr).unwrap());

    
    let expr = esexpr! {
        (keywords a: true b2: true)
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
    use esexpr::{esexpr, ESExprTag, ESExprCodec};

    let expr = esexpr!("a");
    let value = SimpleEnum::A;

    assert_eq!(HashSet::from([ESExprTag::Str]), SimpleEnum::tags());
    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, SimpleEnum::decode_esexpr(expr).unwrap());

    let expr = esexpr!("b");
    let value = SimpleEnum::B;

    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, SimpleEnum::decode_esexpr(expr).unwrap());


    let expr = esexpr!("my-c");
    let value = SimpleEnum::C;

    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, SimpleEnum::decode_esexpr(expr).unwrap());


    let expr = esexpr!("d");

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
    use esexpr::{esexpr, ESExprCodec};

    let expr = esexpr! {
        (many true true false a: true b: true z: false)
    };
    let value = ManyArgsStruct {
        args: vec!(true, true, false),
        kwargs: HashMap::from([
            ("a".to_owned(), true),
            ("b".to_owned(), true),
            ("z".to_owned(), false),
        ])
    };

    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, ManyArgsStruct::decode_esexpr(expr.clone()).unwrap());

    let value = ManyArgsEnum::Value {
        args: vec!(true, true, false),
        kwargs: HashMap::from([
            ("a".to_owned(), true),
            ("b".to_owned(), true),
            ("z".to_owned(), false),
        ])
    };

    assert_eq!(expr, value.encode_esexpr());
    assert_eq!(value, ManyArgsEnum::decode_esexpr(expr).unwrap());


}



#[derive(esexpr::ESExprCodec, Clone)]
struct GenericTest<A>(A);


#[test]
fn generic_tests() {
    use esexpr::{esexpr, ESExprCodec};

    let expr = esexpr! {
        ("generic-test" 5)
    };

    assert_eq!(expr, GenericTest(5).encode_esexpr());
    assert_eq!(5, GenericTest::decode_esexpr(expr).unwrap().0);
}
