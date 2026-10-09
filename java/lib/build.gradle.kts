plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.errorprone)
}

dependencies {
    testImplementation(libs.jqwik)
    compileOnly(libs.jspecify)
    testImplementation(libs.commons.io)
    testImplementation(libs.commons.collections)

    testImplementation(libs.jackson.core)
    testImplementation(libs.jackson.annotations)
    testImplementation(libs.jackson.databind)
    api(libs.guava)
    api(libs.eclipse.collections)
}

tasks.javadoc {
    exclude("dev/argon/esexpr/codecs/**")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "esexpr-java-runtime"

            pom {
                name = "ESExpr Runtime"
                description = "ESExpr runtime library"
            }
        }
    }
}
