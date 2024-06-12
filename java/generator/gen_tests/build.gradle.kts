plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":generator"))

    implementation(project(":lib"))
    annotationProcessor(project(":generator"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }
}


tasks.named<Test>("test") {
    useJUnitPlatform()
}
