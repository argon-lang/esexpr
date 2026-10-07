#[derive(esexpr::ESExprEncodedEq)]
union Invalid {
 //~^ ERROR: ESExprEncodedEq derive is not supported for unions
 value: i32,
}
