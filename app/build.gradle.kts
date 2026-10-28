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
    // runtime behaviour on its own -- the platform's behaviour gates read `targetSdk`, which is
    // 37 below, equal to this.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.paperscrape.livewallpaper"
        minSdk = 26 // Android 8.0 - required for adaptive icons & modern WallpaperService features
        // **37: the app opts into Android 17's behaviour changes** rather than running under
        // Android 16's rules. Assessed change by change against this app's actual code, nothing
        // needs a fix: no reflection, no LAN access, no native libraries, one notification (the
        // update's, whose Android 13 permission update.UpdateNotificationPolicy handles), no
        // SMS/contacts/audio/Bluetooth, no orientation or resizability declarations, and every
        // `startActivity` is from a visible Activity. The two that are not decidable by reading
        // code -- certificate transparency enforced by default, and ECH -- are network behaviour
        // on the five HTTPS hosts Live Weather, the city geocoder and the updater use, and were
        // exercised at runtime.
        targetSdk = 37
        // **Two numbers doing two different jobs, never tied together.**
        //
        // `versionName` names the release and is what a Git tag must equal: CI reads it out of
        // this file and fails the release if the tag disagrees. `versionCode` is Android's own
        // install counter, checked by nothing but the installer, and only has to increase: it
        // answers "is this newer than what is installed", not "which release is this", and has no
        // obligation to be contiguous, only monotonic.
        //
        // Android refuses to install a lower `versionCode` over a higher one, and the only way past
        // that is to uninstall first -- which clears the DataStore, where settings and custom themes
        // live.
        //
        // **One release, one step.** A counter bumped twice in the making of one release is how a
        // build installed for a comparison runs into `adb install -r`'s silent refusal of a
        // downgrade.
        versionCode = 80
        versionName = "5.17"

        // **No API key is baked into this app, and none may be.** `ShippedApkContractTest` enforces it.
        //
        // A key kept out of the repository is still in every published APK if it goes into
        // BuildConfig: a `buildConfigField` of type String becomes a **string constant in the dex**,
        // and R8 renames classes and methods, not string literals -- so a key put there sits in the
        // string table where `strings` on a downloaded APK finds it in one command. A marker put in
        // the field is read back out of the minified `classes.dex`.
        //
        // Shipping a secret to every user is not a thing a build file should make easy, so the
        // mechanism does not exist: there is no env var to set and no field to read. The app has no
        // access of its own to Open-Meteo's higher-limit endpoint and runs no proxy for one; a
        // user's own key reaches it (below).
        //
        // **Live Weather works without a key.** Open-Meteo's free tier needs none
        // (`OpenMeteoProvider` builds the keyless api.open-meteo.com URL), and a user who enters
        // their own key in Settings (`WallpaperPrefs.liveWeatherApiKey`) reaches the
        // higher-limit customer-api.open-meteo.com endpoint -- see
        // `OpenMeteoProvider.resolveApiKey`, which is where that precedence lives.

        // Needed by the instrumented tests in `src/androidTest`: among them the golden images, which
        // render scenes on a device into a real `Bitmap` -- through `CanvasSceneTarget`, and the GL
        // ones through `GlSceneTarget` --, which is why they cannot be JVM tests: `SceneCanvas` passes
        // `android.graphics.Paint` through, and the unit-test classpath's mockable android.jar
        // has no working Paint to read a colour back out of.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // **ARM only. The two Intel ABIs are deliberately not packaged, and that is a decision
        // with a cost.**
        //
        // Two transitive AndroidX dependencies (`androidx.graphics.path` and DataStore's shared
        // counter, which `packaging` below leaves out on every ABI) ship a `.so` per ABI, and AGP
        // packages all four by default. Measured by building both ways rather than estimated, the
        // two x86 ABIs cost the release APK **66 866 B**: four `.so` of 37 444 B of content, and
        // 16 KiB alignment padding for the rest -- eight `.so` against four. A live wallpaper's
        // installed base is ARM phones.
        //
        // **What it costs:** the APK does not install on an x86 emulator, which is the normal
        // way to try an app on a development machine. There is no emulator installed here
        // (no `emulator` package, no system image, no AVD) and CI uses none, so
        // nothing in the current workflow notices -- but it is a door closed, on purpose and
        // knowing that. Adding "x86_64" back to this list is all it takes to
        // reopen it, at the measured price.
        ndk {
            // Lint's ChromeOsAbiSupport asks for x86_64 back; the paragraph above is why it is not.
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    sourceSets {
        getByName("androidTest") {
            // `directories` rather than the deprecated `srcDirs(...)`, which AGP marks
            // @Deprecated("Use `directories` mutable set instead"). Both append to the set the
            // source set already carries, so this is the same declaration in the current API and
            // resolves to the same two directories -- verified by printing the resolved set
            // both ways.
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
        // scripts/generate-release-keystore.sh for how to create your own keystore -- none is
        // generated or committed here, since a release signing key is the
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
        // The CPU protocol needs a build that is what users run: a debug build
        // is not (R8 off, no shrinking: a CPU cost measured on one -- "+4.5 points of CPU for every
        // PNG substituted" -- has proved to be a property of the debug build and not of
        // the artwork), and a real release build cannot be signed on a development machine because
        // the release key only exists on the maintainer's. Written by hand for each measuring
        // session and deleted afterwards, these lines are one forgotten deletion away from the
        // delivery ZIP and a published tag.
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
    // android.compileOptions.targetCompatibility above. The `android.kotlinOptions{}` DSL
    // is deprecated -- see
    // https://developer.android.com/build/migrate-to-built-in-kotlin#migration-steps-migrate-kotlin-options

    buildFeatures {
        compose = true
        buildConfig = true // exposes BuildConfig.VERSION_CODE/VERSION_NAME for the in-app version row
    }

    // **English only, the libraries' strings too.** The app is English-only, on purpose,
    // and without this filter Compose and Material3 still bring
    // their own accessibility labels -- "On", "Off", "Selected", "Tab", "Switch", "Dialog" and the
    // like, the words TalkBack reads -- in 74 languages: about 105 KB of `resources.arsc`, which an APK
    // stores uncompressed. With it, TalkBack reads those labels in English on a phone in any language,
    // as it reads the rest of the app.
    androidResources {
        localeFilters += "en"
    }

    packaging {
        jniLibs {
            // **DataStore's native counter, which nothing loads.** `datastore-preferences` ships
            // `libdatastore_shared_counter.so` for `MultiProcessDataStoreFactory`; this app keeps
            // its stores in one process, and R8 leaves no code in the release dex that loads the
            // library -- 34 KB of the APK with its 16 KiB alignment. `ApkPackagingContractTest`
            // fails if a source starts using the multi-process factory, which would need it back.
            excludes += "**/libdatastore_shared_counter.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // **Nine library files nothing in this app reads.** The Kotlin standard library
            // ships `kotlin/**/*.kotlin_builtins` for `kotlin-reflect`, which this app does not
            // depend on, and kotlinx-coroutines ships `DebugProbesKt.bin` for its JVM debug agent,
            // which cannot attach on Android. Measured on a published APK: 13 199 B
            // compressed, 55 735 B decompressed, and no reference to either name anywhere in
            // `classes.dex` (read with `dexdump`).
            excludes += "/kotlin/**.kotlin_builtins"
            excludes += "/DebugProbesKt.bin"
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

// **The libraries' `META-INF/*.version` files stay out of the builds that are not debuggable.**
// Seventy of them, a few bytes each and about 13 KB with their zip entries, read by one thing only:
// Android Studio's App Inspection, which attaches to a debuggable app. The debug build keeps them,
// so the inspectors still work on it; `release` and `perf` -- what a user installs, and the build
// measured as if it were -- leave them out. Per variant because `packaging` is one block for every
// build type.
androidComponents {
    for (notDebuggable in listOf("release", "perf")) {
        onVariants(selector().withBuildType(notDebuggable)) { variant ->
            variant.packaging.resources.excludes.add("/META-INF/*.version")
        }
    }
}

// **The Dependabot alerts the root script's buildscript force cannot reach.**
//
// The root `build.gradle.kts` forces the *plugin* classpath. AGP also puts its own tooling on
// configurations that belong to **this project**, and `androidLintTool` -- the lint tool's
// classpath -- is one of them. The root block does not touch it, and without this block, with
// AGP 9.4.0 and the root forces, `./gradlew :app:dependencies` shows three alerted coordinates
// alive on exactly that configuration and nowhere else:
//
//   org.apache.commons:commons-lang3:3.16.0     CVE-2025-48924   (MODERATE)
//   org.bouncycastle:bcpkix-jdk18on:1.80.2      CVE-2026-5588    (MODERATE)
//   org.bouncycastle:bcprov-jdk18on:1.80.2      CVE-2026-0636    (MODERATE)
//
// plus `org.apache.httpcomponents:httpclient:4.5.6` (CVE-2020-13956), which the plugin classpath
// lifts to 4.5.14 by ordinary conflict resolution -- so on that classpath it looks
// fixed while `androidLintTool`, where nothing else asks for httpclient, quietly keeps 4.5.6.
// Reading only `buildEnvironment` leaves all four alerts open.
//
// The versions match the root block deliberately: the same artifact resolving to two different
// versions in one build is how you end up debugging a `NoSuchMethodError` that only lint sees.
// Bouncy Castle's three jars move as a set for the same reason.
//
// **The three Bouncy Castle jars are at 1.85**, for two Dependabot alerts -- CVE-2026-8763 /
// GHSA-9pwp-9qqc-pr26 (CRITICAL) and CVE-2026-13506 / GHSA-qp49-qgx5-5m26 (HIGH), both on bcprov,
// both first patched in 1.85. The root block carries the full reasoning; this one moves with it
// because `androidLintTool` resolves `bcprov-jdk18on` on its own and the root force does not reach
// here. The two MODERATE alerts listed above are covered too: 1.85 is past the 1.84 that patched
// them.
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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Jetpack Compose (settings UI). No `ui-tooling`: the project has no `@Preview`.
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    // `Icons` and the core icons the settings draw. material3 does not depend on it, so it is named
    // here. The other icons are vectors of the app's own in `ui/`, not the 11 398 classes of
    // `material-icons-extended`, which every debug build dexes and every R8 run reads.
    implementation("androidx.compose.material:material-icons-core")

    // DataStore for wallpaper preferences
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")

    // The virtual clock `AwaitOnceTest` measures its timeouts on (`runTest`). A test asserting
    // real elapsed time fails on a busy machine -- 5 337 ms against a 3 000 ms bound, once --
    // which is how a gate teaches people to re-run until green. Test-only:
    // it is never packaged into the APK, and it is the same library and version as the
    // `kotlinx-coroutines-android` above, so it brings no new vendor to watch.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

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

// **The sprite artwork is an input to the unit tests, and Gradle cannot see it on its own.**
//
// `SpriteGeometryTest`, `SpriteTintClassTest` and the fidelity checks read the PNGs out of
// `res/drawable-nodpi` at runtime rather than through a resource reference, so nothing connects
// them to the test task's up-to-date checks. Undeclared, editing a sprite and running `test`
// reports UP-TO-DATE and says the old artwork still passes -- which it does, because it is never
// re-read: a `--rerun-tasks` every time somebody remembers, and a wrong green when nobody does.
//
// Declaring the directory is the whole fix. `RELATIVE` path sensitivity because the tests care
// about file names and contents, not about where the checkout lives.
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main/res/drawable-nodpi"))
        .withPropertyName("spriteArtwork")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // The same problem one file over. `InternetInventoryTest` reads AndroidManifest.xml to check the
    // INTERNET inventory against the source, and a manifest-only edit changes nothing Gradle already
    // tracks as an input to the unit tests -- so, undeclared, removing a host from the inventory
    // leaves the test UP-TO-DATE and green.
    inputs.file(layout.projectDirectory.file("src/main/AndroidManifest.xml"))
        .withPropertyName("appManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // And the same problem again, one directory up. `BuildTypeDeclarationTest` and
    // `ShippedApkContractTest` read **this file** and the workflow files; neither is anything
    // Gradle already tracks as an input here, so, undeclared, a build-script edit leaves the unit
    // tests UP-TO-DATE and green without executing a line of either, and the only workaround is to
    // run every time with --rerun-tasks.
    //
    // It is a two-line declaration rather than a habit. The cost is that editing a build script
    // re-runs the unit tests, which is the correct answer: a test that reads a
    // file and does not re-run when that file changes is not checking the file.
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
        .withPropertyName("appBuildScript")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir(".github/workflows"))
        .withPropertyName("ciWorkflows")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // **And the sources the tests read as text.** Changing a Kotlin file recompiles it, but a
    // change to a comment that keeps every line where it was compiles to the same classes, so the
    // unit tests' inputs do not change -- and, undeclared, the guards that read comments
    // (`SpriteMeasurementClaimTest`, `UnitFrameTest`) are answered from the build cache: a wrong
    // sprite size planted in a comment comes back green, `testDebugUnitTest FROM-CACHE`, without
    // the test running. The instrumented
    // sources, the Python generators and the sprite registry under `tools/` and the other resources
    // are read by tests too (`UnitFrameTest`, `SpriteReachabilityTest`, `VehicleAndShopFrontTest`,
    // `SeasonalIconManifestTest`) and compile into nothing the unit tests see. Declared, an edit to
    // any of them re-runs the unit tests, which is the answer the four declarations above give.
    inputs.dir(layout.projectDirectory.dir("src/main/kotlin"))
        .withPropertyName("mainSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/test/kotlin"))
        .withPropertyName("unitTestSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/androidTest/kotlin"))
        .withPropertyName("instrumentedSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // And the goldens: `GoldenUniquenessTest` hashes `src/androidTest/assets/golden/*.png`, assets of the
    // instrumented APK that nothing above declares -- undeclared, a change to the goldens alone leaves the unit tests
    // UP-TO-DATE, and two identical goldens come back green without the test running.
    inputs.dir(layout.projectDirectory.dir("src/androidTest/assets"))
        .withPropertyName("instrumentedAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/main/res"))
        .withPropertyName("appResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.fileTree("tools") {
        include("**/*.py", "**/*.json", "**/*.svg")
        exclude("**/__pycache__/**")
    })
        .withPropertyName("assetTooling")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // And the release notes: `UpdateNotesWholeTest` reads `release-notes/*.md` to show the
    // update dialog gets every release's note whole, and nothing above declares them.
    inputs.dir(rootProject.layout.projectDirectory.dir("release-notes"))
        .withPropertyName("releaseNotes")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
