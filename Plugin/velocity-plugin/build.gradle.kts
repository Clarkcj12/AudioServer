plugins {
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    implementation(project(":core"))
    implementation("io.socket:socket.io-client:2.1.1")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("io.socket",   "club.imaginears.harmonia.libs.socket")
    relocate("io.engineio", "club.imaginears.harmonia.libs.engineio")
    relocate("okhttp3",     "club.imaginears.harmonia.libs.okhttp3")
    relocate("okio",        "club.imaginears.harmonia.libs.okio")
    relocate("org.json",    "club.imaginears.harmonia.libs.json")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
