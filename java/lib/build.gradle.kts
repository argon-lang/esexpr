plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "dev.argon"
version = "0.1.0"

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

    withSourcesJar()
    withJavadocJar()

    
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "esexpr-generator"
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}


tasks.named<Test>("test") {
    useJUnitPlatform()
}
