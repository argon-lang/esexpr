
# ESExpr

ESExpr is a serialization format that consists of exprssions of the following forms.
- A Constructor with a name and positional and keyword arguments.
- Booleans
- Integers
- Strings
- Binary Data
- Float32/64
- Null

Constructor and keyword names are stored in a string table to prevent duplication.
The string table can be stored internally or externally.
Each of these types has its own tag associated with it.
Constructor tags include the constructor name.


## Derivation
The `ESExprCodec` trait can be derived.

```rust
#[derive(ESExprCodec, Copy, Debug, PartialEq)]
struct MyStruct {
    pub a: String,
}

#[derive(ESExprCodec, Copy, Debug, PartialEq)]
struct MyEnum {
    A(String),
    B(i32),
}
```

By default, the struct, enum case, and field names are converted to kebab-case.
In the above example, `MyStruct` has constructor `my-struct` and `MyEnum` has constructors `a` and `b`.
This can be customized using the `constructor` attribute.

```rust
#[derive(ESExprCodec, Copy, Debug, PartialEq)]
#[constructor = "my-ctor"]
struct MyStruct {
    pub a: String,
}

#[derive(ESExprCodec, Copy, Debug, PartialEq)]
struct MyEnum {
    #[constructor = "a2"]
    A(String),
    B(i32),
}
```

In some cases, the encoding overhead of a constructor is undesireable for enum cases.
In such cases, `inline_value` can be used to encode a case directly.
Only a single field is allowed.
It is the responsibility of the user to ensure that the tags do not overlap with any other cases.

```rust
#[derive(ESExprCodec, Copy, Debug, PartialEq)]
struct MyEnum {
    #[inline_value]
    A(String),
    B(i32),
}
```

For simpler cases, string values can be used as an `enum` by adding `simple_enum`.
Each constructor name is encoded as a string value.
Fields may not be specified for any cases.

```rust
#[derive(ESExprCodec, Copy, Debug, PartialEq)]
#[simple_enum]
struct MyEnum {
    A,
    B,
}
```

By default, fields specify positional arguments.
To define keyword arguments, use `keyword`.
Keyword arguments can be omitted if `required` is set to `false`.
Additionally, default values can be set using `default_value`.

```rust
#[derive(ESExprCodec, Copy, Debug, PartialEq)]
struct MyStruct {
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
```

To handle multiple arguments, `vararg` and `dict` can be used for positional and keyword arguments, respectively.

```rust
#[derive(ESExprCodec, Copy, Debug, PartialEq)]
struct MyStruct {
    #[vararg]
    args: Vec<bool>,

    #[dict]
    kwargs: std::collections::HashMap<String, bool>,
}
```
