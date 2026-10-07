use std::fs;
use std::io::Write;
use std::path::PathBuf;
use std::process::{Command, Output, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};

use esexpr_binary::{ExprParserSync, parse_sync};

struct TempDir(PathBuf);

impl TempDir {
	fn new() -> Self {
		static NEXT: AtomicU64 = AtomicU64::new(0);
		let path = std::env::temp_dir().join(format!(
			"esexpr-cli-{}-{}",
			std::process::id(),
			NEXT.fetch_add(1, Ordering::Relaxed)
		));
		fs::create_dir(&path).expect("create isolated test directory");
		Self(path)
	}
}

impl Drop for TempDir {
	fn drop(&mut self) {
		fs::remove_dir_all(&self.0).expect("clean test directory");
	}
}

fn run(args: &[&std::ffi::OsStr], input: &str) -> Output {
	let mut child = Command::new(env!("CARGO_BIN_EXE_esx2esxb"))
		.args(args)
		.stdin(Stdio::piped())
		.stdout(Stdio::piped())
		.stderr(Stdio::piped())
		.spawn()
		.expect("start converter");
	child
		.stdin
		.take()
		.expect("stdin pipe")
		.write_all(input.as_bytes())
		.expect("write test input");
	child.wait_with_output().expect("wait for converter")
}

fn assert_output(output: &Output, expected: &str) {
	assert!(output.status.success(), "{}", String::from_utf8_lossy(&output.stderr));
	let decoded = parse_sync(&mut output.stdout.as_slice())
		.iter_static()
		.collect::<Result<Vec<_>, _>>()
		.expect("converter output should decode");
	assert_eq!(
		decoded,
		esexpr_text::parse_multi(expected).expect("expected text should parse")
	);
}

#[test]
fn stdin_stdout_and_explicit_dashes() {
	for args in [
		vec![],
		vec![std::ffi::OsStr::new("-")],
		vec![std::ffi::OsStr::new("-"), std::ffi::OsStr::new("-")],
	] {
		for input in ["", "#true", "(custom key: #false) #true 42"] {
			assert_output(&run(&args, input), input);
		}
	}
}

#[test]
fn file_input_output_and_mixed_streams() {
	let dir = TempDir::new();
	let input = dir.0.join("input.esx");
	let output = dir.0.join("output.esxb");
	let text = "#true (custom key: #false) 42";
	fs::write(&input, text).expect("write text fixture");
	let result = run(&[input.as_os_str(), output.as_os_str()], "");
	assert!(result.status.success(), "{}", String::from_utf8_lossy(&result.stderr));
	assert!(result.stdout.is_empty());
	let data = fs::read(&output).expect("read binary output");
	let decoded = parse_sync(&mut data.as_slice())
		.iter_static()
		.collect::<Result<Vec<_>, _>>()
		.expect("output should decode");
	assert_eq!(decoded, esexpr_text::parse_multi(text).expect("expected expressions"));
	assert_output(&run(&[input.as_os_str()], ""), text);
	let result = run(&[std::ffi::OsStr::new("-"), output.as_os_str()], text);
	assert!(result.status.success());
	assert_eq!(fs::read(&output).expect("mixed output"), data);
}

#[test]
fn invalid_args_input_and_output_fail() {
	let dir = TempDir::new();
	let missing = dir.0.join("missing.esx");
	for args in [
		vec![
			std::ffi::OsStr::new("a"),
			std::ffi::OsStr::new("b"),
			std::ffi::OsStr::new("c"),
		],
		vec![missing.as_os_str()],
		vec![std::ffi::OsStr::new("-"), dir.0.as_os_str()],
	] {
		let output = run(&args, "");
		assert!(!output.status.success());
		assert!(!output.stderr.is_empty());
	}
	let output = run(&[], "(unterminated");
	assert!(!output.status.success());
	assert!(output.stdout.is_empty());
}
