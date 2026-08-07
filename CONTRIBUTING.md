# Contribuire a PaperScrape

Grazie per l'interesse! Il progetto è volutamente piccolo e leggibile: qualunque
sviluppatore Android con basi di Kotlin dovrebbe potersi orientare in pochi minuti.

## Dove mettere le mani

| Voglio... | File da modificare |
|---|---|
| Aggiungere un nuovo tema colore | `engine/SceneTheme.kt` → aggiungi un `SceneTheme` a `ThemeCatalog.ALL` |
| Cambiare la forma delle colline | `engine/PaperRenderer.kt` → `buildHillPath()` |
| Aggiungere/spostare oggetti in un tema (auto, cani, case, alberi, oggetti stagionali) | `engine/SceneObject.kt` → `SceneObjectCatalog.layoutFor()` |
| Aggiungere un nuovo tipo di oggetto animato | `engine/SceneObject.kt` (nuovo `SceneObjectType` + spec) + `engine/SceneObjectRenderer.kt` (disegno + eventuale hit-test) |
| Cambiare il suono di reazione al tocco | `engine/ReactionSoundPlayer.kt` |
| Modificare l'algoritmo del tema/oggetti casuali ("Randomize") | `engine/RandomSceneGenerator.kt` |
| Aggiungere un nuovo effetto al tocco sullo sfondo libero | `engine/PaperBird.kt` (o crea una nuova classe "particle" sullo stesso modello) |
| Aggiungere un effetto automatico legato al tema (tipo fuochi d'artificio o slitta di Babbo Natale) | `engine/FireworkEffect.kt` o `engine/SantaSleighEffect.kt` come modello + attiva/disattiva da `SceneTheme` e `PaperRenderer.draw()` |
| Cambiare la logica alba/tramonto | `engine/SunPositionCalculator.kt` |
| Aggiungere un'opzione nelle impostazioni | `prefs/WallpaperPrefs.kt` (nuovo campo) + `ui/SettingsScreen.kt` (nuovo controllo) |
| Cambiare frequenza di refresh / battery usage | `engine/PaperWallpaperService.kt` → costante `FRAME_INTERVAL_MS` |

## Idee per contributi futuri

Ordine di priorità allineato al README (vedi anche la nota di pianificazione
lì su come il punto "editor temi" dovrà collegarsi al punto "tema automatico
per data"):

- [ ] **Cambio tema automatico per data/periodo** (Natale, Pasqua, estate, ...)
- [ ] Meteo live (richiede una API key esterna, es. Open-Meteo che è gratuita e senza chiave)
- [ ] Editor temi personalizzato (color picker) con salvataggio multiplo — deve poter essere referenziato dall'automatismo data-based sopra
- [ ] Screenshot/condivisione della scena corrente
- [ ] Widget home screen per cambiare tema rapidamente
- [ ] Supporto neve/pioggia come layer di particelle aggiuntivo
- [ ] Localizzazione stringhe in inglese (attualmente solo italiano)

## Convenzioni di codice

- Kotlin idiomatico, niente `!!` se evitabile.
- Ogni nuovo tema deve fornire *tutti* i campi di `SceneTheme` (giorno/notte per cielo e colline).
- Le preferenze passano sempre da `WallpaperPrefs` (DataStore), mai `SharedPreferences` dirette.
- Mantieni il render loop a Canvas 2D puro: niente dipendenze OpenGL/Vulkan, per restare
  leggero e compatibile con tutti i dispositivi Android 8+.

## Test rapido senza device fisico

Usa l'emulatore Android Studio (API 34+ consigliata) e imposta il wallpaper dalle
Impostazioni dell'emulatore → Sfondo, oppure lancia direttamente `SettingsActivity`
e premi "Imposta come sfondo".

## Pull request

1. Fork + branch descrittivo (`feature/tema-neve`, `fix/parallax-jump`, ...)
2. Verifica che `./gradlew lint assembleDebug` passi
3. Apri la PR spiegando *cosa* cambia e *perché*
