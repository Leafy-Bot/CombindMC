plugins {
    id("java")
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "autobridge"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "opencollab-snapshot"
        url = uri("https://repo.opencollab.dev/main/")
    }
    maven {
        name = "sonatype-snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        name = "paper-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    // Geyser API — provided at runtime, loaded by Geyser itself
    compileOnly("org.geysermc.geyser:api:2.14.3-SNAPSHOT")

    // NeoForge registry access — provided at runtime by the server
    compileOnly("net.neoforged:neoforge:26.0.100-alpha")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
    withJavadocJar()
}

sourceSets {
    main {
        java.srcDirs("src/main/java")
    }
    test {
        java.srcDirs("src/test/java")
    }
}

tasks {
    shadowJar {
        archiveFileName.set("AutoBridge-${project.version}.jar")
        // Don't relocate Geyser classes — they're provided at runtime
        dependencies {
            exclude { it.name.startsWith("geyser") }
            exclude { it.name.startsWith("cumulus") }
            exclude { it.name.startsWith("checkerframework") }
            exclude { it.name.startsWith("jspecify") }
            exclude { it.name.startsWith("jetbrains") }
        }
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    javadoc {
        options.encoding = "UTF-8"
    }
}
