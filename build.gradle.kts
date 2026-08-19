// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.3.0" apply false
    // org.jetbrains.kotlin.android intentionally NOT applied -- AGP 9.0+ provides built-in Kotlin
    // support, replacing this plugin. See https://developer.android.com/build/migrate-to-built-in-kotlin
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
