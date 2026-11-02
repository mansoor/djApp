plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mansoor.djapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mansoor.djapp"
        minSdk = 26
        targetSdk = 35
        // CI builds match the GitHub release tag (v1.0.<run number>).
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = runNumber ?: 1
        versionName = if (runNumber != null) "1.0.$runNumber" else "1.0-dev"
    }

    // Fixed signing key committed to the repo so the SHA1 registered in the
    // Spotify dashboard stays the same for every build (local or CI).
    signingConfigs {
        create("dj") {
            storeFile = file("dj.keystore")
            storePassword = "djapp123"
            keyAlias = "djapp"
            keyPassword = "djapp123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dj")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dj")
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation("com.google.code.gson:gson:2.11.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
