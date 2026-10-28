# Add project specific ProGuard rules here.

# The service and its engine, kept whole: every member, with its name, out of R8's inlining. Nothing
# needs that. The system creates the service by reflection from the manifest, and the rules AAPT2
# writes for the manifest already keep its constructor; the engine is created by `onCreateEngine()`,
# and R8 keeps the framework's overrides on its own. What the two lines cost is a few KB of dex and
# a call R8 could have inlined. Dropping them changes the release dex the CPU figures are measured
# on (the `perf` build), so it is a change to measure on the phone, not a comment to fix.
-keep class com.paperscrape.livewallpaper.engine.PaperWallpaperService { *; }
-keep class com.paperscrape.livewallpaper.engine.PaperWallpaperService$PaperEngine { *; }

# No rule keeps the `prefs` package. DataStore Preferences stores primitives under the string keys
# it is given and never reflects on a class of the app; the one reflective read near the settings,
# `PrecipitationType.valueOf(...)`, is an enum's, and `proguard-android-optimize.txt` keeps enums'
# `values`/`valueOf` by itself.
