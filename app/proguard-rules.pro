# Add project specific ProGuard rules here.

# Keep the wallpaper service and its engine reachable via reflection by the system.
-keep class com.paperscrape.livewallpaper.engine.PaperWallpaperService { *; }
-keep class com.paperscrape.livewallpaper.engine.PaperWallpaperService$PaperEngine { *; }

# Keep preference data classes used by DataStore serialization.
-keep class com.paperscrape.livewallpaper.prefs.** { *; }
