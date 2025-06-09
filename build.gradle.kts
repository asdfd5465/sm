// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Make the Android Application plugin available to sub-projects
    id("com.android.application") version "8.3.2" apply false
    // Make the Kotlin Android plugin available to sub-projects
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    // Make the KSP plugin available
    id("com.google.devtools.ksp") version "1.9.23-1.0.19" apply false
}
