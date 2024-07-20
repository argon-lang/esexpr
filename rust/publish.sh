#!/bin/bash -ex

cargo deploy -p esexpr-derive
cargo deploy -p esexpr
cargo deploy -p esexpr-binary
cargo deploy -p esexpr-json
cargo deploy -p esexpr-text
