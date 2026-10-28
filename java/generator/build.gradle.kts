plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.errorprone)
}

dependencies {
    implementation(libs.commons.text)
    implementation(project(":lib"))
    testImplementation(libs.jspecify)
    testAnnotationProcessor(project(":generator"))
}

tasks.test {
    // Allow tests to exercise the internal cache without exposing it in the generator's module API.
    jvmArgs("--add-opens=dev.argon.esexpr.generator/dev.argon.esexpr.generator.lookup=dev.argon.esexpr.generator.tests")
    val generatorTestSources = layout.projectDirectory.dir("src/test/java/dev/argon/esexpr/generator/gen")
    inputs.dir(generatorTestSources)
    systemProperty("esexpr.generator.testSources", generatorTestSources.asFile.absolutePath)
}

// Positive processor fixtures must compile without javac warnings or Error Prone findings.
tasks.compileTestJava {
    options.compilerArgs.addAll(listOf("-Xlint:removal,cast", "-Werror"))
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
