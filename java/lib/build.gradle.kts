plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "dev.argon"
version = "0.1.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("commons-io:commons-io:2.16.1")
    testImplementation("org.apache.commons:commons-collections4:4.4")

    testImplementation("com.fasterxml.jackson.core:jackson-core:2.17.2")
    testImplementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
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
            artifactId = "esexpr-java-runtime"
            from(components["java"])
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}


tasks.named<Test>("test") {
    useJUnitPlatform()
}
