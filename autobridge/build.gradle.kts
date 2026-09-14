plugins {
    id("java")
}

group = "autobridge"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "opencollab-snapshot"
        url = uri("https://repo.opencollab.dev/main/")
    }
}

dependencies {
    // Real Geyser API — downloaded from OpenCollab maven
    compileOnly(files("libs/geyser-api.jar"))
    compileOnly(files("libs/base-api.jar"))
    compileOnly(files("libs/events.jar"))
    compileOnly(files("libs/annotations.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

sourceSets {
    main {
        java.srcDirs("src/main/java")
        resources.srcDirs("src/main/resources")
    }
    test {
        java.srcDirs("src/test/java")
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        // Don't fail on warnings
        options.compilerArgs.add("-Xlint:none")
    }

    // Build a runnable test jar
    register("buildTest", Jar::class) {
        archiveBaseName.set("autobridge-test")
        from(sourceSets.main.get().output)
        from(sourceSets.test.get().output)
        manifest {
            attributes["Main-Class"] = "autobridge.TestHarness"
        }
    }
}
