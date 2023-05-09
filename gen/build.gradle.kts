/*
 * This file was generated by the Gradle 'init' task.
 *
 * This project uses @Incubating APIs which are subject to change.
 */

plugins {
    id("java.code.analysis.kotlin-application-conventions")
}

dependencies {
    implementation("com.github.javaparser:javaparser-core:3.25.2")
    implementation(project(":utilities"))
}

application {
    // Define the main class for the application.
    mainClass.set("kuruvila.gen.AppKt")
}
