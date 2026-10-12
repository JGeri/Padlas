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
 * Signing material is resolved in this order:
 *  1. keystore.properties in the repository root (local developer machines, git-ignored)
 *  2. PADLAS_* environment variables (CI)
 * When neither is present the release build falls back to the debug key so that a
 * plain `./gradlew assembleRelease` still produces an installable APK.
 */
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val releaseStoreFile: File? = secret("storeFile", "PADLAS_KEYSTORE_FILE")
    ?.let { path -> file(path).takeIf(File::exists) ?: rootProject.file(path).takeIf(File::exists) }

android {
    namespace = "hu.padlas"
    compileSdk = 36

    defaultConfig {
        applicationId = "hu.padlas"
        minSdk = 34
        targetSdk = 36
        versionCode = (findProperty("padlas.versionCode") as String?)?.toInt() ?: 3
        versionName = (findProperty("padlas.versionName") as String?) ?: "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = secret("storePassword", "PADLAS_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "PADLAS_KEY_ALIAS")
                keyPassword = secret("keyPassword", "PADLAS_KEY_PASSWORD")
                // v1 costs a little size but keeps the oldest package installers happy;
                // v2 and v3 are what Android 11+ actually verifies.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // Release code (R8, not debuggable) under the debug package and key: frame timings
        // measured on a phone mean something, and the installed release stays untouched.
        create("profile") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-profile"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:design"))
    implementation(project(":core:database"))
    implementation(project(":core:media"))
    implementation(project(":feature:index"))
    implementation(project(":feature:map"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:dedup"))
    implementation(project(":feature:triage"))
    implementation(project(":feature:transcode"))
    implementation(project(":feature:archive"))
    implementation(project(":feature:sort"))
    implementation(project(":feature:rules"))
    implementation(project(":feature:audit"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
