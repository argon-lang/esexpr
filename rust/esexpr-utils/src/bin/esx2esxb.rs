use esexpr::ESExprCodec;
use esexpr_binary::FixedStringPool;
use esexpr_utils::parse_io_args;

fn main() {
    let (mut input, mut output) = parse_io_args();


    let mut esx = String::new();
    input.read_to_string(&mut esx).unwrap();
    let esx = esexpr_text::parse(&esx).unwrap();


    let mut sp = esexpr_binary::StringPoolBuilder::new();
    sp.add(&esx);
    let mut sp = sp.into_fixed_string_pool();

    esexpr_binary::generate(&mut output, &mut FixedStringPool { strings: vec!() }, &sp.clone().encode_esexpr()).unwrap();
    esexpr_binary::generate(&mut output, &mut sp, &esx).unwrap();
}
