@file:Suppress("UnstableApiUsage")

val modId = project.property("mod_id") as String

provided("org.jetbrains", "annotations")
provided("commons-io", "commons-io")
provided("com.google.errorprone", "error_prone_annotations")

architectury {
    platformSetupLoomIde()
    neoForge()
}

val common: Configuration by configurations.creating
// Without this, the mixin config isn't read properly with the runServer neoforge task
val developmentNeoForge: Configuration = configurations.getByName("developmentNeoForge")
val includeTransitive: Configuration = configurations.getByName("includeTransitive")

configurations {
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
    developmentNeoForge.extendsFrom(configurations["common"])
}

dependencies {
    // See https://github.com/google/guava/issues/6618
    modules {
        module("com.google.guava:listenablefuture") {
            replacedBy("com.google.guava:guava", "listenablefuture is part of guava")
        }
        // Force all fastutil dependencies to use the same version to prevent module conflicts
        module("it.unimi.dsi:fastutil") {
            replacedBy("com.nukkitx.fastutil:fastutil-common", "use nukkitx fastutil to avoid conflicts")
        }
    }
    
    // Exclude only specific conflicting modules to avoid module conflicts
    configurations.all {
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
        exclude(group = "it.unimi.dsi", module = "fastutil")
        exclude(group = "com.nukkitx.fastutil")
        
        // Force resolution strategy to prevent module conflicts
        resolutionStrategy {
            force("com.google.code.gson:gson:2.11.0")
            force("commons-io:commons-io:2.11.0")
            force("com.nukkitx.fastutil:fastutil-common:8.5.3")
        }
    }

    common(project(":shared", configuration = "namedElements")) { isTransitive = false }
    neoForge(libs.neoforge)
    compileOnly(libs.geyser.api)

    shadow(project(path = ":shared", configuration = "transformProductionNeoForge")) { isTransitive = false }

    // TODO fix neoforge runServer task
    // Temporarily disable pack converter to get server running
    // modRuntimeOnly(libs.pack.converter)
    // includeTransitive(libs.pack.converter)
    
    // Add pack-converter to development runtime to fix LogListener ClassNotFoundException
    // developmentNeoForge(libs.pack.converter)
}

tasks {
    remapJar {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
        archiveBaseName.set("${modId}-neoforge")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    shadowJar {
        archiveClassifier.set("dev-shadow")
    }

    jar {
        archiveClassifier.set("dev")
    }
}

sourceSets {
    main {
        resources {
            srcDirs(project(":shared").sourceSets["main"].resources.srcDirs)
        }
    }
}