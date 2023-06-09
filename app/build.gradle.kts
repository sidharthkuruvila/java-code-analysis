/*
 * This file was generated by the Gradle 'init' task.
 *
 * This project uses @Incubating APIs which are subject to change.
 */

plugins {
    id("java.code.analysis.kotlin-application-conventions")
}

dependencies {
    implementation("org.apache.commons:commons-text")
    implementation("org.xerial:sqlite-jdbc:3.41.2.1")
    implementation("com.github.javaparser:javaparser-core:3.25.3")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.25.3")
    implementation(project(":utilities"))
}

application {
    // Define the main class for the application.
    mainClass.set("kuruvila.analysis.AppKt")
}
