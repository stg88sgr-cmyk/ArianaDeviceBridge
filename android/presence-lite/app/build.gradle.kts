plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.snowworks.presencelite"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.snowworks.presencelite"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "2.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
