plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.jetbrains:annotations:24.0.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }
}


tasks.named<Test>("test") {
    useJUnitPlatform()
}
