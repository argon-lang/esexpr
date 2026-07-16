plugins {
    `java-library`
    alias(libs.plugins.errorprone)
}

dependencies {
    compileOnly(libs.jspecify)
    testImplementation(project(":generator"))

    implementation(project(":lib"))
    annotationProcessor(project(":generator"))
}
