use std::path::PathBuf;
use ui_test::{run_tests, Config};
use ui_test::dependencies::DependencyBuilder;

fn main() -> ui_test::color_eyre::Result<()> {
    let mut config = Config::rustc("error-test-cases");
    config.comment_defaults.base().set_custom(
        "dependencies",
        DependencyBuilder {
            crate_manifest_path: PathBuf::from("../esexpr/Cargo.toml"),
            ..DependencyBuilder::default()
        },
    );
    
    let abort_check = config.abort_check.clone();
    ctrlc::set_handler(move || abort_check.abort())?;

    run_tests(config)
}

