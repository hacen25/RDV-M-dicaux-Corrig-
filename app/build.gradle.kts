plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Nom du fichier APK : rdv-medicaux-debug.apk
base {
    archivesName.set("rdv-medicaux")
}

android {
    namespace = "fr.rdvmedicaux"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.rdvmedicaux"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
