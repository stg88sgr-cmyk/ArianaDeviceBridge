plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val arianaDebugKeystorePath = System.getenv("ARIANA_DEBUG_KEYSTORE_PATH")
val arianaDebugStorePassword = System.getenv("ARIANA_DEBUG_STORE_PASSWORD")
val arianaDebugKeyAlias = System.getenv("ARIANA_DEBUG_KEY_ALIAS")
val arianaDebugKeyPassword = System.getenv("ARIANA_DEBUG_KEY_PASSWORD")
val arianaStableDebugSigningAvailable = listOf(
    arianaDebugKeystorePath,
    arianaDebugStorePassword,
    arianaDebugKeyAlias,
    arianaDebugKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "de.snowworks.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.snowworks.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 46
        versionName = "1.14.6-rc1"
    }

    signingConfigs {
        if (arianaStableDebugSigningAvailable) {
            create("arianaStableDebug") {
                storeFile = file(arianaDebugKeystorePath!!)
                storePassword = arianaDebugStorePassword
                keyAlias = arianaDebugKeyAlias
                keyPassword = arianaDebugKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "BOOTSTRAP", "false")
            buildConfigField("boolean", "SAFE_INSTALL", "false")
            if (arianaStableDebugSigningAvailable) signingConfig = signingConfigs.getByName("arianaStableDebug")
        }
        create("safeinstall") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".safe"
            versionNameSuffix = "-safe"
            buildConfigField("boolean", "BOOTSTRAP", "false")
            buildConfigField("boolean", "SAFE_INSTALL", "true")
            matchingFallbacks += listOf("debug")
            if (arianaStableDebugSigningAvailable) signingConfig = signingConfigs.getByName("arianaStableDebug")
        }
        create("sideload") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".sideload"
            versionNameSuffix = "-sideload"
            buildConfigField("boolean", "BOOTSTRAP", "false")
            buildConfigField("boolean", "SAFE_INSTALL", "false")
            matchingFallbacks += listOf("debug")
            if (arianaStableDebugSigningAvailable) signingConfig = signingConfigs.getByName("arianaStableDebug")
        }
        create("bootstrap") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".bootstrap"
            versionNameSuffix = "-bootstrap"
            buildConfigField("boolean", "BOOTSTRAP", "true")
            buildConfigField("boolean", "SAFE_INSTALL", "false")
            matchingFallbacks += listOf("debug")
            if (arianaStableDebugSigningAvailable) signingConfig = signingConfigs.getByName("arianaStableDebug")
        }
        release {
            isMinifyEnabled = true
            buildConfigField("boolean", "BOOTSTRAP", "false")
            buildConfigField("boolean", "SAFE_INSTALL", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = false; buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
