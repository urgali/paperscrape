plugins {
    id("com.android.application")
    // org.jetbrains.kotlin.android intentionally NOT applied -- see root build.gradle.kts comment.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.paperscrape.livewallpaper"
    // API 37 = Android 17. This is a *compile-time* setting only: it says which android.jar
    // the code is compiled and linked against, and it is what androidx.core 1.19 and the
    // Compose 1.12 line require (`minCompileSdk=37` in their AAR metadata). It changes no
    // runtime behaviour on its own -- the platform's behaviour gates read `targetSdk`, which
    // was deliberately held at 36 while this upgrade landed so it could not move the app's
    // behaviour. That hold ended in v4.0: `targetSdk` is 37 below, equal to this.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.paperscrape.livewallpaper"
        minSdk = 26 // Android 8.0 - required for adaptive icons & modern WallpaperService features
        // **Raised to 37 in v4.0**, which is the whole point of that release: the app now opts
        // into Android 17's behaviour changes rather than running under Android 16's rules.
        // Assessed change by change against this app's actual code in v3.8 and again from the
        // v3.9 baseline in v4.0 -- see RELEASE_HISTORY.md. Nothing needed a fix: no reflection, no
        // LAN access, no native libraries, no notifications, no SMS/contacts/audio/Bluetooth, no
        // orientation or resizability declarations, and every `startActivity` is from a visible
        // Activity. The two that are not decidable by reading code -- certificate transparency
        // enforced by default, and ECH -- are network behaviour on the five HTTPS hosts Live
        // Weather, the city geocoder and the updater use, and were exercised at runtime.
        targetSdk = 37
        // **Two numbers doing two different jobs — see AI_PROJECT_RULES.md §11.A.**
        //
        // `versionName` names the release and is what a Git tag must equal: CI reads it out of
        // this file and fails the release if the tag disagrees. `versionCode` is Android's own
        // install counter, checked by nothing but the installer, and only has to increase.
        //
        // v1.0 → 1, v1.1 → 2, v2.0 → 4, v2.1 → 5, v2.2 → 6, v2.3 → 7, v2.4 → 8, v2.5 → 9, v2.6 → 10, v2.7 → 11, v2.8 → 12, v2.9 → 13, v2.10 → 14, v2.11 → 15, v2.12 → 16, v2.13 → 17, v2.14 → 18, v2.15 → 19, v2.16 → 20, v3.0 → 21, v3.1 → 22, v3.2 → 23, v3.3 → 24, v3.4 → 25, v3.5 → 26, v3.6 → 27, v3.7 → 28, v3.8 → 29. Three is skipped
        // because no v1.2 was ever released; the counter has no obligation to be contiguous, only
        // monotonic, and leaving the gap is more honest than renumbering a release that never was.
        //
        // Android refuses to install a lower `versionCode` over a higher one, so anything still
        // carrying the pre-release internal builds (which reached 76) must be uninstalled first —
        // and uninstalling clears the DataStore, which is where settings and custom themes live.
        //
        // **v5.0 is a rename, not a second bump.** This release was prepared as 4.32 and renamed
        // for the neighbourhood redraw, which is a change of visual language rather than a tweak
        // to the old drawing. It was never tagged and never published, so no user ever saw a 4.32
        // and the published sequence runs v4.31 → v5.0 with nothing missing. `versionCode` stays
        // at the 63 this round's Fase 0 set: it answers "is this newer than what is installed",
        // not "which release is this", and bumping it twice in one round is exactly how v4.31
        // walked into `adb install -r`'s silent downgrade refusal (`BACKLOG_v4_31.md` item 111).
        //
        // v5.0 → 63, v5.1 → 64, v5.2 → 65, v5.3 → 66, v5.4 → 67, v5.5 → 68, v5.6 → 69, v5.7 → 70. Ordinary bumps: one release, one step.
        versionCode = 70
        versionName = "5.7"

        // **No API key is baked into this app, and none may be.** `ShippedApkContractTest` enforces it.
        //
        // Until v5.3 the maintainer's own Open-Meteo key arrived here from a
        // PAPERSCRAPE_OPENMETEO_API_KEY env var, populated by a GitHub Secret in CI, and went into
        // BuildConfig via `buildConfigField`. The comment that stood here said it was "never
        // committed in plaintext", which was **true and completely misleading**: it was never in
        // the repository, and it was in every published APK. A `buildConfigField` of type String
        // becomes a **string constant in the dex**, and R8 renames classes and methods, not string
        // literals -- so the key sat in the string table between `ATOMIC` and `AUTUMN` where
        // `strings` on a downloaded APK finds it in one command. The v5.3B audit demonstrated it
        // by building with a marker and reading the marker back out of the minified `classes.dex`.
        //
        // Shipping a secret to every user is not a thing a build file should make easy, so the
        // mechanism is gone rather than merely unused: there is no env var to set and no field to
        // read. The maintainer decided in v5.3 to drop the higher-limit endpoint rather than run a
        // proxy for it.
        //
        // **Nothing about Live Weather changes for a user.** Open-Meteo's free tier needs no key
        // (`OpenMeteoProvider` builds the keyless api.open-meteo.com URL), and a user who enters
        // their own key in Settings (`WallpaperPrefs.liveWeatherApiKey`) still reaches the
        // higher-limit customer-api.open-meteo.com endpoint exactly as before -- see
        // `OpenMeteoProvider.resolveApiKey`, which is where that precedence actually lives. (The
        // comments here, in WallpaperPrefs and in WeatherTimeScreen all used to point at a
        // `WeatherRepository.resolveApiKey` that has never existed.)

        // Needed by the golden-image tests in `src/androidTest`, which are the only instrumented
        // tests the project has. They render scenes through `CanvasSceneTarget` into a real
        // `Bitmap` on a device, which is why they cannot be JVM tests: `SceneCanvas` passes
        // `android.graphics.Paint` through, and the unit-test classpath's mockable android.jar
        // has no working Paint to read a colour back out of.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // **ARM only. The two Intel ABIs are deliberately not packaged, and that is a decision
        // with a cost.**
        //
        // Two transitive AndroidX dependencies (`androidx.graphics.path` and DataStore's shared
        // counter) ship a `.so` per ABI, and AGP packages all four by default. Measured by
        // building both ways rather than estimated: the release APK went from **2 732 518 B to
        // 2 665 676 B, -66 842 B, -2.45 %**, eight `.so` down to four and 467 entries down to 463.
        // (A few of those bytes belong to the other v5.3 repairs in the same build -- the removed
        // BuildConfig field, the manifest attribute; the four x86 `.so` themselves are 37 444 B of
        // content, and 16 KiB alignment padding is the rest. The v5.3B audit isolated the ABI
        // change alone at -66 866 B.) For scale: the v5.3 dependency round added **16 756 B**
        // closing 41 netty advisories, and that was argued over line by line. A live wallpaper's
        // installed base is ARM phones.
        //
        // **What it costs:** the APK no longer installs on an x86 emulator, which is the normal
        // way to try an app on a development machine. There is no emulator installed here
        // (CLAUDE.md §3: no `emulator` package, no system image, no AVD) and CI uses none, so
        // nothing in the current workflow notices -- but it is a door being closed, and the
        // maintainer closed it knowing that. Adding "x86_64" back to this list is all it takes to
        // reopen it, at the measured price.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    sourceSets {
        getByName("androidTest") {
            // `directories` rather than the deprecated `srcDirs(...)`, which AGP marks
            // @Deprecated("Use `directories` mutable set instead"). Both append to the set the
            // source set already carries, so this is the same declaration in the current API and
            // resolves to the same two directories -- verified by printing the resolved set
            // before and after the edit.
            java.directories.add("src/androidTest/kotlin")
        }
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
        // **The build the CPU protocol is measured on, and the reason it is committed.**
        //
        // `BACKLOG_v4_26.md` item 32's protocol needs a build that is what users run: a debug build
        // is not (R8 off, no shrinking, and v4.26 measured that a conclusion drawn on one — "+4.5
        // points of CPU for every PNG substituted" — was a property of the debug build and not of
        // the artwork), and a real release build cannot be signed on a development machine because
        // the release key only exists on the maintainer's. So every measuring session used to write
        // these lines by hand and delete them afterwards, and in v4.25 the deletion is the step that
        // failed: the block reached the delivery ZIP and the published tag.
        //
        // It is committed rather than remembered because a rule enforced by remembering is a rule
        // that fails on the session that forgets, and because two sessions measuring the same thing
        // should be measuring the same binary. It holds no secret: it signs with the `debug.keystore`
        // that is committed at the repository root for exactly this class of reason, and it carries
        // `.debug` so it installs beside a real one instead of over it.
        //
        // **It is never published.** No workflow builds it — CI builds `assembleRelease` for the
        // release job and `assembleDebug`/`test`/`lint` for the checks — so nothing a user installs
        // is ever built from here. `BuildTypeDeclarationTest` is what keeps that true: it pins the
        // exact set of build types this file declares and pins this one to the debug signing config
        // and the `.debug` suffix, so it cannot quietly turn into something shippable.
        create("perf") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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

// **The Dependabot alerts the root script's buildscript force cannot reach.**
//
// The root `build.gradle.kts` forces the *plugin* classpath. AGP also puts its own tooling on
// configurations that belong to **this project**, and `androidLintTool` -- the lint tool's
// classpath -- is one of them. The root block does not touch it, and after AGP 9.4.0 and the
// root forces landed, `./gradlew :app:dependencies` still showed three alerted coordinates alive
// on exactly that configuration and nowhere else:
//
//   org.apache.commons:commons-lang3:3.16.0     CVE-2025-48924   (MODERATE)
//   org.bouncycastle:bcpkix-jdk18on:1.80.2      CVE-2026-5588    (MODERATE)
//   org.bouncycastle:bcprov-jdk18on:1.80.2      CVE-2026-0636    (MODERATE)
//
// plus `org.apache.httpcomponents:httpclient:4.5.6` (CVE-2020-13956), which the plugin classpath
// had already lifted to 4.5.14 by ordinary conflict resolution -- so on that classpath it looked
// fixed while `androidLintTool`, where nothing else asks for httpclient, quietly kept 4.5.6.
// Reading only `buildEnvironment` would have called this round finished with four alerts open.
//
// The versions match the root block deliberately: the same artifact resolving to two different
// versions in one build is how you end up debugging a `NoSuchMethodError` that only lint sees.
// Bouncy Castle's three jars move as a set for the same reason.
//
// **v5.5C raised the three Bouncy Castle jars from 1.84 to 1.85**, for the repository's last two
// Dependabot alerts -- CVE-2026-8763 / GHSA-9pwp-9qqc-pr26 (CRITICAL) and CVE-2026-13506 /
// GHSA-qp49-qgx5-5m26 (HIGH), both on bcprov, both first patched in 1.85. The root block carries
// the full reasoning; this one is raised with it because `androidLintTool` resolves
// `bcprov-jdk18on` on its own and the root force does not reach here. The two MODERATE alerts
// listed above stay covered: 1.85 is past the 1.84 that patched them.
//
// `configureEach` rather than `getByName("androidLintTool")` because AGP creates that
// configuration lazily; naming it eagerly resolves it during configuration. Forcing across every
// configuration is safe here only because it was checked: none of these five coordinates appears
// on `releaseRuntimeClasspath`, `debugRuntimeClasspath` or `releaseCompileClasspath` -- those
// carry androidx, Compose, coroutines and kotlin-stdlib and nothing else -- so no force here can
// reach the APK. Do not extend this list with a coordinate the app actually ships without
// re-checking that.
configurations.configureEach {
    resolutionStrategy {
        force("org.bouncycastle:bcpkix-jdk18on:1.85")
        force("org.bouncycastle:bcprov-jdk18on:1.85")
        force("org.bouncycastle:bcutil-jdk18on:1.85")
        force("org.apache.commons:commons-lang3:3.20.0")
        force("org.apache.httpcomponents:httpclient:4.5.14")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Jetpack Compose (settings UI)
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // DataStore for wallpaper preferences
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

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
    testImplementation("org.json:json:20260814")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

// **The sprite artwork is an input to the unit tests, and Gradle could not see it.**
//
// `SpriteGeometryTest`, `SpriteTintClassTest` and the fidelity checks read the PNGs out of
// `res/drawable-nodpi` at runtime rather than through a resource reference, so nothing connected
// them to the test task's up-to-date checks. Editing a sprite and running `test` reported
// UP-TO-DATE and told you the old artwork still passed -- which it did, because it was never
// re-read. It cost a `--rerun-tasks` every time somebody remembered, and a wrong green when
// nobody did.
//
// Declaring the directory is the whole fix. `RELATIVE` path sensitivity because the tests care
// about file names and contents, not about where the checkout lives.
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main/res/drawable-nodpi"))
        .withPropertyName("spriteArtwork")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // The same problem one file over. `InternetInventoryTest` reads AndroidManifest.xml to check the
    // INTERNET inventory against the source, and a manifest-only edit changes nothing Gradle already
    // tracks as an input to the unit tests -- so removing a host from the inventory left the test
    // UP-TO-DATE and green. Kotlin sources are covered already, because changing one recompiles.
    inputs.file(layout.projectDirectory.file("src/main/AndroidManifest.xml"))
        .withPropertyName("appManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // And the same problem again, one directory up. `BuildTypeDeclarationTest` and
    // `ShippedApkContractTest` read **this file** and the workflow files; neither is anything
    // Gradle already tracks as an input here, so a build-script edit left the unit tests
    // UP-TO-DATE and green without executing a line of either. `BuildTypeDeclarationTest`'s own
    // KDoc records three mutations that all appeared to be caught by nothing for this reason, and
    // CLAUDE.md §7 carries it as a standing trap with "run with --rerun-tasks" as the workaround.
    //
    // It is a two-line fix rather than a habit, so v5.3 declares them. The cost is that editing
    // a build script now re-runs the unit tests, which is the correct answer: a test that reads a
    // file and does not re-run when that file changes is not checking the file.
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
        .withPropertyName("appBuildScript")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir(".github/workflows"))
        .withPropertyName("ciWorkflows")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
