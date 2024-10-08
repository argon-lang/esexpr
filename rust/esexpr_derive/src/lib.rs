use std::collections::HashSet;

use proc_macro::TokenStream;
use proc_macro2::Span;
use proc_macro2::Literal;
use quote::quote;
use syn::TraitBound;
use syn::TraitBoundModifier;
use syn::TypeParamBound;
use syn::{
    parse_quote,
    punctuated::Punctuated,
    Attribute,
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
    Meta,
    MetaList,
    MetaNameValue,
    Path,
    Token,
    Type,
    TypePath,
    Variant,
};

#[proc_macro_derive(ESExprCodec, attributes(constructor, inline_value, keyword, simple_enum, default_value, optional, dict, vararg))]
pub fn derive_esexpr_codec(input: TokenStream) -> TokenStream {
    TokenStream::from(derive_esexpr_codec_impl(proc_macro2::TokenStream::from(input)))
}

type TokenRes = Result<proc_macro2::TokenStream, proc_macro2::TokenStream>;

fn flatten_token_res(res: TokenRes) -> proc_macro2::TokenStream {
    match res {
        Ok(ts) => ts,
        Err(ts) => ts,
    }
}

fn derive_esexpr_codec_impl(input: proc_macro2::TokenStream) -> proc_macro2::TokenStream {
    let input: DeriveInput = parse_quote!(#input);

    let type_name = input.ident;


    let generics_params = input.generics.params;
    
    let generics_lt = input.generics.lt_token;
    let generics_gt = input.generics.gt_token;

    let type_args = generics_params.iter().map(param_to_arg).collect::<Punctuated<GenericArgument, Token![,]>>();

    let type_params =
        if generics_params.is_empty() {
            quote! {}
        }
        else {
            let params = generics_params.into_iter()
                .map(|mut p| {
                    match &mut p {
                        GenericParam::Type(p) => {
                            p.colon_token.get_or_insert(Default::default());
                            p.bounds.push(TypeParamBound::Trait(TraitBound {
                                paren_token: None,
                                modifier: TraitBoundModifier::None,
                                lifetimes: None,
                                path: parse_quote! { ::esexpr::ESExprCodec },
                            }))
                        },

                        GenericParam::Lifetime(_) => {},
                        GenericParam::Const(_) => {},
                    }

                    p
                })
                .collect::<Vec<_>>();

            quote! {
                <#(#params),*>
            }
        };


    let validate = flatten_token_res(validate_attributes(&input.attrs, &type_name, &input.data));
    let tags = flatten_token_res(get_esexpr_tag(&input.attrs, &type_name, &input.data));
    let encode = flatten_token_res(get_esexpr_encode(&input.attrs, &type_name, &input.data));
    let decode = flatten_token_res(get_esexpr_decode(&input.attrs, &type_name, &input.data));

    quote! {
        impl #type_params  ::esexpr::ESExprCodec for #type_name #generics_lt #type_args #generics_gt {

            fn tags() -> ::std::collections::HashSet<::esexpr::ESExprTag> {
                #validate
                #tags
            }

            fn encode_esexpr(self) -> ::esexpr::ESExpr {
                #encode
            }

            fn decode_esexpr(expr: ::esexpr::ESExpr) -> ::core::result::Result<Self, ::esexpr::DecodeError> {
                #decode
            }
        }
    }
}


fn validate_attributes(attrs: &[Attribute], _type_name: &Ident, data: &Data) -> TokenRes {
    match data {
        Data::Struct(s) => {
            if has_simple_enum_attribute(attrs)? {
                Err(quote! { compile_error!("Struct cannot be a simple_enum."); })?;
            }

            validate_fields(&s.fields)?;
        },
        Data::Enum(e) => {
            if constructor_attribute(attrs)?.is_some() {
                Err(quote! { compile_error!("Constructor name may only be specified for structs and enum cases"); })?;
            }

            for c in &e.variants {
                if has_simple_enum_attribute(&c.attrs)? {
                    Err(quote! { compile_error!("Enum case cannot be a simple_enum."); })?;
                }

                validate_fields(&c.fields)?;
            }
        },
        Data::Union(_) => (),
    }

    Ok(quote! {})
}

fn validate_fields(fields: &Fields) -> Result<(), proc_macro2::TokenStream> {
    let fields = match fields {
        Fields::Named(named) => &named.named,
        Fields::Unnamed(unnamed) => &unnamed.unnamed,
        Fields::Unit => return Ok(()),
    };

    for field in fields {
        if keyword_attribute(&field.attrs)?.is_some() {
            if has_dict_attribute(&field.attrs)? {
                return Err(quote! { compile_error!("Keyword arguments cannot be dict values."); });
            }

            if has_vararg_attribute(&field.attrs)? {
                return Err(quote! { compile_error!("Keyword arguments cannot be vararg values."); });
            }

            if has_optional_attribute(&field.attrs)? && has_default_value_attribute(&field.attrs)?.is_some() {
                return Err(quote! { compile_error!("Optional arguments cannot have default values."); });
            }
        }
        else if has_dict_attribute(&field.attrs)? {
            if has_vararg_attribute(&field.attrs)? {
                return Err(quote! { compile_error!("Dict arguments cannot be vararg values."); });
            }
            if has_optional_attribute(&field.attrs)? {
                return Err(quote! { compile_error!("Dictionary arguments cannot be optional."); });
            }

            if has_default_value_attribute(&field.attrs)?.is_some() {
                return Err(quote! { compile_error!("Dictionary arguments cannot have default values."); });
            }
        }
        else if has_vararg_attribute(&field.attrs)? {
            if has_optional_attribute(&field.attrs)? {
                return Err(quote! { compile_error!("Variable arguments cannot be optional."); });
            }

            if has_default_value_attribute(&field.attrs)?.is_some() {
                return Err(quote! { compile_error!("Variable arguments cannot have default values."); });
            }
        }
        else {
            if has_default_value_attribute(&field.attrs)?.is_some() {
                return Err(quote! { compile_error!("Positional arguments cannot have default values."); });
            }
        }
    }

    Ok(())
}


