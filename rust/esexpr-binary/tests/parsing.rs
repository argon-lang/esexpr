use esexpr_binary::ExprParser;
use std::path::PathBuf;



fn parse_test(name: &str) {
    let mut dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    dir.push("../../tests");

    let mut esx = dir.clone();
    esx.push(format!("{}.esxb", name));
    let esx = esexpr_binary::parse(&std::fs::File::open(esx).unwrap())
        .iter_static()
        .collect::<Result<Vec<_>, _>>()
        .unwrap();

    let mut json = dir;
    json.push(format!("{}.json", name));
    let json = std::fs::read_to_string(json).unwrap();
    let json: esexpr_json::JsonEncodedESExpr = serde_json::from_str(&json).unwrap();
    let json = match json {
        esexpr_json::JsonEncodedESExpr::List(items) => items,
        _ => vec![json],
    };
    let json = json.into_iter().map(esexpr_json::JsonEncodedESExpr::into_esexpr).collect::<Vec<_>>();


    assert_eq!(esx, json);
}

#[test]
fn parse_bool() {
    parse_test("bool_false");
    parse_test("bool_true");
    
}

#[test]
fn parse_constructor() {
    parse_test("constructor");
    parse_test("constructor2");
    parse_test("constructor-keyword");
}

#[test]
fn parse_append_string_table() {
    parse_test("append-string-table");
}

#[test]
fn parse_str() {
    parse_test("str");
}

#[test]
fn parse_int() {
    parse_test("int");
}

#[test]
fn parse_float32() {
    parse_test("float32");
}

#[test]
fn parse_float64() {
    parse_test("float64");
}

#[test]
fn parse_null() {
    parse_test("null");
}
