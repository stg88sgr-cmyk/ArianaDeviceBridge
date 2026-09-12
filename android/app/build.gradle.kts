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
        versionCode = 5
        versionName = "1.4.0"
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
            if (arianaStableDebugSigningAvailable) {
                signingConfig = signingConfigs.getByName("arianaStableDebug")
            }
        }
        create("bootstrap") {
            initWith(getByName("debug"))
            versionNameSuffix = "-bootstrap"
            buildConfigField("boolean", "BOOTSTRAP", "true")
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            buildConfigField("boolean", "BOOTSTRAP", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = false
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
}
