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
