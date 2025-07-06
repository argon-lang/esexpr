//! Derive macros for `ESExpr`

use proc_macro::TokenStream;

mod derive;
mod literal;
mod encoded_eq;

#[proc_macro_derive(ESExprCodec, attributes(esexpr))]
/// Derive macro for `ESExprCodec`
pub fn derive_esexpr_codec(input: TokenStream) -> TokenStream {
	TokenStream::from(derive::derive_esexpr_codec_impl(proc_macro2::TokenStream::from(input)))
}

/// Derive macro for `ESExprEncodedEq`
#[proc_macro_derive(ESExprEncodedEq)]
pub fn derive_encoded_eq(input: TokenStream) -> TokenStream {
	TokenStream::from(encoded_eq::derive_encoded_eq_impl(proc_macro2::TokenStream::from(input)))
}

#[proc_macro]
/// Macro for `ESExpr` literal
pub fn esexpr_literal(input: TokenStream) -> TokenStream {
	TokenStream::from(literal::esexpr_literal_impl(proc_macro2::TokenStream::from(input)))
}
