use std::boxed::Box;
use std::ffi::OsStr;
use std::io::{Read, Write};
use std::path::PathBuf;
use std::vec::Vec;

pub fn parse_io_args() -> (Box<dyn Read>, Box<dyn Write>) {
	let args = std::env::args_os().skip(1).collect::<Vec<_>>();

	if args.len() > 2 {
		panic!("Up to two arguments are allowed.");
	}

	let mut args = args.into_iter();

	let infile = args.next().filter(|s| should_use_file_name(s));

	let infile: Box<dyn Read> = match infile {
		Some(p) => Box::new(std::fs::File::open(PathBuf::from(p)).unwrap()),
		None => Box::new(std::io::stdin()),
	};

	let outfile = args.next().filter(|s| should_use_file_name(s));

	let outfile: Box<dyn Write> = match outfile {
		Some(p) => Box::new(std::fs::File::create(PathBuf::from(p)).unwrap()),
		None => Box::new(std::io::stdout()),
	};

	(infile, outfile)
}

fn should_use_file_name(s: &OsStr) -> bool {
	s != "-"
}