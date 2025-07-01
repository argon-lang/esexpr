#![allow(clippy::tabs_in_doc_comments)]

//!
//! ```compile_fail
//! #[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//! pub struct MultipleOptionalPositional1 {
//! 	#[optional]
//! 	pub a: Option<i32>,
//!
//! 	#[optional]
//! 	pub b: Option<i32>,
//! }
//! ```
//!
//! ```compile_fail
//! #[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//! pub struct MultipleOptionalPositional2 {
//! 	#[default_value = 1]
//! 	pub a: i32,
//!
//! 	#[default_value = 1]
//! 	pub b: i32,
//! }
//! ```
//!
//! ```compile_fail
//! #[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//! pub struct RequiredAfterOptionalPositional1 {
//! 	#[optional]
//! 	pub a: Option<i32>,
//!
//! 	#[optional]
//! 	pub b: Option<f32>,
//!
//! 	pub c: i32,
//! }
//! ```
//!
//! ```compile_fail
//! #[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//! pub struct RequiredAfterOptionalPositional2 {
//! 	#[optional]
//! 	pub a: Option<i32>,
//!
//! 	#[optional]
//! 	pub b: Option<f32>,
//!
//! 	pub c: f32,
//! }
//! ```
//!
//! ```compile_fail
//! #[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//! pub struct RequiredAfterOptionalPositional3 {
//! 	#[default_value = 1]
//! 	pub a: i32,
//!
//! 	#[default_value = 1.0]
//! 	pub b: f32,
//!
//! 	pub c: i32,
//! }
//! ```
//!
//! ```compile_fail
//! #[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//! pub struct RequiredAfterOptionalPositional4 {
//! 	#[default_value = 1]
//! 	pub a: i32,
//!
//! 	#[default_value = 1.0]
//! 	pub b: f32,
//!
//! 	pub c: f32,
//! }
//! ```
//!
//! ```compile_fail
//! #[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//! pub struct OptionalNoTag1 {
//! 	#[optional]
//! 	pub a: Option<esexpr::ESExpr>,
//! }
//! ```
//!
//! ```compile_fail
//! #[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//! pub struct OptionalNoTag2 {
//! 	#[optional]
//! 	pub a: Option<Option<esexpr::ESExpr>>,
//! }
//! ```
//!
//!
