plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-text:1.12.0")
}

tasks.withType<JavaCompile> {
    options.release.set(22)
}
