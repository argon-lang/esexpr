#!/bin/bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

# Use the nightly from the pinned stable compiler's commit date, keeping rustfmt
# close to that compiler while enabling the nightly options in rustfmt.toml.
commit_date=$(rustc --version --verbose | sed -n 's/^commit-date: //p')
if [[ ! "$commit_date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    echo "Could not determine the pinned Rust compiler's commit date" >&2
    exit 1
fi

toolchain="nightly-$commit_date"
rustup toolchain install "$toolchain" --profile minimal --component rustfmt
cargo "+$toolchain" fmt --all "$@"
