fn main() {
    run_lalrpop();
}

#[cfg(feature = "text-format")]
fn run_lalrpop() {
    lalrpop::process_root().unwrap();
}

#[cfg(not(feature = "text-format"))]
fn run_lalrpop() {}
