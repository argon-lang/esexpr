use esexpr_binary::ExprGeneratorSync;
use esexpr_utils::parse_io_args;

fn main() {
	let (mut input, mut output) = parse_io_args();

	let mut esx = String::new();
	input.read_to_string(&mut esx).unwrap();
	let esx = esexpr_text::parse_multi(&esx).unwrap();

	let mut eg = esexpr_binary::ExprGenerator::new(&mut output);

	for expr in &esx {
		eg.generate(expr).unwrap();
	}
}
