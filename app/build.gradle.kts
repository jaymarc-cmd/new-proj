import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.likeflow.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.likeflow.app"
        // AccessibilityService gestures need API 24; screenshot-based detection needs API 30 and is gated at runtime.
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-prototype"
    }

    signingConfigs {
        // A fixed, non-secret key for this prototype, so every CI build can install as an update
        // over the previous one. Use a private key before distributing the app any further.
        create("prototype") {
            storeFile = file("likeflow-debug.keystore")
            storePassword = "android"
            keyAlias = "likeflow"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("prototype")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
