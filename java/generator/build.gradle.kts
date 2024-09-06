plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "dev.argon.esexpr"
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

            pom {
                name = "ESExpr Generator"
                description = "ESExpr encoding codec generator"
                url = "https://github.com/argon-lang/esexpr"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        name = "argon-dev"
                        email = "argon@argon.dev"
                        organization = "argon-lang"
                        organizationUrl = "https://argon.dev"
                    }
                }
                scm {
                    connection = "scm:git:git@github.com:argon-lang/esexpr.git"
                    developerConnection = "scm:git:git@github.com:argon-lang/esexpr.git"
                    url = "https://github.com/argon-lang/esexpr/tree/master/java"
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
