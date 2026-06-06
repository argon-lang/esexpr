import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.errorprone)
}

group = "dev.argon.esexpr"
version = "0.5.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.commons.text)
    implementation(project(":lib"))
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly(libs.junit.platform.launcher)

    errorprone(libs.nullaway)
    errorprone(libs.errorprone)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }

    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
    }

    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:unchecked,deprecation,fallthrough,path,rawtypes")
}

tasks.javadoc {
    val sourceSetDirectories = sourceSets
        .main
        .get()
        .java
        .sourceDirectories
        .joinToString(File.separator)

    val coreOptions = options as CoreJavadocOptions
    coreOptions.addStringOption("-source-path", sourceSetDirectories)


    exclude("dev/argon/esexpr/generator/codegen/**")
    exclude("dev/argon/esexpr/generator/lookup/**")
    exclude("dev/argon/esexpr/generator/typeclass/**")
    exclude("dev/argon/esexpr/generator/utils/**")
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

tasks.named<Test>("test") {
    useJUnitPlatform()
}
