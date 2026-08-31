# Add project specific ProGuard rules here.

# Keep the wallpaper service and its engine reachable via reflection by the system.
-keep class com.paperscrape.livewallpaper.engine.PaperWallpaperService { *; }
-keep class com.paperscrape.livewallpaper.engine.PaperWallpaperService$PaperEngine { *; }

# SEC-09: the `-keep class com.paperscrape.livewallpaper.prefs.** { *; }` that used to be here is
# gone. Its stated reason -- "preference data classes used by DataStore serialization" -- does not
# describe anything this app does: DataStore Preferences stores primitives under string keys it is
# given, and never reflects on a user class. The only reflective read anywhere near preferences is
# `PrecipitationType.valueOf(...)`, an enum in the `engine` package, and enum `values`/`valueOf` are
# already kept by `proguard-android-optimize.txt`'s own rules. Keeping the whole package was
# excluding it from shrinking and obfuscation for no reason.
