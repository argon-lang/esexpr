use proc_macro::TokenStream;

mod derive;
mod literal;

#[proc_macro_derive(
	ESExprCodec,
	attributes(
		constructor,
		inline_value,
		keyword,
		simple_enum,
		default_value,
		optional,
		dict,
		vararg
	)
)]
pub fn derive_esexpr_codec(input: TokenStream) -> TokenStream {
	TokenStream::from(derive::derive_esexpr_codec_impl(proc_macro2::TokenStream::from(input)))
}

#[proc_macro]
pub fn esexpr_literal(input: TokenStream) -> TokenStream {
	TokenStream::from(literal::esexpr_literal_impl(proc_macro2::TokenStream::from(input)))
}
