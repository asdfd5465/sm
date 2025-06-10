plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Read signing properties from environment variables, now including a file path
val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
val keystorePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
val keyAlias = System.getenv("SIGNING_KEY_ALIAS")
val keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
val useRemoteSigning = keystorePath != null && keystorePassword != null && keyAlias != null && keyPassword != null

android {
    namespace = "bankwiser.bankpromotion.material"

    if (useRemoteSigning) {
        signingConfigs {
            create("release") {
                val keystoreFile = project.file(keystorePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    this.storePassword = keystorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                } else {
                    println("WARNING: Keystore file not found at path: $keystorePath")
                }
            }
        }
    }

    defaultConfig {
        applicationId = "bankwiser.bankpromotion.material"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (useRemoteSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
        release {
            if (useRemoteSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Firebase & Google Auth
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    // New dependency for modern Credentials API
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
}
