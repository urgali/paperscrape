// **Version forcing for the *build* classpath -- this is a security fix, not a preference.**
//
// Every Dependabot alert this file closes was raised against a
// transitive dependency of the Android Gradle Plugin, never against anything the app declares:
// `:app:dependencies` shows zero of them on `releaseRuntimeClasspath`, `debugRuntimeClasspath` or
// `releaseCompileClasspath`, so none of them is in the APK and none of them is reachable by a
// user. They are still real -- they run on this machine and on the CI runner, with the checkout
// in front of them -- and the maintainer's instruction is to close them by raising the
// version, not by narrowing what the dependency-submission workflow reports to GitHub.
//
// Raising AGP to 9.5.1 (see `plugins` below) does most of the work by itself. What it does not
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
            // At 1.85 for two Dependabot alerts on this build classpath.
            // Both are on bcprov and both are first patched in 1.85:
            //
            //   CVE-2026-8763  / GHSA-9pwp-9qqc-pr26  CRITICAL  Name Constraints bypass via a
            //                                                   trailing dot in rfc822Name and URI
            //   CVE-2026-13506 / GHSA-qp49-qgx5-5m26  HIGH      lazy ASN.1 sequence forcing resets
            //                                                   the nesting-depth guard
            //
            // Two MODERATE alerts first patched in 1.84 -- CVE-2026-5588 (bcpkix) and
            // CVE-2026-0636 (bcprov) -- are covered too,
            // because 1.85 is past 1.84. AGP 9.5.1 still only brings 1.80.2 by itself, which is
            // enough for the CRITICAL CVE-2025-14813 on bcprov and for nothing since.
            //
            // bcpkix and bcutil carry no alert of their own at 1.84; they are raised with bcprov
            // because Bouncy Castle ships the three as one version-locked set and mixing them is
            // how you get a NoSuchMethodError at build time. 1.85 exists for all three -- checked
            // on Maven Central, not assumed.
            force("org.bouncycastle:bcpkix-jdk18on:1.85")
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.bouncycastle:bcutil-jdk18on:1.85")

            // CVE-2024-29371 (HIGH) in jose4j, reached through
            // com.android.tools.build:bundletool. First patched in 0.9.6; 0.9.7 is the current
            // release of the same line.
            force("org.bitbucket.b_c:jose4j:0.9.7")

            // CVE-2021-33813 (HIGH), XXE in jdom2, reached through jetifier-processor. 2.0.6.1 is
            // the patch release for exactly this: same 2.0.6 code plus the fix.
            force("org.jdom:jdom2:2.0.6.1")

            // CVE-2025-48924 (MODERATE), uncontrolled recursion in ClassUtils, reached through
            // commons-compress. First patched in 3.18.0.
            force("org.apache.commons:commons-lang3:3.21.0")

            // CVE-2020-13956 (MODERATE), URI parsing. `httpmime:4.5.6` asks for `httpclient:4.5.6`
            // and on this classpath conflict resolution already lifts it to 4.5.14 -- but it does
            // *not* on the `androidLintTool` configuration over in `app/build.gradle.kts`, which
            // is why the same force appears there too. 4.5.14 is the last release of the 4.5 line
            // and is past the 4.5.13 that carries the fix.
            force("org.apache.httpcomponents:httpclient:4.5.14")

            // CVE-2026-102554 / GHSA-xxph-c9ww-hj94 (HIGH), unbounded allocation when a
            // CompactHashMap, CompactHashSet or MapMakerInternalMap is deserialized, in every Guava
            // from 4.0 through 33.7.1. AGP 9.5.1 brings 18.0, 32.0.1-jre, 33.3.1-jre and 33.4.0-jre
            // onto this classpath (lint-gradle 32.5.1 brings two of them onto `androidLintTool`, in
            // `app/build.gradle.kts`); 33.7.2-jre is the first patched
            // release, and 33.8.0-jre, the release after it, is what is forced here and in
            // `app/build.gradle.kts` for `androidLintTool`.
            force("com.google.guava:guava:33.8.0-jre")
        }
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // **AGP 9.5.1 keeps Netty off this build, and not by hiding it.** Nothing in this project asks for Netty: AGP 9.3.1
    // creates eleven `unified-test-platform-*` configurations on `:app`, two of which resolve
    // `com.google.testing.platform:core` and `io.grpc:grpc-netty`, and Netty comes in under those -- six `io.netty`
    // artifacts with alerts of their own. AGP 9.5.1 has a single `unified-test-platform-gradle-work-action` that resolves
    // neither gRPC nor Netty: `:app:dependencies` holds no Netty line. There is no Netty declaration of ours to raise; the
    // newer AGP is the supported way to change what AGP drags in.
    //
    // A new release is taken once it is two weeks old and no alert names it. The 5.20 takes 9.5.1, Guava
    // 33.8.0-jre and Gradle 9.9.1 on the maintainer's word of 2026-11-16 («voglio aggiungere anche
    // aggiornamento componenti nonostante il rilascio sia prima del 24»), before AGP 9.5.1 (2026-11-05)
    // and Gradle 9.9.1 (2026-11-10) are two weeks old: the alerts were read again on the day they were
    // taken, and none names any of the three.
    id("com.android.application") version "9.5.1" apply false
    // org.jetbrains.kotlin.android intentionally NOT applied -- AGP 9.0+ provides built-in Kotlin
    // support, replacing this plugin. See https://developer.android.com/build/migrate-to-built-in-kotlin
    //
    // **2.4.20 or later for CVE-2026-53914**, unsafe deserialization in the Kotlin build cache
    // (MODERATE). The alert names `org.jetbrains.kotlin:kotlin-gradle-plugin`, which this project
    // never applies and never declares -- it arrives as an AGP transitive at 2.2.10 and is then
    // lifted to whatever this Compose plugin asks for, because the two share a version line. So
    // the version that closes the alert is this one, and raising it is what actually moves KGP.
    //
    // Dependabot reports the first patched version as `2.4.20-Beta1`. That is the first build
    // containing the fix, not the only one: 2.4.21, a later final release of the same line, sorts
    // above the Beta and is what is taken here. No pre-release version enters the toolchain.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
