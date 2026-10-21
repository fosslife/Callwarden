import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fosslife.hush"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fosslife.hush"
        // CallScreeningService with the call-screening role needs Android 10 (API 29).
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Credentials live in ~/.gradle/gradle.properties, never in the repo. Absent (CI, F-Droid)
    // means release builds come out unsigned instead of failing.
    signingConfigs {
        create("release") {
            val path = findProperty("HUSH_UPLOAD_STORE_FILE") as String?
            if (path != null) {
                storeFile = file(path)
                storePassword = property("HUSH_UPLOAD_STORE_PASSWORD") as String
                keyAlias = property("HUSH_UPLOAD_KEY_ALIAS") as String
                keyPassword = property("HUSH_UPLOAD_KEY_PASSWORD") as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // No Google Play dependency-info blob in the APK; keeps builds reproducible for F-Droid.
    // Kept in the bundle so Play Console can flag vulnerable SDK versions.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    testImplementation("junit:junit:4.13.2")
}
