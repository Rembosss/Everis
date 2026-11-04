import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("org.jetbrains.kotlin.jvm") }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
val securityChecks = tasks.register<JavaExec>("securityChecks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.everis.security.SecurityChecksKt")
}
tasks.check { dependsOn(securityChecks) }

// Checks have executable main functions and run through securityChecks, not JUnit.
tasks.test { failOnNoDiscoveredTests = false }
