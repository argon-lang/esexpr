plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.errorprone)
}

dependencies {
    implementation(libs.commons.text)
    implementation(project(":lib"))
}

tasks.javadoc {
    exclude("dev/argon/esexpr/generator/codegen/**")
    exclude("dev/argon/esexpr/generator/lookup/**")
    exclude("dev/argon/esexpr/generator/typeclass/**")
    exclude("dev/argon/esexpr/generator/utils/**")
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "esexpr-generator"

            pom {
                name = "ESExpr Generator"
                description = "ESExpr encoding codec generator"
            }
        }
    }
}
