plugins {
    id("com.android.application")
    // org.jetbrains.kotlin.android intentionally NOT applied -- see root build.gradle.kts comment.
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
        // **Two numbers doing two different jobs — see AI_PROJECT_RULES.md §11.A.**
        //
        // `versionName` names the release and is what a Git tag must equal: CI reads it out of
        // this file and fails the release if the tag disagrees. `versionCode` is Android's own
        // install counter, checked by nothing but the installer, and only has to increase.
        //
        // v1.0 → 1, v1.1 → 2, v2.0 → 4, v2.1 → 5. Three is skipped because no v1.2 was ever
        // released; the counter has no obligation to be contiguous, only monotonic, and leaving
        // the gap is more honest than renumbering a release that does not exist.
        //
        // Android refuses to install a lower `versionCode` over a higher one, so anything still
        // carrying the pre-release internal builds (which reached 76) must be uninstalled first —
        // and uninstalling clears the DataStore, which is where settings and custom themes live.
        versionCode = 5
        versionName = "2.1"

        // Baked into BuildConfig at compile time from the PAPERSCRAPE_OPENMETEO_API_KEY env var
        // (populated via a GitHub Secret in CI, same pattern as the release signing secrets
        // above -- never committed in plaintext). Open-Meteo's free tier works with NO key at
        // all (WeatherRepository falls back to the keyless api.open-meteo.com endpoint when this
        // is blank), so this is purely an *optional* upgrade to Open-Meteo's higher-limit
        // customer-api.open-meteo.com endpoint -- aa's own key, shipped with the app so most
        // users never need to find or enter one themselves. A user who enters their own key in
        // Settings (WallpaperPrefs.liveWeatherApiKey) always takes priority over this one -- see
        // WeatherRepository.resolveApiKey.
        val openMeteoApiKey = System.getenv("PAPERSCRAPE_OPENMETEO_API_KEY") ?: ""
        buildConfigField("String", "OPENMETEO_API_KEY", "\"$openMeteoApiKey\"")
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

        // Real release signing, sourced *only* from environment variables -- never from a
        // committed file or a hardcoded password, unlike the debug config above (whose password
        // is intentionally public). Populate these locally via `export` before running
        // `./gradlew assembleRelease`, or via the RELEASE_* GitHub Secrets consumed by the
        // `release` CI job (see .github/workflows/android-build.yml and
        // scripts/generate-release-keystore.sh for how to create your own keystore -- Claude
        // deliberately did not generate one on your behalf, since a release signing key is the
        // app's permanent identity and should only ever exist on your own machine and in your
        // own GitHub Secrets, never pass through a third party).
        //
        // Left entirely absent (not just empty-stringed) when the env vars aren't set, so a
        // local `./gradlew assembleRelease` run without them produces an *unsigned* APK that
        // fails to install -- loud and obvious -- rather than silently falling back to something
        // that looks shippable but isn't signed with the real key.
        val releaseStorePath = System.getenv("PAPERSCRAPE_RELEASE_STORE_FILE")
        if (!releaseStorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("PAPERSCRAPE_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("PAPERSCRAPE_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("PAPERSCRAPE_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach real signing if the environment actually provided one (see
            // signingConfigs above) -- see that comment for why this isn't silently skipped.
            if (!System.getenv("PAPERSCRAPE_RELEASE_STORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    // No explicit kotlin { compilerOptions { jvmTarget = ... } } needed: with built-in Kotlin
    // (AGP 9.0+, see the plugins{} comment above), jvmTarget defaults to
    // android.compileOptions.targetCompatibility above. The old `android.kotlinOptions{}` DSL
    // this replaces is deprecated -- see
    // https://developer.android.com/build/migrate-to-built-in-kotlin#migration-steps-migrate-kotlin-options

    buildFeatures {
        compose = true
        buildConfig = true // exposes BuildConfig.VERSION_CODE/VERSION_NAME for the in-app version row
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Surface assertion messages, stack traces and per-test results in the console.
            // Without this a CI failure shows only "there were failing tests" plus a path to an
            // HTML report that does not exist on the runner after the job ends.
            all { test ->
                test.testLogging {
                    events("passed", "skipped", "failed")
                    setExceptionFormat("full")
                }
            }
            // Deliberately NOT enabling isReturnDefaultValues. Every class under unit test here
            // is pure JVM logic with no Android imports; if a test ever needs a stubbed
            // framework call, that is a signal the class under test has the wrong dependencies,
            // not a reason to silence the stub.
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

    // `org.json` ships inside the Android framework, so at compile time it resolves against
    // android.jar. Local (JVM) unit tests run against the *mockable* android.jar instead, where
    // every framework method is stubbed and throws "not mocked" -- which would make any test
    // that touches JSONObject/JSONArray useless. Adding the real reference implementation as a
    // test-only dependency puts a working org.json ahead of the stubbed one on the unit test
    // classpath. It is test-only: it is never packaged into the APK, so the app still uses the
    // platform's own implementation on device.
    //
    // Caveat worth knowing: Android's bundled org.json is Harmony-derived and is not
    // byte-for-byte identical to this reference implementation. For the plain object/array/
    // primitive shapes this project persists they agree, but do not rely on unit tests to prove
    // exotic edge-case parsing behaviour matches the device.
    testImplementation("org.json:json:20260719")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
