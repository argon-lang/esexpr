import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    alias(libs.plugins.errorprone)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.jspecify)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(project(":generator"))

    implementation(project(":lib"))
    annotationProcessor(project(":generator"))

    errorprone(libs.nullaway)
    errorprone(libs.errorprone)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
        disable("RefactorSwitch")
    }

    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:unchecked,deprecation,fallthrough,path,rawtypes")
}


tasks.named<Test>("test") {
    useJUnitPlatform()
}
