# Publish Procedure

## Rust

Run the `publish.sh` script.

## Java

Run `gradle publishMavenJavaPublicationToMavenRepository`.

This will place the artifacts into the local maven repository under
- `lib/build/repo/dev/argon/esexpr/esexpr-java-runtime/<version>`
- `generator/build/repo/dev/argon/esexpr/esexpr-generator/<version>`

Then upload to maven central
- POM
- library JAR
- javadoc JAR
- sources JAR
- signature files for the above

## Scala

Set the environment variable for the GPG passphrase.

```
export PGP_PASSPHRASE=<passphrase>
```

Run `sbt publishSigned`.

This will place the artifacts into the local maven repository under
- `esexpr/js/target/repo/dev/argon/esexpr/esexpr-scala-runtime_sjs1_3/<version>`
- `esexpr/jvm/target/repo/dev/argon/esexpr/esexpr-scala-runtime_3/<version>`

Then upload to maven central as in the Java section.


