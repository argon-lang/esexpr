use std::collections::HashSet;
use num_bigint::BigUint;
use num_traits::{Num, ToPrimitive, Zero};
use proc_macro2::TokenStream;
use quote::{format_ident, quote, ToTokens};
use syn::parse::{Parse, ParseStream};
use syn::{braced, Ident, LitInt, LitStr, Result as SynResult, Token};
use std::string::String;


pub fn esexpr_flags_impl(input: TokenStream) -> TokenStream {
	let spec = match syn::parse2::<FlagsSpec>(input) {
		Ok(s) => s,
		Err(e) => {
			let msg = format!("Error parsing esexpr_flags!: {e}");
			return quote! { compile_error!(#msg); };
		}
	};

	spec.codegen().unwrap_or_else(|msg| quote! { compile_error!(#msg); })
}

fn parse_biguint_with_prefix(s: &str) -> Result<BigUint, String> {
	let (radix, stripped) = if s.starts_with("0x") || s.starts_with("0X") {
		(16, &s[2..])
	} else if s.starts_with("0b") || s.starts_with("0B") {
		(2, &s[2..])
	} else if s.starts_with("0o") || s.starts_with("0O") {
		(8, &s[2..])
	} else {
		(10, s)
	};
	let cleaned = stripped.replace("_", "");
	BigUint::from_str_radix(&cleaned, radix).map_err(|_| format!("Could not parse bigint: {s}"))
}

struct FlagsSpec {
	name: Ident,
	items: Vec<FlagsItem>,
}

enum FlagsItem {
	Flag(Ident, BigUint),
	Enum(EnumSpec),
}

struct EnumSpec {
	name: Ident,
	variants: Vec<(Ident, BigUint)>,
}

impl Parse for FlagsSpec {
	fn parse(input: ParseStream) -> SynResult<Self> {
		let name: Ident = input.parse()?;
		let _: Token![:] = input.parse()?;

		let mut items = Vec::new();
		while !input.is_empty() {
			if input.peek(Token![enum]) {
				let _: Token![enum] = input.parse()?;
				let name: Ident = input.parse()?;
				let content;
				let _brace_token = braced!(content in input);
				let mut variants = Vec::new();
				while !content.is_empty() {
					let vname: Ident = content.parse()?;
					let _: Token![=] = content.parse()?;
					let value = if content.peek(LitInt) {
						let lit: LitInt = content.parse()?;
						let s = lit.to_string();
						parse_biguint_with_prefix(&s).map_err(|e| content.error(e))?
					} else if content.peek(LitStr) {
						let ls: LitStr = content.parse()?;
						parse_biguint_with_prefix(&ls.value()).map_err(|e| content.error(e))?
					} else {
						return Err(content.error("Expected integer or string literal for enum variant mask"));
					};
					let _: Option<Token![,]> = if content.peek(Token![,]) { Some(content.parse()?) } else { None };
					variants.push((vname, value));
				}
				// Optional comma after enum block
				let _: Option<Token![,]> = if input.peek(Token![,]) { Some(input.parse()?) } else { None };
				items.push(FlagsItem::Enum(EnumSpec { name, variants }));
			}
			else {
				let name: Ident = input.parse()?;
				let _: Token![=] = input.parse()?;
				let value = if input.peek(LitInt) {
					let lit: LitInt = input.parse()?;
					let s = lit.to_string();
					parse_biguint_with_prefix(&s).map_err(|e| input.error(e))?
				} else if input.peek(LitStr) {
					let ls: LitStr = input.parse()?;
					parse_biguint_with_prefix(&ls.value()).map_err(|e| input.error(e))?
				} else {
					return Err(input.error("Expected integer or string literal for flag mask"));
				};
				let _: Option<Token![,]> = if input.peek(Token![,]) { Some(input.parse()?) } else { None };
				items.push(FlagsItem::Flag(name, value));
			}
		}
		Ok(FlagsSpec { name, items })
	}
}

fn biguint_expr_dec(m: &BigUint) -> TokenStream {
	if let Some(m) = m.to_u64() {
		quote! { ::esexpr::core_types::num_bigint::BigUint::from(#m) }
	}
	else if let Some(m) = m.to_u128() {
		quote! { ::esexpr::core_types::num_bigint::BigUint::from(#m) }
	}
	else {
		let s = m.to_str_radix(10);
		quote! { <::esexpr::core_types::num_bigint::BigUint as ::esexpr::core_types::num_traits::Num>::from_str_radix(#s, 10).unwrap() }
	}
}

impl FlagsSpec {
	fn codegen(&self) -> Result<TokenStream, String> {
		// Generate types
		let mut struct_fields = Vec::new();
		let mut encode_ors = Vec::new();
		let mut decode_inits = Vec::new();
		let mut field_names = Vec::new();
		let mut enums_ts = Vec::new();

		let mut flag_used_bits = BigUint::ZERO;

		for item in &self.items {
			let item_mask: BigUint;
			match item {
				FlagsItem::Flag(name, mask) => {
					if mask.count_ones() != 1 {
						return Err("Flag mask must be a single bit".to_owned());
					}

					item_mask = mask.clone();

					let fname = name;
					let mask_expr = biguint_expr_dec(mask);
					struct_fields.push(quote! { pub #fname: bool });
					field_names.push(fname.to_token_stream());
					encode_ors.push(quote! { if self.#fname { value |= #mask_expr; } });
					decode_inits.push(quote! { #fname: !<::esexpr::core_types::num_bigint::BigUint as ::esexpr::core_types::num_traits::Zero>::is_zero(&(&v & #mask_expr)) });
				}
				FlagsItem::Enum(EnumSpec { name, variants }) => {
					let ename = format_ident!("{}__{}", self.name, name);
					let mut v_defs = Vec::new();
					let mut build_decode_cases = Vec::new();
					let mut union_mask: BigUint = BigUint::from(0u8);
					let mut used_masks = HashSet::new();
					for (vname, mask) in variants {
						if used_masks.contains(mask) {
							return Err("Duplicate variant mask detected".to_string());
						}
						used_masks.insert(mask.clone());

						union_mask |= mask;
						v_defs.push(quote! { #vname });
						let mask_expr = biguint_expr_dec(mask);
						build_decode_cases.push(quote! { vv if vv == #mask_expr => #ename::#vname, });
					}

					let union_mask_expr = biguint_expr_dec(&union_mask);
					item_mask = union_mask;

					enums_ts.push(quote! { #[automatically_derived] #[derive(Debug, Clone, Copy, PartialEq, Eq)] #[allow(non_camel_case_types)] pub enum #ename { #(#v_defs,)* } });

					let fname = name;
					struct_fields.push(quote! { pub #fname: #ename });
					field_names.push(fname.to_token_stream());
					let mut encode_match_arms = Vec::new();
					for (vname, mask) in variants {
						let mask_expr = biguint_expr_dec(mask);
						encode_match_arms.push(quote! { #ename::#vname => #mask_expr });
					}
					encode_ors.push(quote! { value |= match self.#fname { #( #encode_match_arms, )* }; });

					// Build decode clause for enum
					let error_ident = format_ident!("{}", ename);
					decode_inits.push(quote! {
						#fname: {
							match &v & #union_mask_expr {
								#(#build_decode_cases)*
								_ => return Err(::esexpr::DecodeError::new(::esexpr::DecodeErrorType::OutOfRange(::esexpr::core_types::alloc::format!("Invalid or overlapping flags for {}" , stringify!(#error_ident))), ::esexpr::DecodeErrorPath::Current))
							}
						}
					});
				}
			}

			if !(&item_mask & &flag_used_bits).is_zero() {
				return Err("Overlapping flag bits detected".to_owned());
			}

			flag_used_bits |= item_mask;
		}

		let name_struct = &self.name;
		let struct_def = quote! { #[automatically_derived] #[derive(Debug, Clone, PartialEq, Eq)] pub struct #name_struct { #( #struct_fields, )* } };

		let tags_impl = quote! { const TAGS: ::esexpr::ESExprTagSet = ::esexpr::ESExprTagSet::Tags(&[::esexpr::ESExprTag::Int]); };
		let encode_impl = quote! {
			fn encode_esexpr(&self) -> ::esexpr::ESExpr<'a> {
				let mut value = ::esexpr::core_types::num_bigint::BigUint::from(0u8);
				#( #encode_ors )*
				::esexpr::ESExpr::Int(::esexpr::core_types::alloc::borrow::Cow::Owned(::esexpr::core_types::num_bigint::BigInt::from(value)))
			}
		};
		let decode_impl = quote! {
			fn decode_esexpr(expr: ::esexpr::ESExpr<'a>) -> ::core::result::Result<Self, ::esexpr::DecodeError> {
				let v: ::esexpr::core_types::num_bigint::BigUint = <::esexpr::core_types::num_bigint::BigUint as ::esexpr::ESExprCodec>::decode_esexpr(expr)?;
				Ok(#name_struct { #( #decode_inits, )* })
			}
		};
		let codec_impl = quote! { impl<'a> ::esexpr::ESExprCodec<'a> for #name_struct { #tags_impl #encode_impl #decode_impl } };
		let eq_impl = quote! { impl ::esexpr::ESExprEncodedEq for #name_struct { fn is_encoded_eq(&self, other: &Self) -> bool { self == other } } };

		let output = quote! {
			#( #enums_ts )*
			#struct_def
			#codec_impl
			#eq_impl
		};

		Ok(output)
	}
}
