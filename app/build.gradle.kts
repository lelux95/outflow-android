plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.airis.outflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.airis.outflow"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha"
    }

    // Chiave unica per tutte le versioni di prova: si installano una sopra l'altra
    // senza perdere i progressi. Per la pubblicazione sullo Store se ne creera' una nuova.
    signingConfigs {
        create("release") {
            storeFile = file("outflow.keystore")
            storePassword = "outflow-alpha"
            keyAlias = "outflow"
            keyPassword = "outflow-alpha"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
