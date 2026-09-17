plugins {
    id("com.android.application")
}

android {
    namespace = "com.snowworks.arianax88.verify"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.snowworks.arianax88.verify"
        minSdk = 28
        targetSdk = 36
        versionCode = 24
        versionName = "24.0.0"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }
}
