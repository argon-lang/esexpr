#!/bin/bash -e

for f in *.esx; do
    cargo run --manifest-path ../rust/Cargo.toml --bin esx2esxb "$f" "${f}b"
done

