
use lalrpop_util::lalrpop_mod;

use crate::ESExpr;

pub mod lexer;

lalrpop_mod!(esexpr_text, "/text_format/esexpr_text.rs");


enum Field {
    Positional(ESExpr),
    Keyword(String, ESExpr),
}

pub enum ParseError {
    Lex(lexer::LexError),
}

pub fn parse<'input>(s: &'input str) -> Result<ESExpr, lalrpop_util::ParseError<usize, lexer::Token<'input>, ParseError>> {
    let lexer = lexer::Lexer::new(s).map(|res| res.map_err(ParseError::Lex));

	esexpr_text::ExprParser::new().parse(lexer)
}



