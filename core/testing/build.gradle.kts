plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/** Fakes and fixtures for the JVM tests of the other modules; nothing here ships in the app. */
android {
    namespace = "hu.padlas.core.testing"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    api(project(":core:database"))
    api(project(":core:media"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.test.core)
}
