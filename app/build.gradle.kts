plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "bankwiser.bankpromotion.material"
    compileSdk = 34

    defaultConfig {
        applicationId = "bankwiser.bankpromotion.material"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing configurations for release builds
    signingConfigs {
        create("release") {
            // Check if environment variables are set (for CI/CD)
            // Otherwise, Gradle will prompt for these during local release builds if not set.
            val keystoreFile = project.rootProject.file("first.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else if (System.getenv("SIGNING_KEYSTORE_BASE64") != null) {
                // This branch is mainly for CI, local builds would ideally use the file directly
                // or have these set in local.properties / gradle.properties
                // For CI, the workflow will decode the base64 keystore into this file path
                storeFile = project.layout.buildDirectory.file("signing/first.keystore").get().asFile
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Apply the signing configuration to the release build type
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Debug builds can use the default debug signing config
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
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    // ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
