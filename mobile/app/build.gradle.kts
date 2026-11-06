import groovy.json.JsonOutput
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Machine-specific settings live in `local.properties` so the API endpoint and
 * the Google client ID are not baked into the repository.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")

    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProperty(name: String, fallback: String): String =
    (localProperties.getProperty(name) ?: System.getenv(name) ?: fallback)

/**
 * Installs older than this are shown an update they cannot dismiss.
 *
 * Raise it only when an older build genuinely stopped working — a route it
 * calls that no longer exists, say. A merely nicer release is not a reason to
 * take someone's choice away.
 */
val minimumSupportedVersionCode = 1

android {
    namespace = "com.budgettracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.budgettracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"${localProperty("API_BASE_URL", "http://10.0.2.2:8010/api/v1/")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperty("GOOGLE_WEB_CLIENT_ID", "")}\"")

        // Where the in-app updater looks for a newer build. This is the
        // website, not the API: the manifest sits next to the APK it
        // describes. 10.0.2.2:3000 is `npm run dev` as seen from the emulator.
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"${localProperty("UPDATE_MANIFEST_URL", "http://10.0.2.2:3000/downloads/android.json")}\"",
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Signed with the debug key so `assembleRelease` produces an
            // installable APK for the download page without extra setup.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
        )
    }

    sourceSets["main"].java.srcDir("src/main/kotlin")
    sourceSets["test"].java.srcDir("src/test/kotlin")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.splashscreen)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}

/**
 * Publishes a release: the APK, and the manifest that describes it.
 *
 * The download page and the in-app updater both read `android.json`, so the
 * size, digest and version in it have to match the file sitting beside it.
 * Generating both from one build is the only way to be sure they do — a
 * hand-maintained manifest drifts the first time a release is cut in a hurry,
 * and the failure is an updater that offers a download it cannot verify.
 *
 * Release notes come from `mobile/release-notes.txt`, one bullet per line.
 */
tasks.register("publishRelease") {
    group = "distribution"
    description = "Builds the release APK and publishes it with its manifest to web/public/downloads/."

    dependsOn("assembleRelease")

    // Read while the task is being configured. The configuration cache will
    // not let the project model be touched once it is running.
    val versionName = requireNotNull(android.defaultConfig.versionName) { "versionName is not set" }
    val versionCode = requireNotNull(android.defaultConfig.versionCode) { "versionCode is not set" }
    val minimumVersionCode = minimumSupportedVersionCode
    val apkDirectory = layout.buildDirectory.dir("outputs/apk/release")
    val downloads = rootProject.layout.projectDirectory.dir("../web/public/downloads")
    val notesFile = rootProject.layout.projectDirectory.file("release-notes.txt").asFile

    doLast {
        val built = apkDirectory.get().asFile.resolve("app-release.apk")

        check(built.exists()) { "No release APK at ${built.absolutePath}" }

        val target = downloads.asFile.apply { mkdirs() }
        val apkName = "budget-tracker-$versionName.apk"

        // One published build at a time. These are tens of megabytes and an
        // older APK left behind is one someone can still be served.
        target.listFiles { file -> file.name.startsWith("budget-tracker") && file.extension == "apk" }
            ?.forEach { it.delete() }

        val published = target.resolve(apkName)
        built.copyTo(published, overwrite = true)

        val digest = MessageDigest.getInstance("SHA-256")

        published.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }

        val notes = notesFile
            .takeIf { it.exists() }
            ?.readLines()
            ?.map { it.trim().removePrefix("-").trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            .orEmpty()

        val manifest = linkedMapOf(
            "version_code" to versionCode,
            "version_name" to versionName,
            "minimum_version_code" to minimumVersionCode,
            "released_at" to LocalDate.now().toString(),
            "size_bytes" to published.length(),
            "sha256" to digest.digest().joinToString("") { "%02x".format(it) },
            // Relative, so the same manifest works on localhost, a LAN
            // address and a real domain without being rewritten.
            "download_url" to apkName,
            "notes" to notes,
        )

        target.resolve("android.json")
            .writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n")

        logger.lifecycle("Published $apkName ($versionName, build $versionCode) to ${target.absolutePath}")
    }
}
