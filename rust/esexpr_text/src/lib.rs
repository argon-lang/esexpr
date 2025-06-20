use esexpr::ESExpr;

pub mod parser;

pub fn parse<'input>(s: &'input str) -> Result<ESExpr<'static>, nom::Err<nom::error::Error<&'input str>>> {
    let (_, expr) = parser::expr_file(s)?;
    Ok(expr)
}

pub fn parse_multi<'input>(s: &'input str) -> Result<Vec<ESExpr<'static>>, nom::Err<nom::error::Error<&'input str>>> {
    let (_, expr) = parser::multi_expr_file(s)?;
    Ok(expr)
}


