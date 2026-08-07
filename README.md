# PaperScrape 🗻📄

Live wallpaper Android open source con un paesaggio "in carta ritagliata" a più
strati con effetto parallasse, sole e luna che seguono l'ora del giorno (reale o
impostata a mano), temi colore intercambiabili ed effetti al tocco.

Progetto scritto **da zero**, ispirato al concept dei classici live wallpaper
"paper cutout" con paesaggio animato, ma con codice, nome e asset completamente
originali — vedi la nota legale in fondo al file.

Target: **Android 16 (API 36)**, `minSdk 26` (Android 8.0+), Kotlin + Jetpack
Compose per le impostazioni, `Canvas` 2D puro per il rendering (nessuna
dipendenza OpenGL: leggero e compatibile con qualunque device).

> 📌 Ogni release ha una voce in [CHANGELOG.md](CHANGELOG.md) — utile per
> capire cosa contiene ogni commit/tag (`v1`, `v2`, `v3`, ...).

---

## ✨ Funzionalità

- Sfondo animato a **3 strati di colline di carta** con parallasse indipendente,
  che si muove seguendo lo scroll della home screen.
- **Ciclo giorno/notte**: il sole (o la luna, di notte) si muove lungo un arco nel
  cielo, e i colori di cielo/colline sfumano gradualmente tra alba, giorno,
  tramonto e notte.
- **Oggetti animati nella scena**, diversi per ogni tema: case con finestre che
  si illuminano di notte, alberi che ondeggiano, cani che scodinzolano in loop,
  auto che attraversano lo schermo su una propria corsia, e — nei temi
  stagionali — pupazzi di neve, regali, palme, ombrelloni, grattacieli,
  pinguini e palloncini fluttuanti.
- **Interazione al tocco su cani/pinguini/regali/auto**: toccarli fa partire
  un'animazione di reazione (balzo) più un suono breve differenziato
  (bark/squawk/honk/chime, generati al volo — vedi nota sui suoni più sotto).
  Toccare lo sfondo libero fa invece volare un uccellino di carta.
- **Fuochi d'artificio automatici** di notte nel tema Capodanno.
- **Babbo Natale in slitta** (tema Natale): ogni tanto (a intervalli casuali)
  attraversa il cielo trainato da due renne, lanciando regali che cadono verso
  terra.
- Sincronizzazione opzionale con **posizione reale** per calcolare alba/tramonto
  precisi (permesso di localizzazione richiesto solo se attivata).
