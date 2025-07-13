use std::collections::HashSet;

use darling::util::Flag;
use darling::{FromAttributes, FromMeta};
use proc_macro2::Span;
use quote::quote;
use syn::punctuated::Punctuated;
use syn::{
	Data,
	DeriveInput,
	Expr,
	ExprLit,
	ExprPath,
	Field,
	Fields,
	GenericArgument,
	GenericParam,
	Ident,
	Index,
	Lit,
	LitStr,
	Path,
	Token,
	Type,
	TypePath,
	Variant,
	parse_quote,
};

#[derive(Debug, Default, FromAttributes)]
#[darling(attributes(esexpr))]
struct ESExprTypeAttr {
	constructor: Option<String>,
	simple_enum: Flag,
}

#[derive(Debug, Default, FromAttributes)]
#[darling(attributes(esexpr))]
struct ESExprVariantAttr {
	constructor: Option<String>,
	inline_value: Flag,
}

#[derive(Debug, Default, FromAttributes)]
#[darling(attributes(esexpr))]
struct ESExprFieldAttr {
	keyword: Option<ESExprKeywordAttr>,
	default_value: Option<Expr>,
	optional: Flag,
	dict: Flag,
	vararg: Flag,
}

#[derive(Debug)]
struct ESExprKeywordAttr {
	name: Option<String>,
}

impl FromMeta for ESExprKeywordAttr {
	fn from_word() -> darling::Result<Self> {
		Ok(ESExprKeywordAttr { name: None })
	}

	fn from_string(value: &str) -> darling::Result<Self> {
		Ok(ESExprKeywordAttr {
			name: Some(value.to_owned()),
		})
	}
}

type TokenRes = Result<proc_macro2::TokenStream, proc_macro2::TokenStream>;

fn flatten_token_res(res: TokenRes) -> proc_macro2::TokenStream {
	match res {
		Ok(ts) | Err(ts) => ts,
	}
}

