plugins {
    kotlin("jvm") version "1.9.10"
    application

    kotlin("plugin.serialization") version "1.9.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.gutin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://m2.dv8tion.net/releases/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("ch.qos.logback:logback-classic:1.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    implementation("com.prof18.rssparser:rssparser:6.0.3")
    implementation("net.dv8tion:JDA:5.0.0-beta.13") { exclude("opus-java") }
    implementation("org.jsoup:jsoup:1.16.1")


    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}

tasks.jar {
    dependsOn("shadowJar")

    manifest { attributes["Main-Class"] = "MainKt" }
}