plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.paperscrape.livewallpaper"
    // API 36 = Android 16 ("Baklava"), released 2025/2026 SDK cycle.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.paperscrape.livewallpaper"
        minSdk = 26 // Android 8.0 - required for adaptive icons & modern WallpaperService features
        targetSdk = 36
        // versionCode is the single source of truth for the GitHub Release tag/title created by
        // CI (.github/workflows/android-build.yml reads this value directly) — bump it every
        // time you ship a new vN so the release name in GitHub matches the version delivered.
        versionCode = 8
        versionName = "8.0"
    }

    signingConfigs {
        // Pinned to a debug keystore committed at the repo root (holds no real security value —
        // it's the standard, publicly-known debug alias/passwords — but MUST stay identical
        // across builds). Without this, Gradle would auto-generate a fresh, randomly-keyed
        // ~/.android/debug.keystore on every machine/CI run, so each build gets signed with a
        // different certificate — Android then refuses to install an "update" over a build
        // signed with a different key ("App not installed" error).
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose (settings UI)
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // DataStore for wallpaper preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
