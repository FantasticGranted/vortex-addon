import java.io.FileOutputStream
import java.io.IOException

plugins {
    alias(libs.plugins.fabric.loom)
}

val versionFile = file("version.txt")

fun readModVersion(): String {
    if (versionFile.exists()) {
        val v = versionFile.readText().trim()
        if (v.isNotEmpty()) return v
    }

    return "0.0.1"
}

fun bumpVersion(current: String): String {
    val parts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = (parts.getOrElse(2) { 0 }) + 1

    return "$major.$minor.$patch"
}

fun isFileLocked(file: File): Boolean {
    if (!file.exists()) return false

    return try {
        FileOutputStream(file, true).use { false }
    } catch (e: IOException) {
        true
    }
}

loom {
    accessWidenerPath = file("src/main/resources/printer.accesswidener")
}

base {
    archivesName = "vortex"
    version = readModVersion()
    group = "net.numericly"
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "fabric-maven"
        url = uri("https://maven.fabricmc.net")
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)

    // Meteor
    implementation(libs.meteor.client)

    // Super Printer (litematica + malilib for 26.1.2, already Mojang-mapped)
    implementation(files("libs/litematica-fabric-26.1.2-0.27.14.jar"))
    implementation(files("libs/malilib-fabric-26.1.2-0.28.12.jar"))

    // Hana TGP V4 Printer
    implementation(files("libs/litematica-printer-hana-26.1-TGP-V4-local.jar"))

    // Quietee Utils
    implementation(files("libs/quiettee-utils-1.0.0+mc26.1.jar"))
}

sourceSets {
    main {
        // The vortex addon sources are kept in a separate tree and get
        // packaged into their own jar by the vortexJar task.
        java.srcDirs("src/main/java", "src/vortex/java")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

fun toMinecraftCompat(version: String): String {
    val stable = Regex("""^(\d{2})\.([1-9]\d*)(?:\.(\d+))?$""")
    stable.matchEntire(version)?.let {
        val (year, drop, _) = it.destructured
        return "~$year.$drop"
    }

    val pre = Regex("""^(\d{2})\.([1-9]\d*)-pre[-.](\d+)$""")
    pre.matchEntire(version)?.let {
        return version.replace("-pre-", "-pre.")
    }

    val rc = Regex("""^(\d{2})\.([1-9]\d*)-rc[-.](\d+)$""")
    rc.matchEntire(version)?.let {
        return version.replace("-rc-", "-rc.")
    }

    return version
}

val deployModsDir: File = project.file(project.properties["mods_folder"] as String)

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to toMinecraftCompat(libs.versions.minecraft.get()),
            "jdk_version" to libs.versions.jdk.get(),
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        // The vortex addon is packaged into its own jar.
        exclude("com/vortex/**")

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    val vortexJar = register<Jar>("vortexJar") {
        dependsOn("classes")

        archiveBaseName.set("vortex")
        archiveVersion.set(project.version.toString())
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        // Include ALL compiled classes (superprinter + vortex)
        from(sourceSets.main.get().output)
        from(layout.projectDirectory.dir("src/main/resources")) {
            include("vortex_printer.mixins.json")
            include("superprinter.mixins.json")
            include("printer.accesswidener")
            include("assets/**")
        }
        // Bundle quietee-utils classes inside the vortex jar
        from(zipTree("libs/quiettee-utils-1.0.0+mc26.1.jar")) {
            exclude("fabric.mod.json")
        }
        from(layout.projectDirectory.dir("src/vortex/resources")) {
            exclude("fabric.mod.json")
        }
        from(layout.projectDirectory.file("src/vortex/resources/fabric.mod.json")) {
            expand(mapOf(
                "version" to project.version,
                "minecraft_version" to toMinecraftCompat(libs.versions.minecraft.get()),
                "jdk_version" to libs.versions.jdk.get(),
            ))
        }

        from("LICENSE") {
            rename { "${it}_$name" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }

    register<Copy>("deploy") {
        dependsOn("build")
        dependsOn(vortexJar)

        doFirst {
            if (!deployModsDir.isDirectory) {
                throw GradleException("Mods folder not found: ${deployModsDir.absolutePath} - set mods_folder in gradle.properties")
            }

            val current = project.version.toString()
            val targetJar = File(deployModsDir, "vortex-$current.jar")

            if (isFileLocked(targetJar)) {
                throw GradleException("${targetJar.name} is locked by a running game - close Minecraft before deploying")
            }

            deployModsDir.listFiles { f ->
                f.isFile &&
                    f.extension == "jar" &&
                    (f.name.startsWith("superprinter-") || f.name.startsWith("vortex-")) &&
                    f.name != targetJar.name
            }?.forEach { it.delete() }

            println("Deploying vortex-$current.jar -> ${deployModsDir.absolutePath}")
        }

        from(layout.buildDirectory.dir("libs")) {
            include("vortex-${project.version}.jar")
        }
        into(deployModsDir)

        doLast {
            versionFile.writeText(bumpVersion(project.version.toString()))
            println("Next build version: ${readModVersion()}")
        }
    }
}