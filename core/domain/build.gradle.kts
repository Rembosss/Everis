import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

val domainChecks = tasks.register<JavaExec>("domainChecks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.everis.core.DomainChecksKt")
}

tasks.check { dependsOn(domainChecks) }

tasks.register<JavaExec>("statusBenchmark") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.everis.core.StatusBenchmarkKt")
}

// Checks have executable main functions and run through domainChecks, not JUnit.
tasks.test { failOnNoDiscoveredTests = false }
