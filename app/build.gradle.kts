plugins {
    id("com.android.application")
}

android {
    namespace = "com.garrettstorts.iptvhub"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.garrettstorts.iptvhub"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
