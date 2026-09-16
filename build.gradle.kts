// **Version forcing for the *build* classpath -- this is a security fix, not a preference.**
//
// Every one of the 49 Dependabot alerts this repository carried in v5.2 was raised against a
// transitive dependency of the Android Gradle Plugin, never against anything the app declares:
// `:app:dependencies` shows zero of them on `releaseRuntimeClasspath`, `debugRuntimeClasspath` or
// `releaseCompileClasspath`, so none of them is in the APK and none of them is reachable by a
// user. They are still real -- they run on this machine and on the CI runner, with the checkout
// in front of them -- and the maintainer's instruction for v5.3 was to close them by raising the
// version, not by narrowing what the dependency-submission workflow reports to GitHub.
//
// Raising AGP to 9.4.0 (see `plugins` below) does most of the work by itself. What it does not
// reach is forced here. `force` rather than a constraint because these are *someone else's*
// transitives: a constraint states a floor that a stronger declaration downstream can still lose
// to, while `force` is unconditional, which is what a CVE fix has to be.
//
// This block must sit before `plugins {}` -- Kotlin DSL allows exactly `buildscript` then
// `plugins` -- and it does reach the plugin classpath the `plugins {}` DSL resolves: verified by
// reading `./gradlew buildEnvironment` before and after, where each coordinate below turns from
// `x:old` into `x:old -> new`.
//
// None of these coordinates exists on any configuration that feeds the APK, so nothing here can
// change a shipped byte. That was checked, not assumed.
buildscript {
    configurations.classpath {
        resolutionStrategy {
            // CVE-2026-5588 (bcpkix, MODERATE) and CVE-2026-0636 (bcprov, MODERATE) are both
            // first patched in 1.84. AGP 9.4.0 already moves this line from 1.79 to 1.80.2, which
            // is enough for the CRITICAL CVE-2025-14813 on bcprov but not for the other two.
            // bcutil is not itself under an alert; it is forced alongside the other two because
            // Bouncy Castle ships the three as one version-locked set and mixing them is how you
            // get a NoSuchMethodError at build time.
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.bouncycastle:bcutil-jdk18on:1.84")

            // CVE-2024-29371 (HIGH) in jose4j, reached through
            // com.android.tools.build:bundletool. First patched in 0.9.6; 0.9.7 is the current
            // release of the same line.
            force("org.bitbucket.b_c:jose4j:0.9.7")

            // CVE-2021-33813 (HIGH), XXE in jdom2, reached through jetifier-processor. 2.0.6.1 is
            // the patch release for exactly this: same 2.0.6 code plus the fix.
            force("org.jdom:jdom2:2.0.6.1")

            // CVE-2025-48924 (MODERATE), uncontrolled recursion in ClassUtils, reached through
            // commons-compress. First patched in 3.18.0; 3.20.0 is current.
            force("org.apache.commons:commons-lang3:3.20.0")

            // CVE-2020-13956 (MODERATE), URI parsing. `httpmime:4.5.6` asks for `httpclient:4.5.6`
            // and on this classpath conflict resolution already lifts it to 4.5.14 -- but it does
            // *not* on the `androidLintTool` configuration over in `app/build.gradle.kts`, which
            // is why the same force appears there too. 4.5.14 is the last release of the 4.5 line
            // and is past the 4.5.13 that carries the fix.
            force("org.apache.httpcomponents:httpclient:4.5.14")
        }
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // **9.3.1 -> 9.4.0 closes 41 of the 49 alerts on its own, and none of them by hiding.**
    //
    // 41 alerts were against six `io.netty` artifacts at 4.1.93.Final and 4.1.110.Final. Nothing
    // in this project asks for Netty: AGP 9.3.1 created eleven `unified-test-platform-*`
    // configurations on `:app`, two of which resolved `com.google.testing.platform:core` and
    // `io.grpc:grpc-netty`, and Netty came in under those. AGP 9.4.0 collapses all eleven into a
    // single `unified-test-platform-gradle-work-action` that resolves neither gRPC nor Netty --
    // `:app:dependencies` goes from 84 Netty lines to zero. There was no Netty version to raise
    // here, because there was never a Netty declaration of ours to raise; the fix is the newer
    // AGP, which is the supported way to change what AGP drags in.
    //
    // 9.4.0 is the current *stable* AGP. 9.5.0 exists only as alpha and is deliberately not taken.
    id("com.android.application") version "9.4.0" apply false
    // org.jetbrains.kotlin.android intentionally NOT applied -- AGP 9.0+ provides built-in Kotlin
    // support, replacing this plugin. See https://developer.android.com/build/migrate-to-built-in-kotlin
    //
    // **2.2.21 -> 2.4.20 for CVE-2026-53914**, unsafe deserialization in the Kotlin build cache
    // (MODERATE). The alert names `org.jetbrains.kotlin:kotlin-gradle-plugin`, which this project
    // never applies and never declares -- it arrives as an AGP transitive at 2.2.10 and is then
    // lifted to whatever this Compose plugin asks for, because the two share a version line. So
    // the version that closes the alert is this one, and raising it is what actually moves KGP.
    //
    // Dependabot reports the first patched version as `2.4.20-Beta1`. That is the first build
    // containing the fix, not the only one: `2.4.20` final is released and is the current latest,
    // it sorts above the Beta, and it is what is taken here. No pre-release version enters the
    // toolchain.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
