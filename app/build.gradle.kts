plugins {
    id("com.android.application")
}

android {
    namespace = "com.tomasthrawat.typesafe"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tomasthrawat.typesafe"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
