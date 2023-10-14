import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"

    id("com.github.ben-manes.versions") version "0.49.0"
    id("se.ascp.gradle.gradle-versions-filter") version "0.1.16"
    idea
}

group = "dev.groovybyte.chunky.plugin.nativeoctree"
version = "1.0-SNAPSHOT"

// https://repo.lemaik.de/se/llbit/chunky-core/maven-metadata.xml
val chunkyVersion = "2.5.0-SNAPSHOT"

val libraryPaths =
//    listOf(/*"release", */"debug")
//        .map { projectDir.resolve("rust/target/$it/").absoluteFile } +
    listOf(
        projectDir.resolve("zig/").absoluteFile,
//        projectDir.resolve("zig/zig-out/lib/").absoluteFile
    )

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
    maven(url = "https://repo.lemaik.de/")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
//        modularity.inferModulePath.set(true)
}

dependencies {
    implementation("se.llbit:chunky-core:$chunkyVersion") {
        if (chunkyVersion.endsWith("SNAPSHOT")) {
            isChanging = true
        }
    }
}

application {
//    mainModule.set("chunky.plugin.nativeoctree")
    mainClass.set("$group.PluginImpl")
}

javafx {
    version = "11.0.2" // aligned with Chunky
    modules = listOf("javafx.controls", "javafx.fxml")
}

tasks {
    processResources {
        filesMatching("plugin.json") {
            expand(
                "version" to project.version,
                "chunkyVersion" to chunkyVersion,
                "pluginClass" to application.mainClass.get(),
            )
        }
    }

    withType<JavaExec> {
        println(libraryPaths)
        jvmArgs(
            "--enable-preview",
//            "--enable-native-access=chunky.plugin.nativeoctree",
            "--enable-native-access=ALL-UNNAMED",
            "-Djava.library.path=${libraryPaths.joinToString(";") { it.path }}"
        )
    }
    compileJava {
        options.compilerArgs.addAll(
            listOf(
                "--enable-preview",
            )
        )
    }
    withType<Jar> {
        archiveFileName.set("${archiveBaseName.get()}.${archiveExtension.get()}")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        configurations["compileClasspath"].apply {
            files { dep ->
                when (dep.group) {
                    "org.openjfx" -> false
                    "se.llbit" -> false
                    else -> true
                }
            }.forEach { file ->
                from(zipTree(file.absoluteFile))
            }
        }
        libraryPaths
            .flatMap {
                it.listFiles { _, filename ->
                    it.resolve(filename).extension in setOf("so", "dll", "dylib")
                }?.toList() ?: emptyList()
            }
            .distinctBy { it.name }
            .takeIf { it.isNotEmpty() }
            ?.let { from(it.toTypedArray()) }
    }

    withType<DependencyUpdatesTask> {
        gradleReleaseChannel = "current"
        versionsFilter {
            exclusiveQualifiers.addAll("ea")
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
