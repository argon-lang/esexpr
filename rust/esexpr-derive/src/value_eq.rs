use proc_macro2::TokenStream;
use quote::quote;
use syn::{Data, DeriveInput, Fields, GenericParam, TypeParamBound, parse_quote};

pub fn derive_value_eq_impl(input: TokenStream) -> TokenStream {
	// Parse the input tokens into a syntax tree
	let input: DeriveInput = parse_quote!(#input);
	let name = &input.ident;
	let generics = input.generics;

	// Split generics for use in the impl
	let (_impl_generics, ty_generics, _where_clause) = generics.split_for_impl();

	// Add ValueEq bounds to type parameters
	let mut generics_with_bounds = generics.clone();
	for param in &mut generics_with_bounds.params {
		if let GenericParam::Type(type_param) = param {
			type_param
				.bounds
				.push(TypeParamBound::Trait(syn::parse_quote!(ValueEq)));
		}
	}
	let (bounded_impl_generics, _, bounded_where_clause) = generics_with_bounds.split_for_impl();

	// Generate the implementation based on the data type
	match input.data {
		Data::Struct(data_struct) => match data_struct.fields {
			Fields::Named(ref fields) => {
				let field_eq = fields.named.iter().map(|field| {
					#[expect(clippy::unwrap_used, reason = "We know this is a named field")]
					let field_name = field.ident.as_ref().unwrap();
					let field_type = &field.ty;
					quote! {
						if !<#field_type as ::esexpr::ValueEq>::value_eq(&self.#field_name, &other.#field_name) {
							return false;
						}
					}
				});

				quote! {
					impl #bounded_impl_generics ::esexpr::ValueEq for #name #ty_generics #bounded_where_clause {
						fn value_eq(&self, other: &Self) -> bool {
							#(#field_eq)*
							true
						}
					}
				}
			},
			Fields::Unnamed(ref fields) => {
				let field_eq = fields.unnamed.iter().enumerate().map(|(i, field)| {
					let index = syn::Index::from(i);
					let field_type = &field.ty;
					quote! {
						if !<#field_type as ::esexpr::ValueEq>::value_eq(&self.#index, &other.#index) {
							return false;
						}
					}
				});

				quote! {
					impl #bounded_impl_generics ::esexpr::ValueEq for #name #ty_generics #bounded_where_clause {
						fn value_eq(&self, other: &Self) -> bool {
							#(#field_eq)*
							true
						}
					}
				}
			},
			Fields::Unit => {
				quote! {
					impl #bounded_impl_generics ::esexpr::ValueEq for #name #ty_generics #bounded_where_clause {
						fn value_eq(&self, _other: &Self) -> bool {
							true
						}
					}
				}
			},
		},
		Data::Enum(data_enum) => {
			let variants = data_enum.variants.iter().map(|variant| {
                let variant_name = &variant.ident;
                match &variant.fields {
                    Fields::Named(fields) => {
						#[expect(clippy::unwrap_used, reason = "We know this is a named field")]
                        let field_names = fields.named.iter().map(|field| field.ident.as_ref().unwrap()).collect::<Vec<_>>();
                        let self_fields = field_names.iter().map(|field| syn::Ident::new(&format!("self_{field}"), field.span())).collect::<Vec<_>>();
                        let other_fields = field_names.iter().map(|field| syn::Ident::new(&format!("other_{field}"), field.span())).collect::<Vec<_>>();

                        let field_eq = fields.named.iter().zip(self_fields.iter()).zip(other_fields.iter()).map(|((field, self_field), other_field)| {
                            let field_type = &field.ty;
                            quote! {
                                if !<#field_type as ::esexpr::ValueEq>::value_eq(#self_field, #other_field) {
                                    return false;
                                }
                            }
                        });

                        quote! {
                            (#name::#variant_name { #(#field_names: #self_fields),* }, #name::#variant_name { #(#field_names: #other_fields),* }) => {
                                #(#field_eq)*
                                true
                            }
                        }
                    }
                    Fields::Unnamed(fields) => {
                        let field_count = fields.unnamed.len();
                        let self_fields = (0..field_count).map(|i| syn::Ident::new(&format!("self_field_{i}"), variant.ident.span())).collect::<Vec<_>>();
                        let other_fields = (0..field_count).map(|i| syn::Ident::new(&format!("other_field_{i}"), variant.ident.span())).collect::<Vec<_>>();

                        let field_eq = fields.unnamed.iter().zip(self_fields.iter()).zip(other_fields.iter()).map(|((field, self_field), other_field)| {
                            let field_type = &field.ty;
                            quote! {
                                if !<#field_type as ::esexpr::ValueEq>::value_eq(#self_field, #other_field) {
                                    return false;
                                }
                            }
                        });

                        quote! {
                            (#name::#variant_name(#(#self_fields),*), #name::#variant_name(#(#other_fields),*)) => {
                                #(#field_eq)*
                                true
                            }
                        }
                    }
                    Fields::Unit => {
                        quote! {
                            (#name::#variant_name, #name::#variant_name) => true
                        }
                    }
                }
            });

			quote! {
				impl #bounded_impl_generics ::esexpr::ValueEq for #name #ty_generics #bounded_where_clause {
					fn value_eq(&self, other: &Self) -> bool {
						match (self, other) {
							#(#variants,)*
							_ => false
						}
					}
				}
			}
		},
		Data::Union(_) => {
			syn::Error::new(input.ident.span(), "ValueEq derive is not supported for unions").to_compile_error()
		},
	}
}
