import net.ltgt.gradle.errorprone.errorprone
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.external.javadoc.CoreJavadocOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    group = "dev.argon.esexpr"
    version = "0.5.1"

    repositories {
        mavenCentral()
    }

    extensions.configure<SpotlessExtension> {
        java {
            val formatterConfig = rootProject.layout.projectDirectory.file("eclipse-formatter.properties")
            // Track settings changes so the configuration cache cannot retain stale formatter preferences.
            providers.fileContents(formatterConfig).asText.get()
            eclipse("4.41").configFile(formatterConfig.asFile)
            target("src/**/*.java")
        }
    }

    plugins.withType<JavaPlugin> {
        dependencies {
            "testImplementation"(libs.junit.jupiter)
            "testRuntimeOnly"(libs.junit.platform.launcher)
        }

        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion = JavaLanguageVersion.of(22)
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.add("-parameters")
            options.compilerArgs.add("-Xlint:unchecked,deprecation,fallthrough,path,rawtypes")
        }

        val sourceSets = extensions
            .getByType<SourceSetContainer>()

        tasks.withType<Javadoc>().configureEach {
            val sourceSetDirectories = sourceSets
                .named("main")
                .get()
                .java
                .sourceDirectories
                .joinToString(File.separator)

            val coreOptions = options as CoreJavadocOptions
            coreOptions.addStringOption("-source-path", sourceSetDirectories)
        }

        tasks.named<Test>("test") {
            useJUnitPlatform()
        }
    }

    plugins.withId("net.ltgt.errorprone") {
        dependencies {
            "errorprone"(libs.nullaway)
            "errorprone"(libs.errorprone)
        }

        tasks.withType<JavaCompile>().configureEach {
            options.errorprone {
                disable("RefactorSwitch")
                option("NullAway:OnlyNullMarked", "true")
                option("NullAway:JSpecifyMode", "true")
                error("NullAway")
                error("RequireExplicitNullMarking")
            }
        }
    }

    plugins.withId("maven-publish") {
        extensions.configure<JavaPluginExtension>("java") {
            withSourcesJar()
            withJavadocJar()
        }

        configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                from(components["java"])

                pom {
                    url = "https://github.com/argon-lang/esexpr"
                    licenses {
                        license {
                            name = "Apache License, Version 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0"
                        }
                    }
                    developers {
                        developer {
                            name = "argon-dev"
                            email = "argon@argon.dev"
                            organization = "argon-lang"
                            organizationUrl = "https://argon.dev"
                        }
                    }
                    scm {
                        connection = "scm:git:git@github.com:argon-lang/esexpr.git"
                        developerConnection = "scm:git:git@github.com:argon-lang/esexpr.git"
                        url = "https://github.com/argon-lang/esexpr/tree/master/java"
                    }
                }
            }

            repositories {
                maven {
                    url = uri(layout.buildDirectory.dir("repo"))
                }
            }
        }
    }

    plugins.withId("signing") {
        configure<SigningExtension> {
            sign(extensions.getByType<PublishingExtension>().publications)
        }
    }
}
