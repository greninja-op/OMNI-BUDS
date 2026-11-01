import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val appCompileSdk = libs.versions.androidCompileSdk.get().toInt()
val appMinSdk = libs.versions.androidMinSdk.get().toInt()
val appTargetSdk = libs.versions.androidTargetSdk.get().toInt()

android {
    namespace = "com.omnibuds.app"
    compileSdk = appCompileSdk

    defaultConfig {
        applicationId = "com.omnibuds.app"
        minSdk = appMinSdk
        targetSdk = appTargetSdk
        versionCode = 2
        versionName = "1.0.1-redesign"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":platform:android"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
}
