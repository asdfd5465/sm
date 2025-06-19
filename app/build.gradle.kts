plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "bankwiser.bankpromotion.material"
    compileSdk = 34

    // Correctly configure assetPacks here
    assetPacks.addAll(listOf(":contentpack")) // Use addAll for a Set

    defaultConfig {
        applicationId = "bankwiser.bankpromotion.material"
        minSdk = 26
        targetSdk = 34
        versionCode = 20
        versionName = "1.20"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }

    }

    val storeFileFromEnv = System.getenv("SIGNING_KEYSTORE_FILE_PATH") // Custom name for the file path
    val storePasswordFromEnv = System.getenv("SIGNING_KEYSTORE_PASSWORD_ENV") // Custom env var name
    val keyAliasFromEnv = System.getenv("SIGNING_KEY_ALIAS_ENV")         // Custom env var name
    val keyPasswordFromEnv = System.getenv("SIGNING_KEY_PASSWORD_ENV")     // Custom env var name

    signingConfigs {
        create("release") {
            if (storeFileFromEnv?.isNotEmpty() == true &&
                storePasswordFromEnv?.isNotEmpty() == true &&
                keyAliasFromEnv?.isNotEmpty() == true &&
                keyPasswordFromEnv?.isNotEmpty() == true) {

                val keystoreFile = project.file(storeFileFromEnv) // Gradle will resolve this path
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = storePasswordFromEnv
                    keyAlias = keyAliasFromEnv
                    keyPassword = keyPasswordFromEnv
                    println("Release signing configured using CI environment variables. Keystore: ${keystoreFile.absolutePath}")
                } else {
                    println("WARNING: Keystore file specified by SIGNING_KEYSTORE_FILE_PATH ('$storeFileFromEnv') does not exist. Release build may not be signed or may fail.")
                }
            } else {
                println("WARNING: Release signing information not fully provided via CI environment variables. Release build may not be signed or may fail. Missing one or more of: SIGNING_KEYSTORE_FILE_PATH, SIGNING_KEYSTORE_PASSWORD_ENV, SIGNING_KEY_ALIAS_ENV, SIGNING_KEY_PASSWORD_ENV")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }

    packagingOptions {
    jniLibs.pickFirsts.addAll(listOf(
        "lib/x86/libsqlcipher.so",
        "lib/x86_64/libsqlcipher.so",
        "lib/armeabi-v7a/libsqlcipher.so",
        "lib/arm64-v8a/libsqlcipher.so",
        "lib/**/libsqlcipher.so"
    ))
    }
}
dependencies {
    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Corrected Material Icons Extended dependency
    implementation("androidx.compose.material:material-icons-extended:1.6.7") 
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.android.gms:play-services-auth:21.1.0")
    implementation("com.google.firebase:firebase-config-ktx") // For Remote Config
    implementation("com.google.firebase:firebase-analytics-ktx") // Recommended with Remote Config

    // Play Asset Delivery
    implementation("com.google.android.play:asset-delivery-ktx:2.2.2") // Or latest

    // SQLCipher
    implementation("net.zetetic:android-database-sqlcipher:4.5.0")
    // AndroidX SQLite support library (often needed with SQLCipher for modern Android features)
    implementation("androidx.sqlite:sqlite:2.4.0") // Use the base sqlite, not just -ktx
    implementation("androidx.sqlite:sqlite-framework:2.4.0") // Framework bindings

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Media3 for Audio Playback
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    
    // Google Play Billing Library
    implementation("com.android.billingclient:billing-ktx:6.2.0") // Or latest version
}
