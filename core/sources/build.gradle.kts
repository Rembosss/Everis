import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("org.jetbrains.kotlin.jvm") }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
dependencies { implementation(project(":core:domain")) }

val sourceChecks = tasks.register<JavaExec>("sourceChecks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.everis.sources.SourceChecksKt")
}
tasks.check { dependsOn(sourceChecks) }

// Checks have executable main functions and run through sourceChecks, not JUnit.
tasks.test { failOnNoDiscoveredTests = false }
