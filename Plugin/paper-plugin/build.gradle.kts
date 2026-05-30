plugins {
    id("com.gradleup.shadow")
}

repositories {
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.16")
    implementation(project(":core"))
    implementation("io.lettuce:lettuce-core:7.5.2.RELEASE")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Relocate Lettuce and its bundled dependencies to avoid classpath conflicts
    // with the Netty version already loaded by Paper.
    relocate("io.lettuce",          "club.imaginears.harmonia.libs.lettuce")
    relocate("io.netty",            "club.imaginears.harmonia.libs.netty")
    relocate("reactor",             "club.imaginears.harmonia.libs.reactor")
    relocate("org.reactivestreams", "club.imaginears.harmonia.libs.reactivestreams")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
