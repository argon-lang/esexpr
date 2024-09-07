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
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("commons-io:commons-io:2.16.1")
    testImplementation("org.apache.commons:commons-collections4:4.4")

    testImplementation("com.fasterxml.jackson.core:jackson-core:2.17.2")
    testImplementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.jetbrains:annotations:24.0.0")
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
            artifactId = "esexpr-java-runtime"
            from(components["java"])

            pom {
                name = "ESExpr Runtime"
                description = "ESExpr runtime library"
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


tasks.named<Test>("test") {
    useJUnitPlatform()
}
