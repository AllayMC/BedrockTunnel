import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.imageio.ImageIO

plugins {
    id("java")
    id("application")
    alias(libs.plugins.shadow)
}

group = "org.allaymc"
version = "0.1.0"
description = "MITM packet capture tool for Minecraft: Bedrock Edition"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
}

dependencies {
    implementation(libs.protocol)
    implementation(libs.flatlaf)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jsr310)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j)
    implementation(libs.jose4j)
    implementation(libs.rsyntaxtextarea)
    implementation(libs.bined.swing)
    implementation(libs.binary.data)
    implementation(libs.binary.data.array)
}

application {
    mainClass.set("org.allaymc.bedrocktunnel.BedrockTunnelApp")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.compilerArgs.add("-parameters")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.named("distZip") {
    dependsOn(tasks.shadowJar)
}

tasks.named("distTar") {
    dependsOn(tasks.shadowJar)
}

tasks.named("startScripts") {
    dependsOn(tasks.shadowJar)
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir.resolve(".run")
    workingDir.mkdirs()
}

val appName = "BedrockTunnel"
val isWindows = System.getProperty("os.name").startsWith("Windows")
val toolchainVersion = java.toolchain.languageVersion.orElse(JavaLanguageVersion.of(21))
val javaToolchains = extensions.getByType<JavaToolchainService>()
val packagingLauncher = javaToolchains.launcherFor {
    languageVersion.set(toolchainVersion)
}
val executableSuffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
fun packagingTool(name: String) = packagingLauncher.map {
    it.metadata.installationPath.file("bin/$name$executableSuffix").asFile.absolutePath
}
fun runPackagingTool(command: List<String>, output: ByteArrayOutputStream? = null) {
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    val bytes = process.inputStream.readBytes()
    output?.write(bytes)
    if (process.waitFor() != 0) {
        throw GradleException(
            buildString {
                append("Command failed: ")
                append(command.joinToString(" "))
                appendLine()
                append(bytes.toString(Charsets.UTF_8))
            }
        )
    }
}
fun DataOutputStream.writeLeShort(value: Int) {
    writeByte(value and 0xFF)
    writeByte(value ushr 8 and 0xFF)
}
fun DataOutputStream.writeLeInt(value: Int) {
    writeByte(value and 0xFF)
    writeByte(value ushr 8 and 0xFF)
    writeByte(value ushr 16 and 0xFF)
    writeByte(value ushr 24 and 0xFF)
}
fun scaleIcon(source: BufferedImage, size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.drawImage(source, 0, 0, size, size, null)
    graphics.dispose()
    return image
}
fun writeIco(sourcePng: File, targetIco: File) {
    val source = ImageIO.read(sourcePng) ?: throw GradleException("Unable to read icon source: $sourcePng")
    val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
    val images = sizes.map { size ->
        ByteArrayOutputStream().use { output ->
            ImageIO.write(scaleIcon(source, size), "png", output)
            size to output.toByteArray()
        }
    }

    targetIco.parentFile.mkdirs()
    targetIco.outputStream().use { fileOutput ->
        DataOutputStream(fileOutput).use { output ->
            output.writeLeShort(0)
            output.writeLeShort(1)
            output.writeLeShort(images.size)

            var offset = 6 + images.size * 16
            for ((size, pngBytes) in images) {
                output.writeByte(if (size == 256) 0 else size)
                output.writeByte(if (size == 256) 0 else size)
                output.writeByte(0)
                output.writeByte(0)
                output.writeLeShort(1)
                output.writeLeShort(32)
                output.writeLeInt(pngBytes.size)
                output.writeLeInt(offset)
                offset += pngBytes.size
            }

            for ((_, pngBytes) in images) {
                output.write(pngBytes)
            }
        }
    }
}

val shadowJarTask = tasks.named<Jar>("shadowJar")
val packagingDirectory = layout.buildDirectory.dir("packaging")
val stagedJarDirectory = packagingDirectory.map { it.dir("input") }
val runtimeModulesFile = packagingDirectory.map { it.file("runtime-modules.txt") }
val runtimeImageDirectory = packagingDirectory.map { it.dir("runtime") }
val appImageDirectory = packagingDirectory.map { it.dir("app-image") }
val windowsIconFile = packagingDirectory.map { it.file("icons/$appName.ico") }

val generateWindowsIcon by tasks.registering {
    val iconSource = layout.projectDirectory.file("src/main/resources/app-icon.png")
    inputs.file(iconSource)
    outputs.file(windowsIconFile)

    doLast {
        writeIco(iconSource.asFile, windowsIconFile.get().asFile)
    }
}

val stagePackagingInput by tasks.registering(Sync::class) {
    dependsOn(shadowJarTask)
    into(stagedJarDirectory)
    from(shadowJarTask.flatMap { it.archiveFile })
}

val resolveRuntimeModules by tasks.registering {
    dependsOn(shadowJarTask)

    val shadowJarFile = shadowJarTask.flatMap { it.archiveFile }
    inputs.file(shadowJarFile)
    outputs.file(runtimeModulesFile)

    doLast {
        val output = ByteArrayOutputStream()
        runPackagingTool(
            listOf(
                packagingTool("jdeps").get(),
                "--multi-release", toolchainVersion.get().asInt().toString(),
                "--ignore-missing-deps",
                "--print-module-deps",
                shadowJarFile.get().asFile.absolutePath
            ),
            output
        )

        val modules = output.toString()
            .split(',', '\r', '\n')
            .map(String::trim)
            .filter(String::isNotBlank)
            .filter { it.startsWith("java.") || it.startsWith("jdk.") }
            .toSortedSet()
            .apply {
                add("jdk.crypto.ec")
            }

        if (modules.isEmpty()) {
            throw GradleException("Failed to detect runtime modules for jlink")
        }

        runtimeModulesFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(modules.joinToString(","))
        }
    }
}

val createRuntimeImage by tasks.registering {
    dependsOn(resolveRuntimeModules)
    inputs.file(runtimeModulesFile)
    outputs.dir(runtimeImageDirectory)

    doLast {
        delete(runtimeImageDirectory)
        runPackagingTool(
            listOf(
                packagingTool("jlink").get(),
                "--output", runtimeImageDirectory.get().asFile.absolutePath,
                "--add-modules", runtimeModulesFile.get().asFile.readText().trim(),
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--compress=2"
            )
        )
    }
}

tasks.register("packageAppImage") {
    dependsOn(stagePackagingInput, createRuntimeImage, generateWindowsIcon)
    inputs.dir(stagedJarDirectory)
    inputs.dir(runtimeImageDirectory)
    inputs.file(windowsIconFile)
    outputs.dir(appImageDirectory)

    doLast {
        delete(appImageDirectory)
        val command = mutableListOf(
            packagingTool("jpackage").get(),
            "--type", "app-image",
            "--dest", appImageDirectory.get().asFile.absolutePath,
            "--input", stagedJarDirectory.get().asFile.absolutePath,
            "--name", appName,
            "--main-jar", shadowJarTask.get().archiveFileName.get(),
            "--main-class", application.mainClass.get(),
            "--app-version", project.version.toString(),
            "--runtime-image", runtimeImageDirectory.get().asFile.absolutePath
        )
        if (isWindows) {
            command += listOf("--icon", windowsIconFile.get().asFile.absolutePath)
        }
        runPackagingTool(
            command
        )
    }
}