- **9 temi/scene inclusi** — Tramonto, Autunno, Inverno, Deserto, **Natale**,
  **Capodanno** (con fuochi d'artificio a mezzanotte), **Spiaggia**, **Grande
  città** (grattacieli e traffico a 3 corsie) e **Tundra** — ciascuno con la
  propria combinazione di colori *e* oggetti dedicati (non solo palette
  diverse: Natale ha pupazzi di neve e regali, Spiaggia ha palme e ombrelloni,
  Grande città ha grattacieli con finestre che si accendono di notte, ecc.).
  Aggiungerne uno nuovo richiede poche righe in due file.
- Schermata impostazioni in Jetpack Compose con anteprima live dei temi.
- Tutte le preferenze persistite con Jetpack **DataStore**.

### 🔊 Nota sui suoni

I suoni di reazione (bark/squawk/honk/chime) sono generati con
`android.media.ToneGenerator`, integrato in Android — nessun file audio
esterno necessario, quindi il progetto resta compilabile subito senza dover
reperire/licenziare asset audio. Sono bip brevi e riconoscibili, non
registrazioni realistiche. La cartella `app/src/main/res/raw/` è già pronta
per quando vorrai sostituirli con suoni veri: basta aggiungere i file lì e
aggiornare `ReactionSoundPlayer.kt` (istruzioni nel TODO del file stesso).

## 📚 Wiki

### Temi disponibili

| Tema | Elementi caratteristici |
|---|---|
| **Tramonto** | Casa, cani, auto su 2 corsie |
| **Autunno** | Casa, alberi, cane, auto |
| **Inverno** | Casa, alberi, pupazzo di neve, auto |
| **Deserto** | Alberi, cane, auto |
| **Natale** | Casa, pupazzi di neve, regali, albero, **Babbo Natale in slitta trainata da renne** che sorvola il cielo a intervalli casuali lanciando regali |
| **Capodanno** | Grattacieli, palloncini, **fuochi d'artificio automatici a mezzanotte** (effetto particellare) |
| **Spiaggia** | Palme che ondeggiano, ombrelloni a spicchi colorati |
| **Grande città** | Grattacieli con finestre che si illuminano casualmente di notte, traffico su 3 corsie |
| **Tundra** | Pupazzo di neve, pinguini (tappabili, con suono proprio) |
| **🎲 Casuale** | Combinazione generata al volo: colori armonici + 3-6 oggetti scelti a caso dal pool completo |

### Oggetti e interazioni

| Oggetto | Tappabile | Comportamento |
|---|---|---|
| Cane | ✅ | Scodinzola in loop; al tocco balza ed emette un bark |
| Pinguino | ✅ | Ondeggia camminando; al tocco balza con un verso acuto |
| Regalo | ✅ | Al tocco balza con un chime |
| Auto | ✅ (clacson) | Attraversa lo schermo in loop sulla propria corsia, indipendente dal parallasse delle colline |
| Casa | ❌ | Finestre che si illuminano gradualmente di notte |
| Albero / Palma | ❌ | Ondeggia leggermente |
| Pupazzo di neve | ❌ | Leggero dondolio |
| Grattacielo | ❌ | Finestre che si accendono/spengono casualmente di notte |
| Ombrellone | ❌ | Leggero bob verticale |
| Palloncino | ❌ | Fluttua su e giù |
| Sfondo libero | — | Al tocco fa volare un uccellino di carta |

### Impostazioni

| Impostazione | Cosa fa |
|---|---|
| Tema | Sceglie tra i 9 temi fissi (vedi tabella sopra) |
| 🎲 Genera tema casuale | Crea una nuova combinazione colori/oggetti; il seed è salvato, quindi sopravvive al riavvio finché non generi un altro |
| Segui l'ora reale | Sole/luna seguono l'orologio del dispositivo invece di un orario fisso |
| Usa la posizione per alba/tramonto | Calcola l'orario preciso di alba/tramonto in base a lat/lon (richiede permesso di localizzazione) |
| Effetti al tocco | Attiva/disattiva le reazioni di oggetti e uccellino di carta |
| Intensità parallasse | Da 0.5x a 2x, quanto le colline si spostano scorrendo la home |

## 📁 Struttura del progetto

```
PaperScrape/
├── app/src/main/kotlin/com/paperscrape/livewallpaper/
│   ├── engine/
│   │   ├── PaperWallpaperService.kt   # WallpaperService + Engine: loop di rendering, touch, posizione
│   │   ├── PaperRenderer.kt           # Disegna cielo, stelle, sole/luna, strati di colline
│   │   ├── SceneTheme.kt              # Modello dati tema + catalogo temi built-in
│   │   ├── SceneObject.kt             # Modello dati oggetti di scena (auto/cani/case/alberi) per tema
│   │   ├── RandomSceneGenerator.kt    # Generatore procedurale per la funzione "Randomize"
│   │   ├── SceneObjectRenderer.kt     # Disegna e anima gli oggetti di scena, gestisce l'hit-test al tocco
│   │   ├── ReactionSoundPlayer.kt     # Suoni di reazione al tocco (bark/squawk/honk via ToneGenerator)
│   │   ├── FireworkEffect.kt          # Fuochi d'artificio automatici (tema Capodanno, di notte)
│   │   ├── SantaSleighEffect.kt       # Babbo Natale in slitta (tema Natale, a intervalli casuali)
│   │   ├── SunPositionCalculator.kt   # Calcolo posizione sole/luna e alba/tramonto
│   │   └── PaperBird.kt               # Particella "uccellino di carta" per il tocco sullo sfondo libero
│   ├── prefs/
│   │   └── WallpaperPrefs.kt          # Preferenze utente (DataStore)
│   └── ui/
│       ├── SettingsActivity.kt        # Activity che ospita la schermata Compose
│       ├── SettingsScreen.kt          # UI impostazioni (temi, switch, slider)
│       └── theme/PaperScrapeTheme.kt   # Tema Material3 dell'app
├── app/src/main/res/
│   ├── xml/wallpaper.xml              # Metadata del live wallpaper
│   ├── drawable/                      # Icone vettoriali + thumbnail wallpaper
│   └── values/                        # Stringhe, colori, temi
├── .github/workflows/android-build.yml # CI: build automatica APK debug ad ogni push
├── CONTRIBUTING.md                    # Guida rapida per estendere il progetto
└── LICENSE                            # MIT
```

## 🛠️ Come compilarlo

### Opzione A — Android Studio (consigliata)

1. Installa [Android Studio](https://developer.android.com/studio) (Ladybug o più recente).
2. `File → Open` e seleziona la cartella `PaperScrape/`.
3. Android Studio genera automaticamente il Gradle Wrapper (`gradlew`) al primo
   sync — non serve fare nulla di manuale.
4. Assicurati di avere installato **Android SDK Platform 36** dal SDK Manager
   (`Tools → SDK Manager`). Se non è ancora disponibile sul tuo Studio, imposta
   temporaneamente `compileSdk`/`targetSdk` a 35 in `app/build.gradle.kts`.
5. Premi ▶️ Run per installare su un device/emulatore, oppure `Build → Build
   Bundle(s)/APK(s) → Build APK(s)`.

### Opzione B — riga di comando

```bash
# Se non hai ancora un gradlew nel repo (non è incluso il jar binario del wrapper):
gradle wrapper --gradle-version 8.9

./gradlew assembleDebug
# APK generato in: app/build/outputs/apk/debug/app-debug.apk
```

> Nota: il repository non include il binario `gradle-wrapper.jar` (file binario,
> non adatto a un semplice file di testo). Aprendo il progetto in Android Studio
> viene rigenerato automaticamente; da riga di comando basta il comando `gradle
> wrapper` sopra, una tantum.

### Come impostarlo come sfondo

Dopo l'installazione, apri l'app **PaperScrape** dal launcher → scegli un tema →
"Imposta come sfondo". In alternativa: `Impostazioni di sistema → Sfondo → Sfondi
animati → PaperScrape`.

## 🧠 Architettura in breve

- `PaperWallpaperService.Engine` è il cuore: gestisce il ciclo di vita del
  wallpaper, un loop a **~30 fps** via `Handler.postDelayed`, gli eventi touch e
  l'offset di scroll della home screen (`onOffsetsChanged`).
- `PaperRenderer` è **stateless tra un frame e l'altro** (tranne il campo
  stelle, generato una volta per dimensione schermo) e disegna tutto con
  `Canvas`/`Path`/`LinearGradient`/`RadialGradient` — nessuna libreria grafica
  esterna.
- `SunPositionCalculator` calcola una fase del giorno normalizzata (0–1) e la
  posizione x/y di sole o luna lungo un arco; se l'utente attiva la
  localizzazione, usa una formula NOAA semplificata per alba/tramonto reali.
- Le preferenze (`WallpaperPrefs`) sono un `Flow` osservato sia dalla UI Compose
  sia dal motore di rendering: cambiare tema nelle impostazioni si riflette
  live sullo sfondo, senza riavviare nulla.

## 🎯 Roadmap verso un'esperienza completa

Obiettivo del progetto: costruire un live wallpaper "paper cutout" completo e
ricco di funzionalità, con codice/asset interamente originali (vedi nota legale
sotto).

### Fatto ✅

- Paesaggio a strati di carta con parallasse
- Ciclo giorno/notte automatico + alba/tramonto da posizione reale
- Temi colore multipli
- Oggetti animati e interattivi (auto, cani, case, alberi) con reazione al
  tocco e suono
- Temi stagionali/festivi come scene distinte (Natale, Capodanno, Spiaggia,
  Grande città, Tundra, oltre a Tramonto/Autunno/Inverno/Deserto) con oggetti
  dedicati, non solo palette colore diversa
- Funzione Randomize: genera al volo combinazioni infinite di colori
  (armonici, non casuali a caso) e oggetti — non solo una scelta tra i 9 temi
  fissi
- Evento speciale a tema: Babbo Natale in slitta che sorvola il cielo a
  intervalli casuali lanciando regali (tema Natale)

### Cosa manca per una parità 1:1 con l'app originale

| # | Funzionalità mancante | Note |
|---|---|---|
| 1 | **Cambio tema automatico per data/periodo** | Es. tema Natale nella settimana di Natale, tema Pasqua nella settimana di Pasqua, tema estivo (Spiaggia) nei mesi estivi, ecc. — regola/priorità configurabili |
| 2 | **Meteo live** che influenza la scena | Serve un'API meteo (es. Open-Meteo, gratuita e senza chiave) |
| 3 | **Editor temi personalizzato** con color picker | Salvataggio di più temi custom, non solo l'ultimo generato da Randomize — vedi nota di pianificazione sotto |
| 4 | **Screenshot/condivisione** della scena corrente | Cattura il canvas del wallpaper e lo esporta come immagine |
| 5 | **Widget home screen** per cambio rapido di tema | Richiede un `AppWidgetProvider` + layout dedicato |

**Nota di pianificazione — punto 3 × punto 1:** quando arriverà l'editor
temi personalizzato, l'automatismo per data/periodo (punto 1) dovrà poter
scegliere **anche** tra i temi custom salvati dall'utente, non solo tra i 9
temi built-in. In pratica il sistema di regole "data → tema" andrà pensato
per referenziare un `themeId` qualsiasi (built-in, generato da Randomize, o
custom salvato), non una lista fissa — così l'utente potrà, ad esempio,
associare un proprio tema natalizio fatto a mano invece di quello di
default. Il punto 1 verrà quindi implementato con questo vincolo già in
mente, anche se il punto 3 non esiste ancora.

Nessun altro blocco strutturale noto oltre a questi 5 punti: il motore di
rendering, il sistema temi/oggetti e le impostazioni sono già estensibili
abbastanza da assorbirli senza riscritture. Se vuoi seguire i lavori in corso
o proporre un ordine diverso, apri una issue o guarda `CONTRIBUTING.md`.

## ⚖️ Nota legale

Questo progetto **non è un fork né una decompilazione di alcun prodotto di
terzi**: è un'implementazione originale, scritta da zero, che si limita a
condividere un concept generale diffuso tra i live wallpaper "paper cutout"
(paesaggio di carta animato con ciclo giorno/notte). Nome, package, icone e
codice sono originali e distribuiti sotto licenza MIT.

## 📄 Licenza

MIT — vedi [LICENSE](LICENSE). Fanne quello che vuoi, incluso rinominarlo,
modificarlo e pubblicarlo con il tuo nome.
