plugins {
    `java-library`
}

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
}
