import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Private signing material stays outside the workspace and source checkpoints.
val releaseSigningFile = providers.gradleProperty("everisSigningProperties").orNull?.let(rootProject::file)
    ?: File(System.getProperty("user.home"), ".local/share/everis/signing/release.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
fun releaseSigningValue(name: String): String = requireNotNull(releaseSigning.getProperty(name)?.takeIf { it.isNotBlank() }) {
    "Missing $name in local Release signing configuration"
}

android {
    namespace = "org.everis.app"
    compileSdk = 37

    defaultConfig {
        // Isolated emulator package for destructive persistence/onboarding checks.
        applicationId = if (providers.gradleProperty("diaryUiTest").orNull == "true")
            providers.gradleProperty("diaryQaApplicationId").getOrElse("org.everis.qa.pre01") else "org.everis.app"
        minSdk = 31
        targetSdk = 37
        versionCode = 10
        versionName = "0.2-beta"
        buildConfigField("String", "UPDATE_REPOSITORY", "\"Rembosss/Everis\"")
    }

    signingConfigs {
        if (releaseSigningFile.isFile) create("everisRelease") {
            storeFile = File(releaseSigningValue("storeFile"))
            check(requireNotNull(storeFile).isFile) { "Release keystore is missing" }
            storeType = releaseSigningValue("storeType")
            storePassword = releaseSigningValue("storePassword")
            keyAlias = releaseSigningValue("keyAlias")
            keyPassword = releaseSigningValue("keyPassword")
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            ndk { abiFilters += "arm64-v8a" }
        }
        release {
            signingConfig = signingConfigs.findByName("everisRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            ndk { abiFilters += "arm64-v8a" }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    // All five languages remain available offline without Play language downloads.
    bundle { language { enableSplit = false } }
    androidResources { localeFilters.addAll(listOf("ru", "uk", "be", "kk", "en")) }

}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:security"))
    implementation(project(":core:sources"))
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    // Required Expressive surface; kept in the theme while still in beta.
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

// Public build artifacts live here; app/build remains Gradle's disposable working cache.
val exportedVersion = requireNotNull(android.defaultConfig.versionName)
val qaExport = providers.gradleProperty("diaryUiTest").orNull == "true"
listOf(Triple("Debug", "debug", "arm64-v8a"), Triple("Release", "release", "arm64-v8a")).forEach { (taskSuffix, folder, abi) ->
    val release = folder == "release"
    val signedRelease = android.buildTypes.getByName("release").signingConfig != null
    val fileName = if (release && !signedRelease) "app-release-unsigned.apk" else "app-$folder.apk"
    val inputApk = layout.buildDirectory.file("outputs/apk/$folder/$fileName")
    val exportDirectory = rootProject.file("Releases/$exportedVersion/${if (release) "Release" else "Debug"}")
    val outputName = "${if (qaExport) "QA-" else ""}Everis-$exportedVersion-$abi-${if (release) "release" else "debug"}${if (release && !signedRelease) "-unsigned" else ""}.apk"
    tasks.matching { it.name == "assemble$taskSuffix" }.configureEach {
        doLast {
            val input = inputApk.get().asFile
            check(input.isFile) { "Expected APK: $input" }
            exportDirectory.mkdirs()
            Files.copy(input.toPath(), exportDirectory.resolve(outputName).toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.lifecycle("APK exported: ${exportDirectory.resolve(outputName)}")
        }
    }
}
