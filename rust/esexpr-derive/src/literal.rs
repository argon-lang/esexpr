use proc_macro2::TokenStream;
use quote::quote;
use syn::parse::{Parse, ParseStream, Parser};
use syn::{Ident, LitBool, LitFloat, LitInt, LitStr, token};

pub fn esexpr_literal_impl(input: TokenStream) -> TokenStream {
	let literal = match Parser::parse2(ESExprLiteral::parse, input) {
		Ok(literal) => literal,
		Err(e) => {
			let msg = format!("Error parsing ESExpr literal: {e:?}");
			return quote! {
				compile_error!(#msg);
			};
		},
	};

	generate_literal(literal)
}

enum ESExprLiteral {
	Constructor(ESExprConstructorLiteral),
	Scalar(ESExprScalarLiteral),
	Null(u32),
}

impl Parse for ESExprLiteral {
	fn parse(input: ParseStream) -> syn::Result<Self> {
		Ok(if input.peek(token::Paren) {
			ESExprLiteral::Constructor(ESExprConstructorLiteral::parse(input)?)
		}
		else if input.peek(token::Pound) {
			let _: token::Pound = input.parse()?;
			let name: Ident = input.parse()?;
			match name.to_string().as_str() {
				"null" => ESExprLiteral::Null(0),
				_ => todo!(),
			}
		}
		else {
			ESExprLiteral::Scalar(ESExprScalarLiteral::parse(input)?)
		})
	}
}

struct ESExprConstructorLiteral {
	_parens: token::Paren,
	body: ESExprConstructorBody,
}

impl Parse for ESExprConstructorLiteral {
	fn parse(input: ParseStream) -> syn::Result<Self> {
		let tokens;
		let parens: token::Paren = ::syn::parenthesized!(tokens in input);
		let body: ESExprConstructorBody = tokens.parse()?;
		Ok(ESExprConstructorLiteral { _parens: parens, body })
	}
}

struct ESExprConstructorBody {
	name: LitStr,
	args: Vec<ESExprConstructorArgument>,
}

impl Parse for ESExprConstructorBody {
	fn parse(input: ParseStream) -> syn::Result<Self> {
		let name = if input.peek(Ident) {
			let ident: Ident = input.parse()?;
			LitStr::new(&ident.to_string(), ident.span())
		}
		else {
			input.parse()?
		};

		let mut args: Vec<ESExprConstructorArgument> = Vec::new();
		while !input.is_empty() {
			args.push(input.parse()?);
		}

		Ok(ESExprConstructorBody { name, args })
	}
}

enum ESExprConstructorArgument {
	Positional(ESExprLiteral),
	Keyword(LitStr, ESExprLiteral),
}

impl Parse for ESExprConstructorArgument {
	fn parse(input: ParseStream) -> syn::Result<Self> {
		if input.peek(token::Colon) {
			let _: token::Colon = input.parse()?;
			let name = input.parse()?;
			let value = input.parse()?;
			Ok(ESExprConstructorArgument::Keyword(name, value))
		}
		else if input.peek(Ident) {
			let name: Ident = input.parse()?;
			let name = LitStr::new(&name.to_string(), name.span());
			let _: token::Colon = input.parse()?;
			let value = input.parse()?;
			Ok(ESExprConstructorArgument::Keyword(name, value))
		}
		else {
			Ok(ESExprConstructorArgument::Positional(input.parse()?))
		}
	}
}

enum ESExprScalarLiteral {
	Bool(LitBool),
	Str(LitStr),
	Int(LitInt),
	Float(LitFloat),
}

impl Parse for ESExprScalarLiteral {
	fn parse(input: ParseStream) -> syn::Result<Self> {
		if input.peek(LitBool) {
			let value: LitBool = input.parse()?;
			return Ok(Self::Bool(value));
		}
		if input.peek(LitStr) {
			let value: LitStr = input.parse()?;
			return Ok(Self::Str(value));
		}
		if input.peek(LitInt) {
			let value: LitInt = input.parse()?;
			return Ok(Self::Int(value));
		}
		if input.peek(LitFloat) {
			let value: LitFloat = input.parse()?;
			return Ok(Self::Float(value));
		}
		Err(input.error("expected one of bool, str, int, or float"))
	}
}

fn generate_literal(literal: ESExprLiteral) -> TokenStream {
	match literal {
		ESExprLiteral::Constructor(constructor) => generate_constructor(constructor),
		ESExprLiteral::Scalar(scalar) => generate_scalar(scalar),
		ESExprLiteral::Null(level) => generate_null(level),
	}
}

fn generate_constructor(constructor: ESExprConstructorLiteral) -> TokenStream {
	let name = constructor.body.name;
	let mut args = Vec::new();
	let mut kwargs: Vec<TokenStream> = Vec::new();
	for arg in constructor.body.args {
		match arg {
			ESExprConstructorArgument::Positional(literal) => {
				args.push(generate_literal(literal));
			},
			ESExprConstructorArgument::Keyword(name, literal) => {
				let literal_expr = generate_literal(literal);
				kwargs.push(quote! {
					(::std::borrow::Cow::Borrowed(#name), #literal_expr),
				});
			},
		}
	}

	quote! {
		::esexpr::ESExpr::Constructor {
			name: ::std::borrow::Cow::Borrowed(#name),
			args: ::std::borrow::Cow::Owned(vec![
				#(#args,)*
			]),
			kwargs: ::std::borrow::Cow::Owned(::std::collections::HashMap::from([
				#(#kwargs)*
			]))
		}
	}
}

fn generate_scalar(scalar: ESExprScalarLiteral) -> TokenStream {
	match scalar {
		ESExprScalarLiteral::Bool(bool) => quote! {
			::esexpr::ESExpr::Bool(#bool)
		},
		ESExprScalarLiteral::Str(str) => quote! {
			::esexpr::ESExpr::Str(::std::borrow::Cow::Borrowed(#str))
		},
		ESExprScalarLiteral::Int(int) => quote! {
			::esexpr::ESExpr::Int(::std::borrow::Cow::Owned(::num_bigint::BigInt::from(#int)))
		},
		ESExprScalarLiteral::Float(float) => {
			if float.suffix().eq_ignore_ascii_case("f32") {
				quote! {
					::esexpr::ESExpr::Float32(#float)
				}
			}
			else {
				quote! {
					::esexpr::ESExpr::Float64(#float)
				}
			}
		},
	}
}

fn generate_null(level: u32) -> TokenStream {
	quote! {
		::esexpr::ESExpr::Null(::std::borrow::Cow::Owned(::num_bigint::BigUint::from(#level)))
	}
}
