plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "dev.argon"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-text:1.12.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }

    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "esexpr-generator"
            from(components["java"])
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
