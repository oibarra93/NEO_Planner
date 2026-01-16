import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // ✅ Needed for @Serializable DTOs
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.oscaribarra.neoplanner"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.oscaribarra.neoplanner"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // ✅ New DSL (replaces kotlinOptions { jvmTarget = "11" })
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.okhttp)


    // Compose
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.ui.tooling.preview)
    debugImplementation(libs.ui.tooling)
    implementation(libs.androidx.material.icons.extended)

    // Activity + Compose (THIS FIXES setContent + permission launcher)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)

    // ✅ Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // ✅ DataStore (Preferences)
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // ✅ Location (Fused provider)
    implementation(libs.play.services.location)

    // ✅ Ktor + Kotlinx Serialization JSON
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // ✅ CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(kotlin("test"))
}