pub fn derive_esexpr_codec_impl(input: proc_macro2::TokenStream) -> proc_macro2::TokenStream {
	let input: DeriveInput = parse_quote!(#input);

	let type_name = input.ident;

	let generics_params = input.generics.params;

	let generics_lt = input.generics.lt_token;
	let generics_gt = input.generics.gt_token;

	let type_args = generics_params
		.iter()
		.map(param_to_arg)
		.collect::<Punctuated<GenericArgument, Token![,]>>();

	let type_params = if generics_params.is_empty() {
		quote! { <'esexpr_lifetime> }
	}
	else {
		let params = generics_params
			.into_iter()
			.map(|mut p| {
				match &mut p {
					GenericParam::Type(p) => {
						p.colon_token.get_or_insert_default();
						p.bounds.push(parse_quote! { ::esexpr::ESExprCodec<'esexpr_lifetime> });
					},

					GenericParam::Lifetime(_) | GenericParam::Const(_) => {},
				}

				p
			})
			.collect::<Vec<_>>();

		quote! {
			<'esexpr_lifetime, #(#params),*>
		}
	};

	let attr = match ESExprTypeAttr::from_attributes(&input.attrs) {
		Ok(attr) => attr,
		Err(e) => return e.write_errors(),
	};

	if let Err(e) = validate_attributes(&attr, &type_name, &input.data) {
		return e;
	}

	let tags = flatten_token_res(get_esexpr_tag(&attr, &type_name, &input.data));
	let encode = flatten_token_res(get_esexpr_encode(&attr, &type_name, &input.data));
	let decode = flatten_token_res(get_esexpr_decode(&attr, &type_name, &input.data));

	quote! {
		impl #type_params ::esexpr::ESExprCodec<'esexpr_lifetime> for #type_name #generics_lt #type_args #generics_gt {

			const TAGS: ::esexpr::ESExprTagSet = {
				#tags
			};

			fn encode_esexpr(&'esexpr_lifetime self) -> ::esexpr::ESExpr<'esexpr_lifetime> {
				#encode
			}

			fn decode_esexpr(expr: ::esexpr::ESExpr<'esexpr_lifetime>) -> ::esexpr::core_types::core::result::Result<Self, ::esexpr::DecodeError> {
				#decode
			}
		}
	}
}

fn validate_attributes(attr: &ESExprTypeAttr, _type_name: &Ident, data: &Data) -> Result<(), proc_macro2::TokenStream> {
	match data {
		Data::Struct(s) => {
			if attr.simple_enum.is_present() {
				Err(quote! { compile_error!("Struct cannot be a simple_enum."); })?;
			}

			validate_fields(&s.fields)?;
		},
		Data::Enum(e) => {
			if attr.constructor.is_some() {
				Err(
					quote! { compile_error!("Constructor name may only be specified for structs and enum variants"); },
				)?;
			}

			for c in &e.variants {
				let variant_attr =
					ESExprVariantAttr::from_attributes(&c.attrs).map_err(darling::Error::write_errors)?;

				if variant_attr.inline_value.is_present() && variant_attr.constructor.is_some() {
					Err(quote! { compile_error!("Variant cannot have both inline_value and constructor."); })?;
				}

				validate_fields(&c.fields)?;
			}
		},
		Data::Union(_) => (),
	}

	Ok(())
}

fn validate_fields(fields: &Fields) -> Result<(), proc_macro2::TokenStream> {
	let fields = match fields {
		Fields::Named(named) => &named.named,
		Fields::Unnamed(unnamed) => &unnamed.unnamed,
		Fields::Unit => return Ok(()),
	};

	for field in fields {
		let attr = ESExprFieldAttr::from_attributes(&field.attrs).map_err(darling::Error::write_errors)?;

		if attr.optional.is_present() && attr.default_value.is_some() {
			return Err(quote! { compile_error!("Optional arguments cannot have default values."); });
		}

		if attr.keyword.is_some() {
			if attr.dict.is_present() {
				return Err(quote! { compile_error!("Keyword arguments cannot be dict values."); });
			}

			if attr.vararg.is_present() {
				return Err(quote! { compile_error!("Keyword arguments cannot be vararg values."); });
			}
		}
		else if attr.dict.is_present() {
			if attr.vararg.is_present() {
				return Err(quote! { compile_error!("Dict arguments cannot be vararg values."); });
			}
			if attr.optional.is_present() {
				return Err(quote! { compile_error!("Dictionary arguments cannot be optional."); });
			}

			if attr.default_value.is_some() {
				return Err(quote! { compile_error!("Dictionary arguments cannot have default values."); });
			}
		}
		else if attr.vararg.is_present() {
			if attr.optional.is_present() {
				return Err(quote! { compile_error!("Variable arguments cannot be optional."); });
			}

			if attr.default_value.is_some() {
				return Err(quote! { compile_error!("Variable arguments cannot have default values."); });
			}
		}
	}

	Ok(())
}

fn param_to_arg(p: &GenericParam) -> GenericArgument {
	match p {
		GenericParam::Lifetime(l) => GenericArgument::Lifetime(l.lifetime.clone()),
		GenericParam::Type(t) => GenericArgument::Type(Type::Path(TypePath {
			qself: None,
			path: Path::from(t.ident.clone()),
		})),
		GenericParam::Const(c) => GenericArgument::Const(Expr::Path(ExprPath {
			attrs: vec![],
			qself: None,
			path: Path::from(c.ident.clone()),
		})),
	}
}

fn get_esexpr_tag(attr: &ESExprTypeAttr, type_name: &Ident, data: &Data) -> TokenRes {
	fn make_constructor_expr(name: &str) -> Expr {
		parse_quote! { ::esexpr::ESExprTag::Constructor(::esexpr::cowstr::CowStr::Borrowed(#name)) }
	}

	fn make_set_of(e: Expr) -> proc_macro2::TokenStream {
		quote! {
			::esexpr::ESExprTagSet::Tags(&[#e])
		}
	}

	Ok(match data {
		Data::Struct(_) => make_set_of(make_constructor_expr(&make_constructor_name(
			attr.constructor.as_ref(),
			type_name,
		))),

		Data::Enum(_) if attr.simple_enum.is_present() => make_set_of(parse_quote! { ::esexpr::ESExprTag::Str }),

		Data::Enum(e) => {
			let case_tags: Vec<proc_macro2::TokenStream> = e
				.variants
				.iter()
				.map(|c| -> TokenRes {
					let variant_attr =
						ESExprVariantAttr::from_attributes(&c.attrs).map_err(darling::Error::write_errors)?;

					if variant_attr.inline_value.is_present() {
						let t = &get_inline_value_field(c)?.ty;
						Ok(quote! { <#t as ::esexpr::ESExprCodec>::TAGS })
					}
					else {
						let tag =
							make_constructor_expr(&make_constructor_name(variant_attr.constructor.as_ref(), &c.ident));
						Ok(make_set_of(tag))
					}
				})
				.collect::<Result<_, _>>()?;

			quote! {
				::esexpr::ESExprTagSet::Concat(&[ #(#case_tags,)* ])
			}
		},

		Data::Union(_) => Err(quote! { compile_error!("ESExprCodec cannot be derived for union"); })?,
	})
}

fn get_esexpr_encode(attr: &ESExprTypeAttr, type_name: &Ident, data: &Data) -> TokenRes {
	Ok(match data {
		Data::Struct(s) => {
			let constructor_name = make_constructor_name(attr.constructor.as_ref(), type_name);

			let encode_fields = make_encode_fields(&s.fields, |name, i| {
				if let Some(name) = name {
					quote! { &self.#name }
				}
				else {
					let i_ident = Index::from(i);
					quote! { &self.#i_ident }
				}
			})?;

			quote! {
				let mut args = ::esexpr::core_types::alloc::vec::Vec::<::esexpr::ESExpr<'esexpr_lifetime>>::new();
				let mut kwargs = ::esexpr::core_types::alloc::collections::BTreeMap::<::esexpr::cowstr::CowStr<'esexpr_lifetime>, ::esexpr::ESExpr>::new();
				#encode_fields
				::esexpr::ESExpr::constructor(#constructor_name, args, kwargs)
			}
		},

		Data::Enum(e) if attr.simple_enum.is_present() => {
			let mut names = HashSet::new();
			
			let cases: proc_macro2::TokenStream = e
				.variants
				.iter()
				.map(|c| -> TokenRes {
					let variant_attr =
						ESExprVariantAttr::from_attributes(&c.attrs).map_err(darling::Error::write_errors)?;

					let case_name = &c.ident;
					let case_name_str = make_constructor_name(variant_attr.constructor.as_ref(), case_name);
					
					if !names.insert(case_name_str.clone()) {
						return Err(quote! {
							compile_error!("Duplicate simple enum case name: {}", #case_name_str);
						})
					}

					Ok(quote! {
						#type_name::#case_name => ::esexpr::ESExpr::Str(::esexpr::cowstr::CowStr::Static(#case_name_str)),
					})
				})
				.collect::<Result<_, _>>()?;

			quote! {
				match self {
					#cases
				}
			}
		},

		Data::Enum(e) => {
			let mut case_tag_exprs = Vec::new();
			let mut case_tag_assertions = Vec::new();
			
			let cases: proc_macro2::TokenStream = e.variants.iter().map(|c| -> TokenRes {
                fn make_field_name<'a>(name: Option<&'a Ident>, i: usize) -> proc_macro2::TokenStream {
                    let name =
                        if let Some(name) = name { format!("field_{name}") }
                        else { format!("field_{i}") };

                    let name = Ident::new(&name, Span::mixed_site());
                    quote! { #name }
                }

				let variant_attr = ESExprVariantAttr::from_attributes(&c.attrs)
					.map_err(darling::Error::write_errors)?;

				let case_name = &c.ident;

                let pattern = match &c.fields {
                    Fields::Named(fields) => {
                        let field_patterns: proc_macro2::TokenStream = fields.named.iter().enumerate().map(|(i, field)| {
							#[expect(clippy::unwrap_used, reason = "This is a named field, so it must have a name.")]
                            let orig_name = field.ident.as_ref().unwrap();
                            let mapped_name = make_field_name(field.ident.as_ref(), i);
                            quote! { #orig_name: #mapped_name, }
                        }).collect();

                        quote! {
                            #type_name::#case_name { #field_patterns }
                        }
                    },

                    Fields::Unnamed(fields) => {
                        let field_patterns: proc_macro2::TokenStream = (0..fields.unnamed.len()).map(|i| {
                            let mapped_name = make_field_name(None, i);
                            quote! { #mapped_name, }
                        }).collect();

                        quote! {
                            #type_name::#case_name(#field_patterns)
                        }
                    },

                    Fields::Unit => quote! { #type_name::#case_name },
                };

				let field_tags;
				let case_tokens;
				
                if variant_attr.inline_value.is_present() {
                    let field = get_inline_value_field(c)?;
                    let field_name = make_field_name(field.ident.as_ref(), 0);
                    let field_type = &field.ty;
					
					field_tags = quote! {
						<#field_type as ::esexpr::ESExprCodec>::TAGS
					};

                    case_tokens = quote! {
                        #pattern => {
                            <#field_type as ::esexpr::ESExprCodec>::encode_esexpr(#field_name)
                        }
                    };
                }
                else {
                    let constructor_name = make_constructor_name(variant_attr.constructor.as_ref(), case_name);

                    let encode_fields = make_encode_fields(&c.fields, make_field_name)?;

					field_tags = quote! {
						::esexpr::ESExprTagSet::Tags(&[ ::esexpr::ESExprTag::Constructor(::esexpr::cowstr::CowStr::Static(#constructor_name)) ])
					};
					
                    case_tokens = quote! {
                        #pattern => {
                            let mut args = ::esexpr::core_types::alloc::vec::Vec::<::esexpr::ESExpr<'esexpr_lifetime>>::new();
                            let mut kwargs = ::esexpr::core_types::alloc::collections::BTreeMap::<::esexpr::cowstr::CowStr<'esexpr_lifetime>, ::esexpr::ESExpr<'esexpr_lifetime>>::new();
                            #encode_fields
                            ::esexpr::ESExpr::constructor(#constructor_name, args, kwargs)
                        }
                    };
                }

				let message_disjoint = format!("Inline value case '{case_name}' must have distinct tags from immediately preceding optional positional arguments");

				case_tag_assertions.push(quote! {
					assert!(::esexpr::ESExprTagSet::Concat(&[ #(#case_tag_exprs,)* ]).is_disjoint(#field_tags), #message_disjoint);
				});

				case_tag_exprs.push(field_tags);
				
				Ok(case_tokens)

            }).collect::<Result<_, _>>()?;

			quote! {
				#(#case_tag_assertions)*
				match self {
					#cases
				}
			}
		},

		Data::Union(_) => Err(quote! { compile_error!("ESExprCodec cannot be derived for union"); })?,
	})
}

fn make_encode_fields<'a, F: Fn(Option<&'a Ident>, usize) -> proc_macro2::TokenStream>(
	fields: &'a Fields,
	make_field_expr: F,
) -> TokenRes {
	fn make_pos_tag_check(
		field_name: &str,
		field_tags: &proc_macro2::TokenStream,
		prev_optional_positional_tags: &[proc_macro2::TokenStream],
	) -> proc_macro2::TokenStream {
		if prev_optional_positional_tags.is_empty() {
			return quote! {}
		}

		let message_nonfull =
			format!("Field '{field_name}' cannot follow optional positional arguments with all tags");

		let message_disjoint = format!(
			"Field '{field_name}' must have distinct tags from immediately preceding optional positional arguments"
		);

		quote! {
			const {
				assert!(!::esexpr::ESExprTagSet::Concat(&[ #(#prev_optional_positional_tags,)* ]).is_all(), #message_nonfull);
				assert!(::esexpr::ESExprTagSet::Concat(&[ #(#prev_optional_positional_tags,)* ]).is_disjoint(#field_tags), #message_disjoint);
			}
		}
	}

	let fields = match fields {
		Fields::Named(fields) => fields.named.iter().collect(),
		Fields::Unnamed(fields) => fields.unnamed.iter().collect(),
		Fields::Unit => Vec::new(),
	};

	let mut has_dict_field = false;
	let mut kwarg_names = HashSet::new();

	let mut prev_optional_positional_tags = Vec::new();

	fields.into_iter().enumerate().map(|(i, field)| -> TokenRes {
		let field_name = match field.ident {
			Some(ref ident) => ident.to_string(),
			None => i.to_string(),
		};

        let field_expr = make_field_expr(field.ident.as_ref(), i);
        let field_type = &field.ty;

		let field_attr = ESExprFieldAttr::from_attributes(&field.attrs)
			.map_err(darling::Error::write_errors)?;

        Ok(
            if let Some(keyword_attr) = field_attr.keyword.as_ref() {
                if has_dict_field {
                    Err(quote! { compile_error!("Keyword arguments cannot be used with dict arguments"); })?;
                }

                let kw = make_kwarg_name(keyword_attr.name.as_ref(), field.ident.as_ref())?;

                if kwarg_names.contains(&kw) {
                    let message = make_str_expr(&format!("Duplicate keyword argument \"{kw}\""));
                    Err(quote! { compile_error!(#message); })?;
                }

                kwarg_names.insert(kw.clone());

                if field_attr.optional.is_present() {
                    quote! { if let Some(value) = <#field_type as ::esexpr::ESExprOptionalFieldCodec>::encode_optional_field(#field_expr) { kwargs.insert(::esexpr::cowstr::CowStr::Static(#kw), value); } }
                }
                else if let Some(default_value) = field_attr.default_value.as_ref() {
                    quote! {
                        {
                            let value = #field_expr;
                            if !<#field_type as ::esexpr::ESExprEncodedEq>::is_encoded_eq(value, &#default_value) {
                                kwargs.insert(::esexpr::cowstr::CowStr::Static(#kw), <#field_type as ::esexpr::ESExprCodec>::encode_esexpr(value));
                            }
                        }
                    }
                }
                else {
                    quote! { kwargs.insert(::esexpr::cowstr::CowStr::Static(#kw), <#field_type as ::esexpr::ESExprCodec>::encode_esexpr(#field_expr)); }
                }
            }
            else if field_attr.dict.is_present() {
                if has_dict_field {
                    Err(quote! { compile_error!("Only a single dict argument is allowed"); })?;
                }
                has_dict_field = true;

				if !kwarg_names.is_empty() {
					Err(quote! { compile_error!("Keyword arguments cannot be used with dict arguments"); })?;
				}

                quote! { ::esexpr::ESExprDictCodec::encode_dict_element(#field_expr, &mut kwargs); }
            }
            else if field_attr.vararg.is_present() {
				let tags = quote! { <<#field_type as ::esexpr::ESExprVarArgCodec>::Element as ::esexpr::ESExprCodec>::TAGS };
				let checks = make_pos_tag_check(&field_name, &tags, &prev_optional_positional_tags);
				prev_optional_positional_tags.push(tags);

                quote! {
					#checks
					::esexpr::ESExprVarArgCodec::encode_vararg_element(#field_expr, &mut args);
				}
            }
            else {
                if field_attr.optional.is_present() {
					let tags = quote! { <<#field_type as ::esexpr::ESExprOptionalFieldCodec>::Element as ::esexpr::ESExprCodec>::TAGS };
					let checks = make_pos_tag_check(&field_name, &tags, &prev_optional_positional_tags);
					prev_optional_positional_tags.push(tags);

                    quote! {
						#checks
						if let Some(value) = <#field_type as ::esexpr::ESExprOptionalFieldCodec>::encode_optional_field(#field_expr) {
							args.push(value);
						}
					}
                }
                else if let Some(default_value) = field_attr.default_value.as_ref() {
					let tags = quote! { <#field_type as ::esexpr::ESExprCodec>::TAGS };
					let checks = make_pos_tag_check(&field_name, &tags, &prev_optional_positional_tags);
					prev_optional_positional_tags.push(tags);

                    quote! {
						#checks
                        {
                            let value = #field_expr;
                            if !<#field_type as ::esexpr::ESExprEncodedEq>::is_encoded_eq(value, &#default_value) {
                                args.push(<#field_type as ::esexpr::ESExprCodec>::encode_esexpr(value));
                            }
                        }
                    }
                }
                else {
					let tags = quote! { <#field_type as ::esexpr::ESExprCodec>::TAGS };
					let checks = make_pos_tag_check(&field_name, &tags, &prev_optional_positional_tags);
					prev_optional_positional_tags.clear();
                    quote! {
						#checks
						args.push(::esexpr::ESExprCodec::encode_esexpr(#field_expr));
					}
                }
            }
        )
    }).collect::<Result<_, _>>()
}

fn get_esexpr_decode(attr: &ESExprTypeAttr, type_name: &Ident, data: &Data) -> TokenRes {
	Ok(match data {
		Data::Struct(s) => {
			let constructor_name = make_constructor_name(attr.constructor.as_ref(), type_name);

			let decode_fields = make_decode_fields(&s.fields, &constructor_name, quote! { #type_name })?;

			quote! {
				if let (::esexpr::ESExpr::Constructor(::esexpr::ESExprConstructor { name, args, kwargs })) = expr {
					let mut args = <::esexpr::core_types::alloc::collections::VecDeque<::esexpr::ESExpr<'_>> as ::esexpr::core_types::core::convert::From<::esexpr::core_types::alloc::vec::Vec<::esexpr::ESExpr<'_>>>>::from(
						<::esexpr::core_types::alloc::vec::Vec<::esexpr::ESExpr<'_>> as ::esexpr::core_types::core::convert::From<::esexpr::ConstructorArgs<'_>>>::from(args)
					);
					let mut arg_index = 0_usize;
					let mut kwargs = <::esexpr::core_types::alloc::collections::BTreeMap<::esexpr::cowstr::CowStr<'_>, ::esexpr::ESExpr<'_>> as ::esexpr::core_types::core::convert::From<::esexpr::KeywordArgs<'_>>>::from(kwargs);
					if name == #constructor_name {
						Ok(#decode_fields)
					}
					else {
						Err(::esexpr::DecodeError::new(
							::esexpr::DecodeErrorType::UnexpectedExpr {
								expected_tags: <Self as ::esexpr::ESExprCodec>::TAGS,
								actual_tag: ::esexpr::ESExprTag::Constructor(name.into_owned_cowstr()),
							},
							::esexpr::DecodeErrorPath::Current,
						))?
					}
				}
				else {
					Err(::esexpr::DecodeError::new(
						::esexpr::DecodeErrorType::UnexpectedExpr {
								expected_tags: <Self as ::esexpr::ESExprCodec>::TAGS,
							actual_tag: ::esexpr::ESExprTag::into_owned(::esexpr::ESExpr::tag(&expr)),
						},
						::esexpr::DecodeErrorPath::Current,
					))?
				}
			}
		},

		Data::Enum(e) if attr.simple_enum.is_present() => {
			let decode_cases: proc_macro2::TokenStream = e
				.variants
				.iter()
				.map(|c| -> TokenRes {
					let variant_attr =
						ESExprVariantAttr::from_attributes(&c.attrs).map_err(darling::Error::write_errors)?;

					let case_name = &c.ident;
					let case_name_str = &make_constructor_name(variant_attr.constructor.as_ref(), case_name);

					Ok(quote! {
						#case_name_str => Ok(#type_name::#case_name),
					})
				})
				.collect::<Result<_, _>>()?;

			let type_name_str = make_str_expr(&type_name.to_string());

			quote! {
				match expr {
					::esexpr::ESExpr::Str(s) => match s.as_ref() {
						#decode_cases
						_ => Err(::esexpr::DecodeError::new(
							::esexpr::DecodeErrorType::OutOfRange(::esexpr::core_types::alloc::format!("Invalid value for simple enum {}: {}", #type_name_str, s)),
							::esexpr::DecodeErrorPath::Current,
						)),
					},
					_ => {
						Err(::esexpr::DecodeError::new(
							::esexpr::DecodeErrorType::UnexpectedExpr {
								expected_tags: <Self as ::esexpr::ESExprCodec>::TAGS,
								actual_tag: ::esexpr::ESExprTag::into_owned(::esexpr::ESExpr::tag(&expr)),
							},
							::esexpr::DecodeErrorPath::Current,
						))
					},
				}
			}
		},

		Data::Enum(e) => {
			let decode_cases: proc_macro2::TokenStream = e
				.variants
				.iter()
				.map(|c| -> TokenRes {
					let case_name = &c.ident;

					let variant_attr = ESExprVariantAttr::from_attributes(&c.attrs)
						.map_err(darling::Error::write_errors)?;

					if variant_attr.inline_value.is_present() {
						let field = get_inline_value_field(c)?;
						let field_type = &field.ty;

						let value = quote! { <#field_type as ::esexpr::ESExprCodec>::decode_esexpr(expr)? };
						let case_value = if let Some(field_name) = &field.ident {
							quote! { #type_name::#case_name { #field_name: #value } }
						}
						else {
							quote! { #type_name::#case_name(#value) }
						};

						Ok(quote! {
							_ if <#field_type as ::esexpr::ESExprCodec>::TAGS.contains(&expr.tag()) => {
								::esexpr::core_types::core::result::Result::Ok(#case_value)
							},
						})
					}
					else {
						let name = make_constructor_name(variant_attr.constructor.as_ref(), case_name);
						let decode_fields = make_decode_fields(&c.fields, &name, quote! { #type_name::#case_name })?;
						Ok(quote! {
							::esexpr::ESExpr::Constructor(::esexpr::ESExprConstructor { name, args, kwargs }) if name == #name => {
								let mut args = <::esexpr::core_types::alloc::collections::VecDeque<::esexpr::ESExpr<'_>> as ::esexpr::core_types::core::convert::From<::esexpr::core_types::alloc::vec::Vec<::esexpr::ESExpr<'_>>>>::from(
									<::esexpr::core_types::alloc::vec::Vec<::esexpr::ESExpr<'_>> as ::esexpr::core_types::core::convert::From<::esexpr::ConstructorArgs<'_>>>::from(args)
								);
								let mut arg_index = 0_usize;
								let mut kwargs = <::esexpr::core_types::alloc::collections::BTreeMap<::esexpr::cowstr::CowStr<'_>, ::esexpr::ESExpr<'_>> as ::esexpr::core_types::core::convert::From<::esexpr::KeywordArgs<'_>>>::from(kwargs);
								::esexpr::core_types::core::result::Result::Ok(#decode_fields)
							},
						})
					}
				})
				.collect::<Result<_, _>>()?;

			quote! {
				match expr {
					#decode_cases
					_ => {
						Err(::esexpr::DecodeError::new(
							::esexpr::DecodeErrorType::UnexpectedExpr {
								expected_tags: <Self as ::esexpr::ESExprCodec>::TAGS,
								actual_tag: ::esexpr::ESExprTag::into_owned(::esexpr::ESExpr::tag(&expr)),
							},
							::esexpr::DecodeErrorPath::Current,
						))
					},
				}
			}
		},

		Data::Union(_) => Err(quote! { compile_error!("ESExprCodec cannot be derived for union"); })?,
	})
}

fn make_decode_fields(fields: &Fields, constructor_name: &str, constructor: proc_macro2::TokenStream) -> TokenRes {
	Ok(match fields {
		Fields::Named(fields) => {
			let field_init: proc_macro2::TokenStream = fields
				.named
				.iter()
				.map(|field| -> TokenRes {
					#[expect(clippy::unwrap_used, reason = "This is a named field, so it must have a name.")]
					let field_name = field.ident.as_ref().unwrap();
					let field_value = make_decode_field(field, constructor_name)?;
					Ok(quote! { #field_name: #field_value, })
				})
				.collect::<Result<_, _>>()?;

			quote! { #constructor { #field_init } }
		},
		Fields::Unnamed(fields) => {
			let field_init: proc_macro2::TokenStream = fields
				.unnamed
				.iter()
				.map(|field| -> TokenRes {
					let field_value = make_decode_field(field, constructor_name)?;
					Ok(quote! { #field_value, })
				})
				.collect::<Result<_, _>>()?;

			quote! { #constructor(#field_init) }
		},
		Fields::Unit => constructor,
	})
}

#[derive(Copy, Clone)]
enum FieldPath<'a> {
	Positional,
	Keyword(&'a str),
}

fn make_decode_field(field: &Field, constructor_name: &str) -> TokenRes {
	let field_type = &field.ty;

	let attr = ESExprFieldAttr::from_attributes(&field.attrs).map_err(darling::Error::write_errors)?;

	Ok(if let Some(keyword_attr) = attr.keyword {
		let kw = make_kwarg_name(keyword_attr.name.as_ref(), field.ident.as_ref())?;
		let error_mapping = make_error_mapping(constructor_name, FieldPath::Keyword(&kw));

		if attr.optional.is_present() {
			quote! {
				<#field_type as ::esexpr::ESExprOptionalFieldCodec>::decode_optional_field(kwargs.remove(#kw))
					.map_err(#error_mapping)?
			}
		}
		else if let Some(default_value) = attr.default_value.as_ref() {
			quote! {
				kwargs.remove(#kw)
					.map(<#field_type as ::esexpr::ESExprCodec>::decode_esexpr)
					.transpose()
					.map_err(#error_mapping)?
					.unwrap_or_else(|| #default_value)
			}
		}
		else {
			quote! {
				<#field_type as ::esexpr::ESExprCodec>::decode_esexpr(
					kwargs.remove(#kw).ok_or_else(|| ::esexpr::DecodeError::new(
						::esexpr::DecodeErrorType::MissingKeyword(::esexpr::core_types::alloc::string::String::from(#kw)),
						::esexpr::DecodeErrorPath::Constructor(::esexpr::core_types::alloc::string::String::from(#constructor_name))
					))?
				).map_err(#error_mapping)?
			}
		}
	}
	else if attr.dict.is_present() {
		quote! { <#field_type as ::esexpr::ESExprDictCodec>::decode_dict_element(&mut kwargs, #constructor_name)? }
	}
	else if attr.vararg.is_present() {
		quote! { <#field_type as ::esexpr::ESExprVarArgCodec>::decode_vararg_element(&mut args, #constructor_name, &mut arg_index)? }
	}
	else {
		let error_mapping = make_error_mapping(constructor_name, FieldPath::Positional);

		if attr.optional.is_present() {
			quote! {
				{
					let current_arg_index = arg_index;
					<#field_type as ::esexpr::ESExprOptionalFieldCodec>::decode_optional_field(
						if args.front().is_some_and(|e| <<#field_type as ::esexpr::ESExprOptionalFieldCodec>::Element as ::esexpr::ESExprCodec>::TAGS.contains(&e.tag())) {
							args.pop_front().inspect(|_| arg_index += 1)
						}
						else {
							None
						}
					)
						.map_err(#error_mapping)?
				}
			}
		}
		else if let Some(default_value) = attr.default_value.as_ref() {
			quote! {
				{
					let current_arg_index = arg_index;
					if args.front().is_some_and(|e| <#field_type as ::esexpr::ESExprCodec>::TAGS.contains(&e.tag())) {
						args.pop_front()
					}
					else {
						None
					}
						.map(
							|arg| {
								<#field_type as ::esexpr::ESExprCodec>::decode_esexpr(arg)
									.map_err(#error_mapping)
							}
						)
						.transpose()?
						.unwrap_or_else(|| #default_value)
				}
			}
		}
		else {
			quote! {
				if let Some(arg) = args.pop_front() {
					let current_arg_index = arg_index;
					arg_index += 1;
					<#field_type as ::esexpr::ESExprCodec>::decode_esexpr(arg).map_err(#error_mapping)?
				}
				else {
					Err(::esexpr::DecodeError::new(
						::esexpr::DecodeErrorType::MissingPositional,
						::esexpr::DecodeErrorPath::Constructor(::esexpr::core_types::alloc::string::String::from(#constructor_name))
					))?
				}
			}
		}
	})
}

fn make_error_mapping(constructor_name: &str, path: FieldPath) -> proc_macro2::TokenStream {
	match path {
		FieldPath::Positional => {
			quote! { |mut e| { e.error_path_with(|p| ::esexpr::DecodeErrorPath::Positional(::esexpr::core_types::alloc::string::String::from(#constructor_name), current_arg_index, ::esexpr::core_types::alloc::boxed::Box::new(p))); e } }
		},

		FieldPath::Keyword(name) => {
			quote! { |mut e| { e.error_path_with(|p| ::esexpr::DecodeErrorPath::Keyword(::esexpr::core_types::alloc::string::String::from(#constructor_name), ::esexpr::core_types::alloc::string::String::from(#name), ::esexpr::core_types::alloc::boxed::Box::new(p))); e } }
		},
	}
}

fn make_constructor_name(constructor: Option<&String>, ident: &Ident) -> String {
	match constructor {
		Some(name) => name.clone(),
		None => reformat_type_name(&ident.to_string()),
	}
}

fn make_kwarg_name(attr_name: Option<&String>, field_name: Option<&Ident>) -> Result<String, proc_macro2::TokenStream> {
	Ok(match attr_name {
		Some(name) => name.clone(),
		None => match field_name {
			Some(name) => reformat_field_name(&name.to_string()),
			None => Err(
				quote! { compile_error!("Keyword arguments for unnamed fields must specify a name: #[esexpr(keyword = \"name\")]"); },
			)?,
		},
	})
}

fn get_inline_value_field(c: &Variant) -> Result<&Field, proc_macro2::TokenStream> {
	match &c.fields {
		Fields::Named(fields) => {
			if fields.named.len() == 1 {
				Ok(&fields.named[0])
			}
			else {
				Err(quote! { compile_error!("Inline value case must have exactly one field"); })
			}
		},
		Fields::Unnamed(fields) => {
			if fields.unnamed.len() == 1 {
				Ok(&fields.unnamed[0])
			}
			else {
				Err(quote! { compile_error!("Inline value case must have exactly one field"); })
			}
		},
		Fields::Unit => Err(quote! { compile_error!("Unit case cannot be an inline_value"); }),
	}
}

// Convert name from PascalCase to kebab-case
fn reformat_type_name(name: &str) -> String {
	let mut last_dash = false;
	let mut last_upper = None;
	let mut res = String::new();

	for c in name.chars() {
		if c.is_ascii_lowercase() {
			if let Some(upper) = last_upper.take() {
				if !res.is_empty() && !last_dash {
					res.push('-');
				}
				res.push(upper);
			}
			res.push(c);
			last_dash = false;
		}
		else if c.is_ascii_uppercase() {
			if let Some(upper) = last_upper.take() {
				res.push(upper);
				last_dash = false;
			}
			else if !res.is_empty() && !last_dash {
				res.push('-');
				last_dash = true;
			}

			last_upper = Some(c.to_ascii_lowercase());
		}
		else {
			if let Some(upper) = last_upper.take() {
				res.push(upper);
			}

			res.push(c);
			last_dash = false;
		}
	}

	if let Some(upper) = last_upper.take() {
		res.push(upper);
	}

	res
}

fn reformat_field_name(name: &str) -> String {
	name.replace('_', "-")
}

fn make_str_expr(s: &str) -> Expr {
	Expr::Lit(ExprLit {
		attrs: vec![],
		lit: Lit::Str(LitStr::new(s, Span::mixed_site())),
	})
}

#[cfg(test)]
mod test {
	use quote::quote;
	use syn::parse::Parser;
	use syn::visit::{self, Visit};

	use super::reformat_type_name;

	#[test]
	fn reformat_str_test() {
		assert_eq!("test-abc", reformat_type_name("TestABC"));
		assert_eq!("test-name-with-parts", reformat_type_name("TestNameWithParts"));
		assert_eq!("test-abc-after", reformat_type_name("TestABCAfter"));
	}

	macro_rules! ensure_error {
		($message: expr, $def: item) => {
			check_error(
				$message,
				super::derive_esexpr_codec_impl(quote! {
					$def
				}),
			)
		};
	}

	fn check_error(message: &str, tokens: proc_macro2::TokenStream) {
		let mut checker = CompileErrorChecker {
			compile_error_messages: Vec::new(),
		};

		eprintln!("{}", tokens);

		checker.visit_item(&syn::parse2(tokens).unwrap());

		if checker.compile_error_messages.is_empty() {
			println!("No compile_error!s were found");
		}
		else {
			println!("Error messages:");
			for e in &checker.compile_error_messages {
				println!("{}", e);
			}
		}

		assert!(
			checker.compile_error_messages.iter().any(|s| s == message),
			"Expected error message \"{message}\" not found"
		);
	}

	struct CompileErrorChecker {
		compile_error_messages: Vec<String>,
	}

	impl<'a> visit::Visit<'a> for CompileErrorChecker {
		fn visit_macro(&mut self, mac: &'a syn::Macro) {
			if mac.path.get_ident().is_some_and(|i| i.to_string() == "compile_error") {
				let args = syn::punctuated::Punctuated::<syn::Expr, syn::Token![,]>::parse_terminated
					.parse2(mac.tokens.clone())
					.unwrap();

				match &args[0] {
					syn::Expr::Lit(syn::ExprLit {
						lit: syn::Lit::Str(s), ..
					}) => {
						self.compile_error_messages.push(s.value());
					},
					_ => panic!("Expected a string for compile_error!"),
				}
			}

			visit::visit_macro(self, mac)
		}
	}

	#[test]
	fn derive_union() {
		ensure_error!("ESExprCodec cannot be derived for union",
			union ConstructorNameEnum {
				a: i32,
				b: f32,
			}
		);
	}

	#[test]
	fn constructor_on_enum() {
		ensure_error!(
			"Constructor name may only be specified for structs and enum variants",
			#[esexpr(constructor = "my-ctor")]
			enum ConstructorNameEnum {
				MyName123Test,

				CustomName,
			}
		);
	}

	#[test]
	fn simple_enum_on_struct() {
		ensure_error!(
			"Struct cannot be a simple_enum.",
			#[esexpr(simple_enum)]
			struct ConstructorNameEnum(i32);
		);
	}

	#[test]
	fn kwarg_with_dict() {
		ensure_error!(
			"Keyword arguments cannot be used with dict arguments",
			struct MyStruct {
				#[esexpr(dict)]
				a: HashMap<String, String>,

				#[esexpr(keyword)]
				b: String,
			}
		);
		ensure_error!(
			"Keyword arguments cannot be used with dict arguments",
			struct MyStruct {
				#[esexpr(keyword)]
				b: String,

				#[esexpr(dict)]
				a: HashMap<String, String>,
			}
		);
	}

	#[test]
	fn multiple_dict() {
		ensure_error!(
			"Only a single dict argument is allowed",
			struct MyStruct {
				#[esexpr(dict)]
				a: HashMap<String, String>,

				#[esexpr(dict)]
				b: HashMap<String, String>,
			}
		);
	}

	#[test]
	fn inline_value_exactly_one() {
		ensure_error!(
			"Unit case cannot be an inline_value",
			enum MyEnum {
				#[esexpr(inline_value)]
				MyCase,
			}
		);

		ensure_error!(
			"Inline value case must have exactly one field",
			enum MyEnum {
				#[esexpr(inline_value)]
				MyCase(),
			}
		);

		ensure_error!(
			"Inline value case must have exactly one field",
			enum MyEnum {
				#[esexpr(inline_value)]
				MyCase(i32, i32),
			}
		);

		ensure_error!(
			"Inline value case must have exactly one field",
			enum MyEnum {
				#[esexpr(inline_value)]
				MyCase {},
			}
		);

		ensure_error!(
			"Inline value case must have exactly one field",
			enum MyEnum {
				#[esexpr(inline_value)]
				MyCase { a: i32, b: i32 },
			}
		);
	}

	#[test]
	fn keyword_arg_unnamed() {
		ensure_error!(
			"Keyword arguments for unnamed fields must specify a name: #[esexpr(keyword = \"name\")]",
			struct MyStruct(#[esexpr(keyword)] u32);
		);
	}

	#[test]
	fn default_value_dict() {
		ensure_error!(
			"Dictionary arguments cannot have default values.",
			struct MyStruct(#[esexpr(dict, default_value = 4)] HashMap<String, u32>);
		);
	}

	#[test]
	fn optional_value_dict() {
		ensure_error!(
			"Dictionary arguments cannot be optional.",
			struct MyStruct(#[esexpr(optional, dict)] HashMap<String, u32>);
		);
	}

	#[test]
	fn default_value_vararg() {
		ensure_error!(
			"Variable arguments cannot have default values.",
			struct MyStruct(#[esexpr(vararg, default_value = "4")] Vec<u32>);
		);
	}

	#[test]
	fn optional_value_vararg() {
		ensure_error!(
			"Variable arguments cannot be optional.",
			struct MyStruct(#[esexpr(optional, vararg)] Vec<u32>);
		);
	}

	#[test]
	fn default_value_optional() {
		ensure_error!(
			"Optional arguments cannot have default values.",
			struct MyStruct(#[esexpr(keyword = "a", optional, default_value = 4)] u32);
		);
	}

	#[test]
	fn default_value_optional_positional() {
		ensure_error!(
			"Optional arguments cannot have default values.",
			struct MyStruct(#[esexpr(optional, default_value = 4)] u32);
		);
	}

	#[test]
	fn keyword_arg_invalid() {
		ensure_error!(
			"Duplicate keyword argument \"a\"",
			struct MyStruct(#[esexpr(keyword = "a")] u32, #[esexpr(keyword = "a")] u32);
		);
		ensure_error!(
			"Keyword arguments cannot be dict values.",
			struct MyStruct(#[esexpr(dict, keyword = "x")] HashMap<String, String>);
		);
		ensure_error!(
			"Keyword arguments cannot be vararg values.",
			struct MyStruct(#[esexpr(vararg, keyword = "x")] HashMap<String, String>);
		);
	}
}
