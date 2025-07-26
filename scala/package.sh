#!/bin/bash -e

sbt --client reload

version=$(sbt --client 'show esexprJVM/version' 2>/dev/null \
  | sed 's/\x1B\[[0-9;]*[a-zA-Z]//g' \
  | grep '\[info\] *' \
  | sed -n 's/.*\[info\] *//p' \
  | tail -n 1 \
  | sed $'s/^[[:space:][:cntrl:]]*//;s/[[:space:][:cntrl:]]*$//')

echo "Project version: $version"

rm -f esexpr-scala-*.zip
sbt --client clean
sbt --client publishSigned

pushd esexpr/jvm/target/repo
zip -r "../../../../esexpr-scala-$version.zip" .
popd

pushd esexpr/js/target/repo
zip -ur "../../../../esexpr-scala-$version.zip" .
popd
