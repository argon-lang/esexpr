#!/bin/bash -e

version=$(./gradlew -q lib:properties --property version 2>/dev/null | grep "^version:" | awk '{ print $2 }')


echo "Project version: $version"

rm -f esexpr-java-*.zip
./gradlew clean
./gradlew publishMavenJavaPublicationToMavenRepository

pushd lib/build/repo
zip -r "../../../esexpr-java-$version.zip" .
popd

pushd generator/build/repo
zip -r "../../../esexpr-java-$version.zip" .
popd
