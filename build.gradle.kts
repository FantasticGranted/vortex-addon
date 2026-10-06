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
    mavenCentral()
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

    // Hana printer deps
    implementation(files("libs/tweakeroo-fabric-26.1.2-0.28.10.jar"))
    implementation(files("libs/quickshulker-v3.2.2-mc26.1.jar"))
    implementation(files("libs/chesttracker-2.8.3%2B26.1.2.jar"))
    implementation(files("libs/modmenu.jar"))
    implementation(fileTree("libs/fapi") { include("*.jar") })

    // Lombok (Hana printer uses @Getter)
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    // Pinyin search (Hana printer)
    implementation("com.belerweb:pinyin4j:2.5.1")

    // Quietee Utils
    implementation(files("libs/quiettee-utils-1.0.0+mc26.1.jar"))
}

sourceSets {
    main {
        // The vortex addon sources are kept in a separate tree and get
        // packaged into their own jar by the vortexJar task.
        java.srcDirs("src/main/java", "src/vortex/java", "src/hana/java")
        resources.srcDirs("src/main/resources", "src/vortex/resources", "src/hana/resources")
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
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("fabric.mod.json.bak")
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    // Vortex core jar
    jar {
        inputs.property("archivesName", project.base.archivesName.get())
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }

        include("com/vortex/**")
        include("com/example/**")
        include("assets/vortex/**")
        include("fabric.mod.json")
    }

    // VolytraFly jar
    register<Jar>("volytraflyJar") {
        group = "build"
        archiveBaseName.set("volytrafly")
        archiveVersion.set(project.version.toString())
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(sourceSets.main.get().output) {
            include("com/volytrafly/**")
        }
        from("src/vortex/resources") {
            include("fabric.mod.json.volytrafly")
            rename { "fabric.mod.json" }
            include("printer.accesswidener")
            include("assets/volytrafly/**")
        }
        from("LICENSE") {
            rename { "${it}_volytrafly" }
        }
    }

    // SuperPrinter jar
    register<Jar>("superprinterJar") {
        group = "build"
        archiveBaseName.set("superprinter")
        archiveVersion.set(project.version.toString())
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(sourceSets.main.get().output) {
            include("net/numericly/superprinter/**")
        }
        from("src/main/resources") {
            include("fabric.mod.json.superprinter")
            rename { "fabric.mod.json" }
            include("printer.accesswidener")
        }
        from("LICENSE") {
            rename { "${it}_superprinter" }
        }
    }

    // Hana Printer jar
    register<Jar>("hanaJar") {
        group = "build"
        archiveBaseName.set("hana-printer")
        archiveVersion.set(project.version.toString())
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(sourceSets.main.get().output) {
            include("me/aleksilassila/litematica/**")
        }
        from("src/hana/resources") {
            include("fabric.mod.json.hana")
            rename { "fabric.mod.json" }
            include("printer.accesswidener")
            include("assets/hana-printer/**")
        }
        from("LICENSE") {
            rename { "${it}_hana-printer" }
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
        dependsOn("build", "volytraflyJar", "superprinterJar", "hanaJar")

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
                    (f.name.startsWith("superprinter-") || f.name.startsWith("vortex-") || f.name.startsWith("volytrafly-") || f.name.startsWith("hana-printer-")) &&
                    f.name != targetJar.name
            }?.forEach { it.delete() }

            println("Deploying vortex-$current.jar -> ${deployModsDir.absolutePath}")
        }

        from(layout.buildDirectory.dir("libs")) {
            include("vortex-${project.version}.jar")
            include("volytrafly-${project.version}.jar")
            include("superprinter-${project.version}.jar")
            include("hana-printer-${project.version}.jar")
        }
        into(deployModsDir)

        doLast {
            versionFile.writeText(bumpVersion(project.version.toString()))
            println("Next build version: ${readModVersion()}")
        }
    }
}