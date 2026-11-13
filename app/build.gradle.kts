plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.airis.outflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.airis.outflow"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.6.0-alpha"
        // true nelle versioni di prova: mostra la modalita' Tester. Per lo Store va messo a false.
        buildConfigField("boolean", "ALPHA", "true")
    }

    buildFeatures {
        buildConfig = true
    }

    // Due chiavi:
    // - "release": chiave alpha, unica per tutti gli APK di prova (si installano uno sopra l'altro).
    // - "upload": chiave di caricamento per Google Play, usata SOLO per il bundle .aab.
    //   Non e' nel repository: arriva dai secret di GitHub tramite variabili d'ambiente
    //   (UPLOAD_KEYSTORE_FILE, UPLOAD_KEYSTORE_PASSWORD, UPLOAD_KEY_ALIAS, UPLOAD_KEY_PASSWORD).
    //   Si attiva con: gradle bundleRelease -PplayUpload=true
    val uploadKeystore = System.getenv("UPLOAD_KEYSTORE_FILE")
    val usePlayUpload = (project.findProperty("playUpload") == "true") && !uploadKeystore.isNullOrBlank()

    signingConfigs {
        create("release") {
            storeFile = file("outflow.keystore")
            storePassword = "outflow-alpha"
            keyAlias = "outflow"
            keyPassword = "outflow-alpha"
        }
        if (usePlayUpload) {
            create("upload") {
                storeFile = file(uploadKeystore!!)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (usePlayUpload) "upload" else "release")
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