fn param_to_arg(p: &GenericParam) -> GenericArgument {
    match p {
        GenericParam::Lifetime(l) => GenericArgument::Lifetime(l.lifetime.clone()),
        GenericParam::Type(t) =>
            GenericArgument::Type(Type::Path(TypePath {
                qself: None,
                path: Path::from(t.ident.clone()),
            })),
        GenericParam::Const(c) =>
            GenericArgument::Const(Expr::Path(ExprPath {
                attrs: vec!(),
                qself: None,
                path: Path::from(c.ident.clone()),
            })),
    }
}


fn get_esexpr_tag(attrs: &[Attribute], type_name: &Ident, data: &Data) -> TokenRes {
    fn make_constructor_expr(name: &Expr) -> Expr {
        parse_quote! { ::esexpr::ESExprTag::Constructor(#name.to_owned()) }
    }

    fn make_set_of(e: Expr) -> proc_macro2::TokenStream {
        quote! {
            ::std::collections::HashSet::from([#e])
        }
    }

    Ok(match data {
        Data::Struct(_) => {
            make_set_of(make_constructor_expr(&make_constructor_name(attrs, type_name)?))
        },

        Data::Enum(_) if has_simple_enum_attribute(attrs)? =>
            make_set_of(parse_quote! { ::esexpr::ESExprTag::Str }),

        Data::Enum(e) => {
            let case_tags: proc_macro2::TokenStream = e.variants.iter().map(|c| -> TokenRes {
                if has_inline_value_attribute(&c.attrs)? {
                    let t = &get_inline_value_field(c)?.ty;
                    Ok(quote! { tags.extend(<#t as ::esexpr::ESExprCodec>::tags()); })
                }
                else {
                    let tag = make_constructor_expr(&make_constructor_name(&c.attrs, &c.ident)?);
                    Ok(quote! { tags.insert(#tag); })
                }
            }).collect::<Result<_, _>>()?;

            quote! {
                let mut tags = ::std::collections::HashSet::new();
                #case_tags
                tags
            }
        },

        Data::Union(_) => Err(quote! { compile_error!("ESExprCodec cannot be derived for union"); })?,
    })
}

fn get_esexpr_encode(attrs: &[Attribute], type_name: &Ident, data: &Data) -> TokenRes {
    Ok(match data {
        Data::Struct(s) => {
            let constructor_name = make_constructor_name(attrs, type_name)?;

            let encode_fields = make_encode_fields(
                &s.fields,
                |name, i| 
                    if let Some(name) = name {
                        quote! { self.#name }
                    }
                    else {
                        let i_ident = Index::from(i);
                        quote! { self.#i_ident }
                    }
            )?;

            quote! {
                let mut args = ::std::vec::Vec::<::esexpr::ESExpr>::new();
                let mut kwargs = ::std::collections::HashMap::<std::string::String, ::esexpr::ESExpr>::new();
                #encode_fields
                ::esexpr::ESExpr::Constructor { name: #constructor_name.to_owned(), args, kwargs }
            }
        },

        Data::Enum(e) if has_simple_enum_attribute(attrs)? => {
            let cases: proc_macro2::TokenStream = e.variants.iter().map(|c| -> TokenRes {
                let case_name = &c.ident;
                let case_name_str = &make_constructor_name(&c.attrs, case_name)?;

                Ok(quote! {
                    #type_name::#case_name => ::esexpr::ESExpr::Str(#case_name_str.to_owned()),
                })
            }).collect::<Result<_, _>>()?;


            quote! {
                match self {
                    #cases
                }
            }
        },

        Data::Enum(e) => {
            let cases: proc_macro2::TokenStream = e.variants.iter().map(|c| -> TokenRes {
                let case_name = &c.ident;

                fn make_field_name<'a>(name: Option<&'a Ident>, i: usize) -> proc_macro2::TokenStream {
                    let mut name =
                        if let Some(name) = name { name.to_string() }
                        else { i.to_string() };
                        
                    name.insert_str(0, "field_");
                    let name = Ident::new(&name, Span::mixed_site());
                    quote! { #name }
                }

                let pattern = match &c.fields {
                    Fields::Named(fields) => {
                        let field_patterns: proc_macro2::TokenStream = fields.named.iter().enumerate().map(|(i, field)| {
                            let orig_name = field.ident.as_ref().unwrap();
                            let mapped_name = make_field_name(field.ident.as_ref(), i);
                            quote! { #orig_name: #mapped_name, }
                        }).collect();
                        
                        quote! {
                            #type_name::#case_name { #field_patterns }
                        }
                    },

                    Fields::Unnamed(fields) => {
                        let field_patterns: proc_macro2::TokenStream = (0..fields.unnamed.len()).into_iter().map(|i| {
                            let mapped_name = make_field_name(None, i);
                            quote! { #mapped_name, }
                        }).collect();
                        
                        quote! {
                            #type_name::#case_name(#field_patterns)
                        }
                    },

                    Fields::Unit => quote! { #type_name::#case_name },
                };

                if has_inline_value_attribute(&c.attrs)? {
                    let field = get_inline_value_field(c)?;
                    let field_name = make_field_name(field.ident.as_ref(), 0);
                    let field_type = &field.ty;

                    Ok(quote! {
                        #pattern => {
                            <#field_type as ::esexpr::ESExprCodec>::encode_esexpr(#field_name)
                        }
                    })
                }
                else {
                    let constructor_name = make_constructor_name(&c.attrs, case_name)?;
                    
                    let encode_fields = make_encode_fields(&c.fields, make_field_name)?;
    
                    Ok(quote! {
                        #pattern => {
                            let mut args = ::std::vec::Vec::<::esexpr::ESExpr>::new();
                            let mut kwargs = ::std::collections::HashMap::<::std::string::String, ::esexpr::ESExpr>::new();
                            #encode_fields
                            ::esexpr::ESExpr::Constructor { name: #constructor_name.to_owned(), args, kwargs }
                        }
                    })
                }

            }).collect::<Result<_, _>>()?;

            quote! {
                match self {
                    #cases
                }
            }
        },

        Data::Union(_) => Err(quote! { compile_error!("ESExprCodec cannot be derived for union"); })?,
    })
}

fn make_encode_fields<'a, F: Fn(Option<&'a Ident>, usize) -> proc_macro2::TokenStream>(fields: &'a Fields, make_field_expr: F) -> TokenRes {
    let fields = match fields {
        Fields::Named(fields) => fields.named.iter().collect(),
        Fields::Unnamed(fields) => fields.unnamed.iter().collect(),
        Fields::Unit => Vec::new(),
    };

    let mut has_dict_field = false;
    let mut has_vararg_field = false;
    let mut kwarg_names = HashSet::new();
    let mut has_optional_positional = false;


    fields.into_iter().enumerate().map(|(i, field)| -> TokenRes {
        let field_expr = make_field_expr(field.ident.as_ref(), i);
        let field_type = &field.ty;

        Ok(
            if let Some(keyword_attr) = keyword_attribute(&field.attrs)? {
                if has_dict_field {
                    Err(quote! { compile_error!("Keyword arguments must preceed dict arguments"); })?;
                }
        
                let kw = make_kwarg_name(keyword_attr.as_deref(), field.ident.as_ref())?;
                let kw_name = get_string_expr_value(&kw)?;
                if kwarg_names.contains(&kw_name) {
                    let message = make_str_expr(&format!("Duplicate keyword argument \"{}\"", kw_name));
                    Err(quote! { compile_error!(#message); })?;
                }

                kwarg_names.insert(kw_name);

                if has_optional_attribute(&field.attrs)? {
                    quote! { if let Some(value) = <#field_type as ::esexpr::ESExprOptionalFieldCodec>::encode_optional_field(#field_expr) { kwargs.insert(#kw.to_owned(), value); } }
                }
                else if let Some(default_value) = has_default_value_attribute(&field.attrs)? {
                    quote! {
                        {
                            let value = #field_expr;
                            if value != #default_value {
                                kwargs.insert(#kw.to_owned(), <#field_type as ::esexpr::ESExprCodec>::encode_esexpr(value));
                            }
                        }
                    }
                }
                else {
                    quote! { kwargs.insert(#kw.to_owned(), <#field_type as ::esexpr::ESExprCodec>::encode_esexpr(#field_expr)); }
                }
            }
            else if has_dict_attribute(&field.attrs)? {
                if has_dict_field {
                    Err(quote! { compile_error!("Only a single dict argument is allowed"); })?;
                }
                has_dict_field = true;
        
                quote! { ::esexpr::ESExprDictCodec::encode_dict_element(#field_expr, &mut kwargs); }
            }
            else if has_vararg_attribute(&field.attrs)? {
                if has_vararg_field {
                    Err(quote! { compile_error!("Only a single vararg is allowed"); })?;
                }
                has_vararg_field = true;

                if has_optional_positional {
                    Err(quote! { compile_error!("Variable arguments cannot follow optional positional arguments."); })?;
                }
        
                quote! { ::esexpr::ESExprVarArgCodec::encode_vararg_element(#field_expr, &mut args); }
            }
            else {
                if has_vararg_field {
                    Err(quote! { compile_error!("Positional arguments must precede varargs"); })?;
                }

                if has_optional_attribute(&field.attrs)? {
                    if has_optional_positional {
                        Err(quote! { compile_error!("Only a single optional positional argument is allowed."); })?;
                    }

                    has_optional_positional = true;
                    quote! { if let Some(value) = <#field_type as ::esexpr::ESExprOptionalFieldCodec>::encode_optional_field(#field_expr) { args.push(value); } }
                }
                else if let Some(default_value) = has_default_value_attribute(&field.attrs)? {
                    has_optional_positional = true;
                    quote! {
                        {
                            let value = #field_expr;
                            if value != #default_value {
                                args.push(<#field_type as ::esexpr::ESExprCodec>::encode_esexpr(value));
                            }
                        }
                    }
                }
                else {
                    if has_optional_positional {
                        Err(quote! { compile_error!("Required positional arguments cannot follow optional positional arguments."); })?;
                    }

                    quote! { args.push(::esexpr::ESExprCodec::encode_esexpr(#field_expr)); }
                }
            }
        )
    }).collect::<Result<_, _>>()
}

fn get_esexpr_decode(attrs: &[Attribute], type_name: &Ident, data: &Data) -> TokenRes {
    Ok(match data {
        Data::Struct(s) => {
            let constructor_name = make_constructor_name(attrs, type_name)?;

            let decode_fields = make_decode_fields(&s.fields, &constructor_name, quote! { #type_name })?;

            quote! {
                if let (::esexpr::ESExpr::Constructor { name, mut args, mut kwargs }) = expr {
                    if name == #constructor_name {
                        Ok(#decode_fields)
                    }
                    else {
                        Err(::esexpr::DecodeError(
                            ::esexpr::DecodeErrorType::UnexpectedExpr {
                                expected_tags: Self::tags(),
                                actual_tag: ::esexpr::ESExprTag::Constructor(name),
                            },
                            ::esexpr::DecodeErrorPath::Current,
                        ))?
                    }
                }
                else {
                    Err(::esexpr::DecodeError(
                        ::esexpr::DecodeErrorType::UnexpectedExpr {
                            expected_tags: Self::tags(),
                            actual_tag: expr.tag(),
                        },
                        ::esexpr::DecodeErrorPath::Current,
                    ))?
                }
            }
        },

        Data::Enum(e) if has_simple_enum_attribute(attrs)? => {
            let decode_cases: proc_macro2::TokenStream = e.variants.iter().map(|c| -> TokenRes {
                let case_name = &c.ident;
                let case_name_str = &make_constructor_name(&c.attrs, case_name)?;

                Ok(quote! {
                    #case_name_str => Ok(#type_name::#case_name),
                })
            }).collect::<Result<_, _>>()?;

            let type_name_str = make_str_expr(&type_name.to_string());

            quote! {
                match expr {
                    ::esexpr::ESExpr::Str(s) => match s.as_str() {
                        #decode_cases
                        _ => Err(::esexpr::DecodeError(
                            ::esexpr::DecodeErrorType::OutOfRange(format!("Invalid value for simple enum {}: {}", #type_name_str, s)),
                            ::esexpr::DecodeErrorPath::Current,
                        )),
                    },
                    _ => {
                        Err(::esexpr::DecodeError(
                            ::esexpr::DecodeErrorType::UnexpectedExpr { expected_tags: Self::tags(), actual_tag: expr.tag() },
                            ::esexpr::DecodeErrorPath::Current,
                        ))
                    },
                }
            }
        },

        Data::Enum(e) => {

            let decode_cases: proc_macro2::TokenStream = e.variants.iter().map(|c| -> TokenRes {
                let case_name = &c.ident;

                if has_inline_value_attribute(&c.attrs)? {
                    let field = get_inline_value_field(c)?;
                    let field_type = &field.ty;

                    let value = quote! { <#field_type as ::esexpr::ESExprCodec>::decode_esexpr(expr)? };
                    let case_value =
                        if let Some(field_name) = &field.ident {
                            quote! { #type_name::#case_name { #field_name: #value } }
                        }
                        else {
                            quote! { #type_name::#case_name(#value) }
                        };

                    Ok(quote! {
                        _ if <#field_type as ::esexpr::ESExprCodec>::tags().contains(&expr.tag()) => {
                            ::std::result::Result::Ok(#case_value)
                        },
                    })
                }
                else {
                    let name = make_constructor_name(&c.attrs, case_name)?;
                    let decode_fields = make_decode_fields(&c.fields, &name, quote! { #type_name::#case_name })?;
                    Ok(quote! {
                        ::esexpr::ESExpr::Constructor { name, mut args, mut kwargs } if name == #name => {
                            ::std::result::Result::Ok(#decode_fields)
                        },
                    })
                }
            }).collect::<Result<_, _>>()?;

            quote! {
                match expr {
                    #decode_cases
                    _ => {
                        Err(::esexpr::DecodeError(
                            ::esexpr::DecodeErrorType::UnexpectedExpr { expected_tags: Self::tags(), actual_tag: expr.tag() },
                            ::esexpr::DecodeErrorPath::Current,
                        ))  
                    },
                }
            }
        },

        Data::Union(_) => Err(quote! { compile_error!("ESExprCodec cannot be derived for union"); })?,
    })
}

fn make_decode_fields(fields: &Fields, constructor_name: &Expr, constructor: proc_macro2::TokenStream) -> TokenRes {
    let mut arg_index = 0;
    Ok(match fields {
        Fields::Named(fields) => {
            let field_init: proc_macro2::TokenStream = fields.named.iter().map(|field| -> TokenRes {
                let field_name = field.ident.as_ref().unwrap();
                let field_value = make_decode_field(field, &mut arg_index, constructor_name)?;
                Ok(quote! { #field_name: #field_value, })
            }).collect::<Result<_, _>>()?;

            quote! { #constructor { #field_init } }
        },
        Fields::Unnamed(fields) => {
            let field_init: proc_macro2::TokenStream = fields.unnamed.iter().map(|field| -> TokenRes {
                let field_value = make_decode_field(field, &mut arg_index, constructor_name)?;
                Ok(quote! { #field_value, })
            }).collect::<Result<_, _>>()?;

            quote! { #constructor(#field_init) }
        },
        Fields::Unit => constructor,
    })
}

enum FieldPath<'a> {
    Positional(usize),
    Keyword(&'a Expr),
}

fn make_decode_field(field: &Field, arg_index: &mut usize, constructor_name: &Expr) -> TokenRes {
    let field_type = &field.ty;

    Ok(
        if let Some(keyword_attr) = keyword_attribute(&field.attrs)? {
            let kw = make_kwarg_name(keyword_attr.as_deref(), field.ident.as_ref())?;
            let error_mapping = make_error_mapping(constructor_name, FieldPath::Keyword(&kw));

            if has_optional_attribute(&field.attrs)? {
                quote! {
                    <#field_type as ::esexpr::ESExprOptionalFieldCodec>::decode_optional_field(kwargs.remove(#kw))
                        .map_err(#error_mapping)?
                }
                
            }
            else if let Some(default_value) = has_default_value_attribute(&field.attrs)? {
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
                        kwargs.remove(#kw).ok_or_else(|| ::esexpr::DecodeError(
                            ::esexpr::DecodeErrorType::MissingKeyword(#kw.to_owned()),
                            ::esexpr::DecodeErrorPath::Constructor(#constructor_name.to_owned())
                        ))?
                    ).map_err(#error_mapping)?
                }
            }
        }
        else if has_dict_attribute(&field.attrs)? {
            quote! { <#field_type as ::esexpr::ESExprDictCodec>::decode_dict_element(&mut kwargs, #constructor_name)? }
        }
        else if has_vararg_attribute(&field.attrs)? {
            let i_expr = Literal::usize_suffixed(*arg_index);
            quote! { <#field_type as ::esexpr::ESExprVarArgCodec>::decode_vararg_element(&mut args, #constructor_name, #i_expr)? }
        }
        else {
            let error_mapping = make_error_mapping(constructor_name, FieldPath::Positional(*arg_index));

            if has_optional_attribute(&field.attrs)? {
                quote! {
                    <#field_type as ::esexpr::ESExprOptionalFieldCodec>::decode_optional_field(
                        if args.is_empty() {
                            std::option::Option::None
                        }
                        else {
                            std::option::Option::Some(args.remove(0))
                        }
                    ).map_err(#error_mapping)?

                }
                
            }
            else if let Some(default_value) = has_default_value_attribute(&field.attrs)? {
                quote! {
                    if args.is_empty() {
                        #default_value
                    }
                    else {
                        <#field_type as ::esexpr::ESExprCodec>::decode_esexpr(args.remove(0))
                            .map_err(#error_mapping)?
                    }
                }
            }
            else {
                quote! {
                    if args.is_empty() {
                        Err(::esexpr::DecodeError(
                            ::esexpr::DecodeErrorType::MissingPositional,
                            ::esexpr::DecodeErrorPath::Constructor(#constructor_name.to_owned())
                        ))?
                    }
                    else {
                        <#field_type as ::esexpr::ESExprCodec>::decode_esexpr(args.remove(0)).map_err(#error_mapping)?
                    }
                }
            }

        }
    )
}



fn make_error_mapping(constructor_name: &Expr, path: FieldPath) -> proc_macro2::TokenStream {
    match path {
        FieldPath::Positional(i) => {
            let i_expr = Literal::usize_suffixed(i);
            quote! { |mut e| { e.1 = ::esexpr::DecodeErrorPath::Positional(#constructor_name.to_owned(), #i_expr, Box::new(e.1)); e } }
        },

        FieldPath::Keyword(name) => {
            quote! { |mut e| { e.1 = ::esexpr::DecodeErrorPath::Keyword(#constructor_name.to_owned(), #name.to_owned(), Box::new(e.1)); e } }
        }
    }
}



fn make_constructor_name(attrs: &[Attribute], type_name: &Ident) -> Result<Expr, proc_macro2::TokenStream> {
    if let Some(ctor) = constructor_attribute(attrs)? {
        Ok(ctor.clone())
    }
    else {
        Ok(make_str_expr(&reformat_type_name(&type_name.to_string())))
    }
}

fn make_kwarg_name(attr_name: Option<&Expr>, field_name: Option<&Ident>) -> Result<Expr, proc_macro2::TokenStream> {
    Ok(match attr_name {
        Some(name) => name.clone(),
        None =>
            match field_name {
                Some(name) => make_str_expr(&reformat_field_name(&name.to_string())),
                None => Err(quote! { compile_error!("Keyword arguments for unnamed fields must specifiy a name: #[keyword = \"name\"]"); })?,
            },
    })
}

fn get_string_expr_value(e: &Expr) -> Result<String, proc_macro2::TokenStream> {
    match e {
        Expr::Lit(ExprLit { lit: Lit::Str(lit_str), .. }) => Ok(lit_str.value()),
        _ => Err(quote! { compile_error!("Expected a string literal"); }),
    }
}




enum DecodedAttribute<'a> {
    Simple,
    NameValue(&'a Expr),
    ArgList,
}

fn decode_attr<'a>(name: &str, attrs: &'a [Attribute]) -> Result<Option<DecodedAttribute<'a>>, proc_macro2::TokenStream> {
    fn try_decode_attr<'a>(name: &str, attr: &'a Attribute) -> Result<Option<DecodedAttribute<'a>>, proc_macro2::TokenStream> {
        match &attr.meta {
            Meta::NameValue(MetaNameValue { path, value, .. }) if path.get_ident().is_some_and(|i| i.to_string() == name) =>
                Ok(Some(DecodedAttribute::NameValue(value))),

            Meta::List(MetaList { path, .. }) if path.get_ident().is_some_and(|i| i.to_string() == name) => {                
                Ok(Some(DecodedAttribute::ArgList))
            },
            
            Meta::Path(p) if p.get_ident().is_some_and(|i| i.to_string() == name) =>
                Ok(Some(DecodedAttribute::Simple)),

            _ => Ok(None),
        }
    }

    let mut attrs = attrs.iter().filter_map(|attr| {
        try_decode_attr(name, attr).transpose()
    }).collect::<Result<Vec<_>, _>>()?;

    if attrs.len() > 1 {
        let msg = make_str_expr(&format!("Attribute {} may only be specified once.", name));
        Err(quote! { compile_error!(#msg); })
    }
    else {
        Ok(attrs.pop())
    }
}

fn has_simple_attribute(name: &str, attrs: &[Attribute]) -> Result<bool, proc_macro2::TokenStream> {
    decode_attr(name, attrs)?.map(|attr| {
        match attr {
            DecodedAttribute::Simple => Ok(()),
            _ => {
                let msg = make_str_expr(&format!("Attribute {} must be a simple attribute", name));
                Err(quote! { compile_error!(#msg); })?
            }
        }
    }).transpose().map(|o| o.is_some())
}

fn get_name_value_attribute<'a>(name: &str, attrs: &'a [Attribute]) -> Result<Option<&'a Expr>, proc_macro2::TokenStream> {
    decode_attr(name, attrs)?.map(|attr| {
        match attr {
            DecodedAttribute::NameValue(value) => Ok(value),
            _ => {
                let msg = make_str_expr(&format!("Attribute {} must be a name-value attribute", name));
                Err(quote! { compile_error!(#msg); })?
            }
        }
    }).transpose()
}

fn get_name_optional_value_attribute<'a>(name: &str, attrs: &'a [Attribute]) -> Result<Option<Option<&'a Expr>>, proc_macro2::TokenStream> {
    decode_attr(name, attrs)?.map(|attr| {
        match attr {
            DecodedAttribute::Simple => Ok(None),
            DecodedAttribute::NameValue(value) => Ok(Some(value)),
            _ => {
                let msg = make_str_expr(&format!("Attribute {} must be a simple or name-value attribute", name));
                Err(quote! { compile_error!(#msg); })?
            }
        }
    }).transpose()
}


fn constructor_attribute(attrs: &[Attribute]) -> Result<Option<&Expr>, proc_macro2::TokenStream> {
    get_name_value_attribute("constructor", attrs)
}


fn keyword_attribute(attrs: &[Attribute]) -> Result<Option<Option<&Expr>>, proc_macro2::TokenStream> {
    get_name_optional_value_attribute("keyword", attrs)
}


fn has_simple_enum_attribute(attrs: &[Attribute]) -> Result<bool, proc_macro2::TokenStream> {
    has_simple_attribute("simple_enum", attrs)
}

fn has_default_value_attribute(attrs: &[Attribute]) -> Result<Option<Expr>, proc_macro2::TokenStream> {
    let Some(expr_str_expr) = get_name_value_attribute("default_value", attrs)? else { return Ok(None); };

    let expr_str = get_string_expr_value(expr_str_expr)?;

    syn::parse_str::<Expr>(&expr_str)
        .map(Some)
        .map_err(|_| quote! { compile_error!("Default value must be a string containing a valid expression of the field type") })
}

fn has_optional_attribute(attrs: &[Attribute]) -> Result<bool, proc_macro2::TokenStream> {
    has_simple_attribute("optional", attrs)
}

fn has_inline_value_attribute(attrs: &[Attribute]) -> Result<bool, proc_macro2::TokenStream> {
    has_simple_attribute("inline_value", attrs)
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
        Fields::Unit => {
            Err(quote! { compile_error!("Unit case cannot be an inline_value"); })
        },   
    }
}

fn has_dict_attribute(attrs: &[Attribute]) -> Result<bool, proc_macro2::TokenStream> {
    has_simple_attribute("dict", attrs)
}

fn has_vararg_attribute(attrs: &[Attribute]) -> Result<bool, proc_macro2::TokenStream> {
    has_simple_attribute("vararg", attrs)
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
    name.replace("_", "-")
}




fn make_str_expr(s: &str) -> Expr {
    Expr::Lit(ExprLit {
        attrs: vec!(),
        lit: Lit::Str(LitStr::new(s, Span::mixed_site())),
    })
}



#[cfg(test)]
mod test {
    use quote::quote;
    use crate::reformat_type_name;
    use syn::{
        parse::Parser,
        visit::{self, Visit}
    };


    #[test]
    fn reformat_str_test() {
        assert_eq!("test-abc", reformat_type_name("TestABC"));
        assert_eq!("test-name-with-parts", reformat_type_name("TestNameWithParts"));
        assert_eq!("test-abc-after", reformat_type_name("TestABCAfter"));
    }

    macro_rules! ensure_error {
        ($message: expr, $def: item) => {
            check_error($message, crate::derive_esexpr_codec_impl(quote! {
                $def
            }))
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

        assert!(checker.compile_error_messages.iter().any(|s| s == message));
    }

    struct CompileErrorChecker {
        compile_error_messages: Vec<String>,
    }

    impl <'a> visit::Visit<'a> for CompileErrorChecker {
        fn visit_macro(&mut self, mac: &'a syn::Macro) {
            if mac.path.get_ident().is_some_and(|i| i.to_string() == "compile_error") {
                
                let args =
                    syn::punctuated::Punctuated::<syn::Expr, syn::Token![,]>::parse_terminated
                    .parse2(mac.tokens.clone())
                    .unwrap();

                match &args[0] {
                    syn::Expr::Lit(syn::ExprLit { lit: syn::Lit::Str(s), .. }) => {
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
        ensure_error!("Constructor name may only be specified for structs and enum cases",
            #[constructor = "my-ctor"]
            enum ConstructorNameEnum {
                MyName123Test,

                CustomName,
            }
        );
    }

    #[test]
    fn simple_enum_on_struct() {
        ensure_error!("Struct cannot be a simple_enum.",
            #[simple_enum]
            struct ConstructorNameEnum(i32);
        );
    }

    #[test]
    fn simple_enum_on_case() {
        ensure_error!("Enum case cannot be a simple_enum.",
            enum ConstructorNameEnum {
                #[simple_enum]
                MyCase,
            }
        );
    }

    #[test]
    fn kwarg_after_dict() {
        ensure_error!("Keyword arguments must preceed dict arguments",
            struct MyStruct {
                #[dict]
                a: HashMap<String, String>,

                #[keyword]
                b: String,
            }
        );
    }

    #[test]
    fn multiple_dict() {
        ensure_error!("Only a single dict argument is allowed",
            struct MyStruct {
                #[dict]
                a: HashMap<String, String>,

                #[dict]
                b: HashMap<String, String>,
            }
        );
    }

    #[test]
    fn multiple_vararg() {
        ensure_error!("Only a single vararg is allowed",
            struct MyStruct {
                #[vararg]
                a: Vec<String>,

                #[vararg]
                b: Vec<String>,
            }
        );
    }

    #[test]
    fn arg_after_vararg() {
        ensure_error!("Positional arguments must precede varargs",
            struct MyStruct {
                #[vararg]
                a: Vec<String>,

                b: String,
            }
        );
    }

    #[test]
    fn inline_value_exactly_one() {
        ensure_error!("Unit case cannot be an inline_value",
            enum MyEnum {
                #[inline_value]
                MyCase,
            }
        );

        ensure_error!("Inline value case must have exactly one field",
            enum MyEnum {
                #[inline_value]
                MyCase(),
            }
        );

        ensure_error!("Inline value case must have exactly one field",
            enum MyEnum {
                #[inline_value]
                MyCase(i32, i32),
            }
        );

        ensure_error!("Inline value case must have exactly one field",
            enum MyEnum {
                #[inline_value]
                MyCase {},
            }
        );

        ensure_error!("Inline value case must have exactly one field",
            enum MyEnum {
                #[inline_value]
                MyCase { a: i32, b: i32 },
            }
        );
    }

    #[test]
    fn keyword_arg_unnamed() {
        ensure_error!("Keyword arguments for unnamed fields must specifiy a name: #[keyword = \"name\"]",
            struct MyStruct(#[keyword] u32);
        );
    }

    #[test]
    fn default_value_positional() {
        ensure_error!("Positional arguments cannot have default values.",
            struct MyStruct(#[default_value = "4"] u32);
        );
    }

    #[test]
    fn required_after_optional_positional() {
        ensure_error!("Required positional arguments cannot follow optional positional arguments.",
            struct MyStruct(#[optional] u32, u32);
        );
    }

    #[test]
    fn multiple_optional_positional() {
        ensure_error!("Only a single optional positional argument is allowed.",
            struct MyStruct(#[optional] u32, #[optional] u32);
        );
    }

    #[test]
    fn vararg_after_optional_positional() {
        ensure_error!("Variable arguments cannot follow optional positional arguments.",
            struct MyStruct(#[optional] u32, #[vararg] u32);
        );
    }

    #[test]
    fn default_value_dict() {
        ensure_error!("Dictionary arguments cannot have default values.",
            struct MyStruct(#[default_value = "4"] #[dict] HashMap<String, u32>);
        );
    }

    #[test]
    fn optional_value_dict() {
        ensure_error!("Dictionary arguments cannot be optional.",
            struct MyStruct(#[optional] #[dict] HashMap<String, u32>);
        );
    }

    #[test]
    fn default_value_vararg() {
        ensure_error!("Variable arguments cannot have default values.",
            struct MyStruct(#[default_value = "4"] #[vararg] Vec<u32>);
        );
    }

    #[test]
    fn optional_value_vararg() {
        ensure_error!("Variable arguments cannot be optional.",
            struct MyStruct(#[optional] #[vararg] Vec<u32>);
        );
    }

    #[test]
    fn default_value_optional() {
        ensure_error!("Optional arguments cannot have default values.",
            struct MyStruct(#[keyword = "a"] #[optional] #[default_value = "4"] u32);
        );
    }

    #[test]
    fn constructor_invalid() {
        ensure_error!("Attribute constructor may only be specified once.",
            #[constructor = "a"]
            #[constructor = "b"]
            struct MyStruct(u32);
        );
        ensure_error!("Attribute constructor must be a name-value attribute",
            #[constructor]
            struct MyStruct(u32);
        );
        ensure_error!("Attribute constructor must be a name-value attribute",
            #[constructor(name = "my-ctor")]
            struct MyStruct(u32);
        );
    }

    #[test]
    fn keyword_arg_invalid() {
        ensure_error!("Attribute keyword may only be specified once.",
            struct MyStruct(#[keyword] #[keyword = "b"] u32);
        );
        ensure_error!("Duplicate keyword argument \"a\"",
            struct MyStruct(#[keyword = "a"] u32, #[keyword = "a"] u32);
        );
        ensure_error!("Keyword arguments cannot be dict values.",
            struct MyStruct(#[dict] #[keyword = "x"] HashMap<String, String>);
        );
        ensure_error!("Keyword arguments cannot be vararg values.",
            struct MyStruct(#[vararg] #[keyword = "x"] HashMap<String, String>);
        );
    }

    #[test]
    fn inline_value_invalid() {
        ensure_error!("Attribute inline_value may only be specified once.",
            enum MyEnum {
                #[inline_value]
                #[inline_value]
                MyCase(i32),
            }
        );
        ensure_error!("Attribute inline_value must be a simple attribute",
            enum MyEnum {
                #[inline_value = true]
                MyCase(i32),
            }
        );
        ensure_error!("Attribute inline_value must be a simple attribute",
            enum MyEnum {
                #[inline_value()]
                MyCase(i32),
            }
        );
    }

    #[test]
    fn simple_enum_invalid() {
        ensure_error!("Attribute simple_enum may only be specified once.",
        #[simple_enum]
        #[simple_enum]
            enum MyEnum {
                MyCase(i32),
            }
        );
        ensure_error!("Attribute simple_enum must be a simple attribute",
        #[simple_enum = true]
            enum MyEnum {
                MyCase(i32),
            }
        );
        ensure_error!("Attribute simple_enum must be a simple attribute",
        #[simple_enum(x = 1)]
            enum MyEnum {
                MyCase(i32),
            }
        );
    }

    #[test]
    fn default_value() {
        ensure_error!("Attribute default_value may only be specified once.",
            struct MyStruct(#[keyword = "name"] #[default_value = "1"] #[default_value = "2"] u32);
        );
        ensure_error!("Attribute default_value must be a name-value attribute",
            struct MyStruct(#[keyword = "name"] #[default_value] u32);
        );
        ensure_error!("Attribute default_value must be a name-value attribute",
            struct MyStruct(#[keyword = "name"] #[default_value()] u32);
        );
    }

    #[test]
    fn dict_invalid() {
        ensure_error!("Attribute dict may only be specified once.",
            struct MyStruct(#[dict] #[dict] HashMap<String, String>);
        );
        ensure_error!("Attribute dict must be a simple attribute",
        #[simple_enum = true]
            struct MyStruct(#[dict = 1] HashMap<String, String>);
        );
        ensure_error!("Attribute dict must be a simple attribute",
            struct MyStruct(#[dict()] HashMap<String, String>);
        );
        ensure_error!("Dict arguments cannot be vararg values.",
            struct MyStruct(#[dict] #[vararg] HashMap<String, String>);
        );
    }

    #[test]
    fn vararg_invalid() {
        ensure_error!("Attribute vararg may only be specified once.",
            struct MyStruct(#[vararg] #[vararg] Vec<String>);
        );
        ensure_error!("Attribute vararg must be a simple attribute",
            struct MyStruct(#[vararg = 1] Vec<String>);
        );
        ensure_error!("Attribute vararg must be a simple attribute",
            struct MyStruct(#[vararg()] Vec<String>);
        );
    }
    

}


