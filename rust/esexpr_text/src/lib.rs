use esexpr::ESExpr;

pub mod parser;

pub fn parse<'input>(s: &'input str) -> Result<ESExpr, nom::Err<nom::error::Error<&'input str>>> {
    let (_, expr) = parser::expr_file(s)?;
    Ok(expr)
}



