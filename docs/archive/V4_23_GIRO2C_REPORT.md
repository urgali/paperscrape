# v4.23 — giro 2C: il golden della zucca e il controllo che mente

**Ogni affermazione è marcata OSSERVATO (visto girare o visto in un file), MISURATO (un numero
prodotto qui), DEDOTTO (ricavato da qualcosa di osservato) o DICHIARATO (preso da un documento del
progetto senza riverificarlo in questo giro).**

**v4.23 preparata, NON pubblicata, NON approvata, NON verificata da me.** `versionCode 54`,
`versionName "4.23"` invariati. Nessun tag, nessun push, nessuna release.

---

## 0. Baseline — combacia, condizione D non scattata

| | atteso | trovato | |
|---|---|---|---|
| ZIP | `PaperScrape_v4_23_fase2B.zip` | idem | OSSERVATO |
| SHA-256 | `0e8afed5…97b924` | `0e8afed5d1797a35fa15a19f6d1b5dd362ee67bf8260e48e3bc523f98697b924` | MISURATO |
| byte | 6 224 815 | 6 224 815 | MISURATO |
| voci | 967 | 967 | MISURATO |
| `versionCode` / `versionName` | 54 / "4.23" | 54 / "4.23" | OSSERVATO |
| test JVM | 1332 | **1332**, 0 falliti, 0 errori, 0 saltati | MISURATO |
| test Python | 108 | **108, OK** | MISURATO |
| PNG golden | 27 | 27 | MISURATO |
| `@Ignore` | 0 | 0 (le tre occorrenze del sorgente sono dentro commenti) | MISURATO |

L'albero di lavoro è estratto dallo ZIP di baseline, non ereditato: `work_v4_23g/paperscrape`.

---

## 1. Il fatto di partenza, riverificato

**OSSERVATO.** `PaperRenderer.drawMoonWithPhase` porta già la guardia giusta
(`PaperRenderer.kt`, dentro `drawMoonWithPhase`):

```kotlin
if (sceneCustomization.halloweenEnabled) {
    …drawTinted(canvas, R.drawable.moon_jack_o_lantern, …, HALLOWEEN_MOON_COLOUR)
    canvas.restore()
    return
}

if (!sceneCustomization.moon.realisticPhases) { … }
```

Il ritorno anticipato precede la lettura di `realisticPhases`, e il commento sopra porta la
derivazione. **Il renderer non è stato toccato in questo giro** — `git diff` non lo tocca e il
confronto degli archivi lo conferma (§6).

---

## 2. Compito 1 — il golden della zucca (voce 41, chiusa)

### La scena

`SceneGoldenTest.halloweenMoon`, nome del frame `halloween-moon`:

```kotlin
GoldenScene(
    name = "halloween-moon",
    dayPhase = GoldenScene.night(),
    themeId = "halloween",
    customise = {
        it.copy(
            halloweenEnabled = true,
            moon = it.moon.copy(visible = true, realisticPhases = true),
            clouds = it.clouds.copy(visible = false),
        )
    },
    focus = listOf(GoldenFocus(140, 120, 220, 200, "the carved disc, phases on and overridden")),
)
```

### Perché entrambi i flag sono accesi

**DEDOTTO, e poi MISURATO.** La guardia è *il ritorno anticipato*. Un golden con Halloween acceso e
le fasi spente uscirebbe identico che la guardia stia in piedi o cada, perché senza fasi non c'è
niente da ignorare: non ci sarebbe nulla da provare. Con entrambi accesi, se il ramo delle fasi
trapelasse, la lanterna verrebbe sostituita da una delle quattro sagome nel colore della luna.

**MISURATO** — gli sprite ricampionati alla misura a cui il renderer li disegna in questo frame
(79,2 px: `radius = 360 × 0,055 × 2 = 39,6`, sprite di 240 unità con scala `radius/120`), pixel con
alfa > 8:

| sprite | px a 79,2 |
|---|---|
| `moon_crescent` | **809** |
| `moon_half` | 1 750 |
| `moon_gibbous` | 2 660 |
| `moon_full` | 3 449 |
| `moon_jack_o_lantern` | **2 609** |

Il bilancio di frame intero è `0,002 × 360 × 800 = **576** px. **Anche la sagoma più piccola lo
supera da sola** (809 > 576). Il fotogramma ha un modo di fallire — lo stesso requisito da cui
`SettingsGateScenesTest` deriva i suoi cancelli.

Entrambi i flag sono **scritti nella scena**, non ereditati: `realisticPhases` è già `true` di
default e il tema `halloween` presetta già `halloweenEnabled`, ma una scena è la descrizione dei
suoi ingressi, e un invariante che dipende da due default che restano fermi non è scritto affatto.

### Il tema scelto, col perché

**Tema `halloween`, notte fonda (`GoldenScene.night()`, ora 1).** È l'unico tema che presetta il
flag, quindi il fotogramma è una configurazione che l'utente raggiunge **scegliendo un tema**, non
una coppia di interruttori montata su una scena estranea. Si porta dietro il cielo horror e le
zucche a terra, ed è questo che gli fa portare informazione che nessun altro frame ha: `night` e
`theme-city` sono gli unici altri frame di notte fonda e nessuno dei due è questo cielo, questo
terreno o questa luna.

### Le nuvole sono spente — ed è la voce 40, non una comodità

**MISURATO, e la prima cattura è stata buttata.** Con la fascia di nuvole del tema **il primo
fotogramma non conteneva affatto la zucca**: `CloudBand` mette la fascia a
`800 × (0,06 + (0,6 − 0,42) × 0,5) = 120`, che è il bordo alto del disco, e la fascia è disegnata
**dopo** il corpo celeste. La cattura è uscita con un alone sfocato sopra la linea delle nuvole e
nient'altro. Un golden che non vede lo sprite di cui porta il nome non asserisce niente su quello
sprite, quindi le nuvole sono spente qui. Nient'altro nella scena è stato disposto attorno alla luna.

### Il focus, derivato e poi verificato

**DEDOTTO.** All'ora 1, con alba 6 e tramonto 20: `arcT = wrap24(1 − 20) / 10 = 0,5`, quindi
`celestialX = 0,5` e `celestialY = sin(0,5π) = 1` — la luna all'apice del suo arco, centrata.
Con `CELESTIAL_MARGIN_FRACTION = 0,12` e `sunCloudHeight = 0,42`:

```
cx = 0,12·360 + 0,5·(360 − 2·0,12·360) = 180
cy = 0,62·800 − 0,42·800                = 160
radius = 360 · 0,055 · 2                = 39,6      → quadrato 79,2 px
```

Rettangolo arrotondato verso l'esterno: **(140,120)–(220,200)**, 6 400 px.

**MISURATO sul PNG committato**, non solo calcolato: l'arancione acceso occupa
`x 147..211 × y 128..191`, centro **(179,0 / 159,5)** contro i (180, 160) derivati. Lo scarto
sotto il pixel è la voce 45 — il disegno non centrato nella propria tela.

Il focus porta la `SceneGolden.MAX_FOCUS_DIFFERING_FRACTION` condivisa (2%), **non un cancello
derivato**: questo golden pinna uno sprite, non un'impostazione. **Nessuna tolleranza toccata,
nessun cancello spostato.** Quello che aggiunge sopra la regola di frame intero è la faccia: occhi,
naso e ghigno sono tagliati *attraverso* la carta, quindi sono qualche centinaio di pixel di cielo
che passa, e il bilancio di 576 del frame intero perdonerebbe una faccia che si richiude mentre il
disco resta al suo posto.

### Che cosa contiene il fotogramma

**OSSERVATO — guardato prima di committarlo, non solo asserito.** Cielo di Halloween a notte fonda:
quasi nero in alto che scende su un orizzonte arancione duro. Il disco intagliato all'apice
dell'arco, arancione `#FF8C2A`, col cielo che si vede **attraverso** i due occhi obliqui, il naso
triangolare e il ghigno dentato — **una stella è visibile attraverso uno dei denti**, che è la prova
a occhio che è un taglio e non un dipinto. Attorno, il campo stellare (punti e scintille). Sotto:
colline scure, alberi spogli, case e negozi con le finestre accese, la vetrina rossa del ristorante,
persone sul marciapiede, zucche a terra, e la strada notturna vuota.

Le anteprime sono accanto all'archivio: `anteprime/golden_halloween-moon.png` (il frame come
committato) e `anteprime/golden_halloween-moon_disco.png` (il quadrato del focus a 6×).

### `GoldenUniquenessTest` verde — condizione Q non scattata

**MISURATO.** `no two committed goldens are byte-identical` **PASSED** con 28 PNG. Verificato anche
fuori dal test, con `sha256sum` su tutta la directory: nessun gruppo di duplicati.

SHA-256 del PNG nuovo: `d6ae9cbefc9aca7b637f65caee4bf40e9292497072f294c4b4eacc67dc1bde0b`.

---

## 3. Nessun golden esistente si è mosso — condizione V non scattata

**MISURATO.** I 27 PNG di baseline sono **byte-identici** a quelli consegnati: il diff dei due
archivi (§6) non elenca nessun `app/src/androidTest/assets/golden/*.png` fra i file modificati, e
l'unica voce aggiunta in quella directory è `halloween-moon.png`. Non è stato toccato niente del
percorso di disegno: le uniche modifiche a `src/main` sono in `ui/SettingsUiModel.kt` e
`ui/WorldSceneScreen.kt`.

**MISURATO, seconda prova, indipendente dal diff:** la suite strumentata completa sul BV6600
confronta ogni golden committato contro il frame reso adesso, e chiude **`OK (149 tests)`**,
0 falliti, in 2 560,6 s.

### La suite è stata eseguita due volte, e il primo giro è un'informazione, non un incidente da nascondere

**OSSERVATO.** Il primo giro completo ha dato **148 passati / 1 fallito**, e il fallito era il
golden nuovo:

```
No golden committed for 'halloween-moon'. A frame was written to …/golden-output/halloween-moon.png
```

La causa è mia e vale la pena scriverla: **i PNG attesi sono asset dell'APK strumentato**, non file
che il test legge dall'host. Avevo committato il PNG in `app/src/androidTest/assets/golden/` *dopo*
l'ultima `installDebugAndroidTest`, quindi il confronto girava contro un APK che conteneva 27
golden e non il ventottesimo — verificato aprendo l'APK installato. Ricostruito e reinstallato,
l'APK ne contiene 28 e il test passa.

Due cose ne restano. La prima: il messaggio d'errore sembra dire che la copia non è avvenuta,
mentre sta dicendo che l'APK è vecchio; è ora annotato in `CLAUDE.md` §7. La seconda, ed è quella
che conta qui: **quel primo giro ha confrontato tutti e 27 i golden preesistenti contro i frame resi
sul dispositivo e li ha passati tutti**, quindi la condizione V era già chiusa prima che il secondo
giro la riconfermasse.

---

## 4. Compito 2 — il controllo che mente (voce 47, chiusa)

### Come è bloccato

`WorldSceneScreen.SunMoonSubScreen`, la riga «Realistic Moon Phases»:

```kotlin
val moonPhases = SettingsUiModel.moonPhases(
    storedRealisticPhases = customization.moon.realisticPhases,
    halloweenEnabled = customization.halloweenEnabled,
)
SettingSwitchRow(
    title = "Realistic Moon Phases",
    subtitle = if (moonPhases.overriddenByHalloween) {
        "Halloween's moon is a carved lantern and is always full. Turn Halloween off in " +
            "Seasons & decorations to set this; your choice is kept until then."
    } else {
        "Show real moon phases at night"
    },
    checked = moonPhases.shownOn,
    enabled = moonPhases.interactive,
    onCheckedChange = { scope.launch { prefs.setMoonRealisticPhases(it, forThemeId) } },
)
```

`SettingSwitchRow` aveva già il parametro `enabled` (titolo in `outline`, `Switch(enabled = …)`), e
questo è lo stesso schema che le schermate Nuvole e Precipitazioni usano dalla v3.1 per
`liveWeatherDriving`: mostrato spento, non toccabile, con la riga che dice perché e dove si disfa.
**Nessun componente nuovo, nessun refactor.**

La regola sta in una funzione pura, `SettingsUiModel.moonPhases(storedRealisticPhases,
halloweenEnabled) → MoonPhasesUiState(shownOn, interactive, overriddenByHalloween)`, accanto a
`liveWeather` e `seasonalPalette`, che sono lì per lo stesso motivo: la mappatura fra flag
memorizzati e ciò che la UI mostra è la parte che vale la pena testare, ed è testabile solo se è
libera da Compose.

### Come sopravvive la preferenza — condizione U non scattata

**Non si scrive niente.** `shownOn` è `storedRealisticPhases && !halloweenEnabled`: il valore
memorizzato viene **scavalcato per la visualizzazione** e lasciato dov'è. Nel DataStore resta quello
dell'utente, e torna esattamente quello nel momento in cui Halloween si spegne.

La regola da cui discende è già scritta nel progetto, nel doc di `PeopleDensity.resolveNightDensity`,
a proposito di un'altra impostazione: un default che «silently change what an existing user had set
up» non è «something a settings refactor is entitled to do». Scrivere `false` avrebbe fatto sembrare
giusto l'interruttore gratis, e avrebbe distrutto una scelta dell'utente.

**Condizione U non è scattata perché non è mai stata necessaria:** `SettingSwitchRow` accetta già
`enabled`, quindi bloccare il controllo non ha mai richiesto di toccare la preferenza. La strada
vietata è stata invece **usata come mutazione**, per vedere il test fallire (§4, mutazione m2).

### La verifica cambiando tema, non solo la decorazione

**Per costruzione e poi OSSERVATO sul dispositivo.** Entrambi gli ingressi della funzione — il flag
Halloween e la preferenza delle fasi — sono letti dallo **stesso** `customization` risolto, che è per
tema; nessuno dei due viene da un'impostazione globale. Un test lo pinna esplicitamente (`the
override and the value it overrides are read from the same theme's customization`), e la mutazione
che sostituisce `customization.halloweenEnabled` con un valore non letto dal tema lo fa fallire.

Sul dispositivo: §7.

### La prova che pinna l'invariante, e perché regge

`app/src/test/kotlin/com/paperscrape/livewallpaper/ui/MoonPhaseControlTest.kt`, **8 test JVM**, in
due metà, perché falliscono in posti diversi e **nessuna delle due basta da sola**:

**La regola** è asserita direttamente: tutte e quattro le combinazioni dei flag, più la proprietà
che conta di più — che *solo* Halloween decide se la riga è bloccata. Far dipendere
l'interattività anche dal valore memorizzato produrrebbe un interruttore bloccato **acceso** per chi
aveva le fasi attive: sembra ragionevole ed è il vicolo cieco in cui stava l'interruttore Live
Weather della v3.0 (`LiveWeatherUiState.switchIsInteractive` porta quella storia).

**Il cablaggio** non è raggiungibile così. **In questo albero non c'è un solo `createComposeRule`**
— verificato con un grep su tutto `app/src` — quindi non esiste un modo di toccare un interruttore
e rileggere il DataStore sulla JVM; e un test strumentato che lo facesse mostrerebbe la riga
bloccata **senza mostrare che nessuno scrive**, che è la metà che distrugge dati quando è sbagliata.
Quindi il cablaggio è pinnato **leggendo il sorgente**, come `BusinessHoursWiringTest` pinna i due
punti di chiamata dell'orario commerciale e `SkyscraperWindowTest` l'accoppiamento del colore delle
finestre — e per la stessa ragione in tutti e tre i casi: **la proprietà riguarda quali punti di
chiamata esistono**, e nessun fotogramma reso e nessuna preferenza fatta girare può dire che un
secondo punto di chiamata *non* esiste.

Le quattro asserzioni di sorgente: la riga prende `checked` ed `enabled` dalla derivazione e non dal
flag grezzo; i due ingressi vengono dallo stesso `customization` (è questo che tiene lo
scavalcamento per tema); **`setMoonRealisticPhases` ha esattamente un chiamante in tutto
`src/main`**, il `onCheckedChange` dell'interruttore stesso; e `setHalloweenEnabled` scrive la
propria chiave e il marcatore del tema pendente e nient'altro.

### Ogni test visto fallire prima di essere creduto

**MISURATO.** Sette mutazioni, ognuna presa dal test che esiste per lei **e da nessun altro**
(l'ultima colonna è la lista dei test che passano da PASSED a FAILED):

| # | mutazione | test che fallisce |
|---|---|---|
| m1 | `checked = customization.moon.realisticPhases` (il flag grezzo torna sull'interruttore) | *the row reads its state from the derivation…* |
| m2 | **`scope.launch { prefs.setMoonRealisticPhases(false, forThemeId) }` aggiunto alla schermata quando Halloween si accende** — la riparazione ovvia, quella vietata | *nothing but the switch ever writes the moon-phase preference* |
| m3 | `interactive = !halloweenEnabled \|\| storedRealisticPhases` | *only Halloween decides…* + *with Halloween on…* |
| m4 | `shownOn = storedRealisticPhases` (lo scavalcamento sparisce) | *with Halloween on…* |
| m5 | `setHalloweenEnabled` scrive anche `MOON_REALISTIC_PHASES = false` | *setting Halloween touches no other preference* |
| m6 | `halloweenEnabled = true` al posto di `customization.halloweenEnabled` | *…read from the same theme's customization* |
| m7 | `shownOn = false` | *with Halloween off…* + *turning Halloween off restores…* |

Tutti e 8 i test sono stati visti FAILED sotto almeno una mutazione. L'albero è stato ripristinato
dopo ogni mutazione e i tre file sono **byte-identici** agli originali (verificato con `diff`).

---

## 5. Contabilità

| | baseline | ora | delta |
|---|---|---|---|
| test JVM | 1332 | **1340**, 0 falliti / 0 errori / 0 saltati | **+8** (`MoonPhaseControlTest`) |
| test strumentati | 148 | **149**, `OK (149 tests)`, 0 falliti | **+1** (`SceneGoldenTest.halloweenMoon`) |
| test Python (asset) | 108 | **108, OK** | 0 (nessun asset toccato) |
| `lintDebug` | 0 errori, 26 warning, 3 hint | **0 errori, 26 warning, 3 hint** — stesse categorie | **0** |
| PNG golden | 27 | **28** | **+1** (`halloween-moon.png`) |
| asserzioni Canvas | 25 | **26** | +1 |
| golden GL | 3 | **3** | 0, **non toccati** |
| voci dell'archivio | 967 | vedi §6 | vedi §6 |
| `versionCode` / `versionName` | 54 / "4.23" | 54 / "4.23" | **0** |

---

## 6. L'artefatto e i file toccati

`PaperScrape_v4_23_giro2C.zip`, che sostituisce
`0e8afed5d1797a35fa15a19f6d1b5dd362ee67bf8260e48e3bc523f98697b924` (6 224 815 byte, 967 voci).
**SHA-256 e byte stanno nella nota di consegna accanto all'archivio**, non qui: sono proprietà del
file che contiene questo report e non possono stare dentro di esso.

**Aritmetica delle voci, derivata dal diff dei due archivi** (confronto per SHA-256 di ogni voce,
fatto prima di scrivere questi numeri):

```
967 + 3 − 0 = 970,  più 9 modificate allo stesso percorso
```

**I 3 file aggiunti:**

| file | che cos'è |
|---|---|
| `app/src/androidTest/assets/golden/halloween-moon.png` | il golden nuovo (SHA-256 `d6ae9cbe…1bde0b`) |
| `app/src/test/kotlin/…/ui/MoonPhaseControlTest.kt` | gli 8 test JVM dell'invariante |
| `V4_23_GIRO2C_REPORT.md` | questo report |

**I 9 file modificati:**

| file | che cosa è cambiato |
|---|---|
| `app/src/main/kotlin/…/ui/SettingsUiModel.kt` | `MoonPhasesUiState` + `moonPhases()` — la regola, pura |
| `app/src/main/kotlin/…/ui/WorldSceneScreen.kt` | la riga «Realistic Moon Phases» legge la derivazione, si blocca e spiega |
| `app/src/androidTest/kotlin/…/engine/SceneGoldenTest.kt` | la scena `halloween-moon` e la sua derivazione |
| `BACKLOG_v4_23.md` | voci 41 e 47 chiuse, metà della 40 chiusa, voce 48 aperta |
| `release-notes/v4.23.md` | la riga sul controllo, e la chiusa corretta (non è più vero che ogni differenza è nell'artwork) |
| `RELEASE_HISTORY.md` | il punto 5 e la sezione «Goldens, part two» |
| `ROADMAP.md` | due righe nell'albero v4.23 e la nota sul backlog |
| `ARCHITECTURE.md` | 25 → 26 golden Canvas, e la nota onesta su che cosa la UI ha e non ha di test |
| `CLAUDE.md` | 25/27 → 26/28, e la trappola dell'APK strumentato |

**Nessun file rimosso.** `sprites-examples.zip` **non è nell'archivio** — verificato sull'elenco
delle voci, non per costruzione — e non ci sono `local.properties`, cartelle di build, `__pycache__`,
né i `.claude/` e `.mcp.json` che l'ambiente lascia cadere nell'albero di lavoro. L'archivio è
costruito **dall'elenco delle voci della baseline più i 3 file aggiunti**, non da un `zip -r` della
directory, proprio perché niente di estraneo possa entrarci. `CLAUDE.md`, `.gitignore`,
`.github/workflows/` e `debug.keystore` ci sono, come sempre.

I **28** PNG golden sono nell'archivio (`androidTest/assets/golden/`), di cui i 27 preesistenti
byte-identici alla baseline.

---

## 7. Il dispositivo

Blackview BV6600, Android 10, Helio A25, PowerVR GE8320, 720×1440.

### Stato annotato prima di toccarlo, e ripristinato dopo

| | prima | dopo |
|---|---|---|
| wallpaper vivo | `com.paperscrape.livewallpaper/…PaperWallpaperService` (release del maintainer) | **lo stesso**, e **osservato mentre rende** |
| `global auto_time` | 1 | 1 (mai toccato) |
| `system screen_off_timeout` | 60000 | 60000 (mai toccato) |
| data/ora | corrette via rete | corrette, **mai cambiate** |
| `svc power stayon` | spento | **acceso da me** per le raffiche, **rimesso su `false`** |
| pacchetti | solo `com.paperscrape.livewallpaper` | **solo `com.paperscrape.livewallpaper`** — `.debug` e `.debug.test` disinstallati |

La release del maintainer **non è mai stata disinstallata né toccata nelle sue impostazioni**: è
stata solo riportata a essere il wallpaper vivo, dal suo stesso pulsante «Set as wallpaper».
`anteprime/dispositivo_ripristinato_release_maintainer.png` è la home a ripristino fatto — Autumn di
notte, con la luna liscia della v4.22, perché quella è la versione che il maintainer ha installata.

### La cattura dal wallpaper vivo

`anteprime/wallpaper_vivo_zucca_BV6600.png`. **OSSERVATO.** Build `.debug` impostata come wallpaper
vivo, tema Halloween, ora fissa **21:00** (Weather & time → «Follow real time» spento → «Fixed
time»), che è l'ora in cui la luna è appena salita ed è **sotto** la fascia di nuvole: a 23:00 reali
la fascia la copriva quasi tutta, e nell'anteprima del selettore si vedeva spuntare solo il ghigno —
la voce 40 dal vivo. La faccia si legge a scala di dispositivo: occhi obliqui, naso triangolare,
ghigno dentato, tutti tagliati attraverso la carta.

Nessuna modifica al dispositivo è stata usata per ottenerla oltre a quelle in tabella: l'ora fissa e
il tema sono impostazioni della **mia** build `.debug`, che è stata poi disinstallata.

### Le catture della UI

Quattro, e insieme sono la verifica dello scavalcamento **e** del ripristino:

| file | che cosa mostra |
|---|---|
| `ui_controllo_bloccato.png` | tema Halloween: titolo in grigio, interruttore spento e non toccabile, e la riga che dice perché e dove si disfa. Sopra, «Show Moon» acceso e vivo, per il confronto |
| `ui_controllo_vivo_halloween_spento.png` | stesso tema, decorazione Halloween spenta: la riga torna viva, **accesa**, col sottotitolo normale |
| `ui_controllo_bloccato_con_preferenza_true.png` | **la prova**: Halloween riacceso, interruttore spento e bloccato, mentre nel DataStore `moon_realistic_phases` vale `true` |
| `ui_controllo_vivo_tema_sunset.png` | cambiato **tema** a Sunset: la riga è viva e accesa, con `theme_id = sunset` |

**MISURATO — il DataStore, letto con `run-as` a ogni passo** (`files/datastore/paperscrape_prefs.preferences_pb`):

1. Aperta la schermata col tema Halloween e **toccato l'interruttore disabilitato**: le due catture
   prima e dopo il tocco sono **byte-identiche** (SHA-256 `cedb6f624cafc8bd…` entrambe), e il file
   delle preferenze **non contiene affatto la chiave** `moon_realistic_phases`. Se la schermata
   avesse scritto `false`, la chiave ci sarebbe.
2. Spenta la decorazione Halloween, la riga torna viva e **accesa**.
3. Scritto un valore esplicito dell'utente: spento (`moon_realistic_phases` = `12 02 08 00`, cioè
   **false**) e poi riacceso (`12 02 08 01`, **true**).
4. **Riacceso Halloween**: `halloween_enabled` = `08 01`, e `moon_realistic_phases` **ancora
   `08 01`** — la preferenza dell'utente è intatta mentre l'interruttore mostra spento e bloccato.
5. **Cambiato tema** a Sunset: `theme_id` = `sunset`, la riga è viva e accesa. Lo scavalcamento
   segue il tema, non l'app.

Il passo 4 è la condizione U verificata sul dispositivo e non solo nel test.

---

## 8. Condizioni d'arresto

| | | |
|---|---|---|
| **D** — la baseline non combacia | **non scattata** | §0, tutte le righe verificate |
| **U** — bloccare il controllo richiederebbe di scrivere nella preferenza | **non scattata** | `SettingSwitchRow` ha già `enabled`; la preferenza non è mai stata toccata, e la strada vietata è stata usata solo come mutazione |
| **V** — un golden esistente si muove | **non scattata** | i 27 PNG sono byte-identici; la suite strumentata li riconferma sul dispositivo |
| **Q** — il golden nuovo è byte-identico a uno esistente | **non scattata** | `GoldenUniquenessTest` verde con 28 PNG |
| regola generale del §4 del brief | **non scattata** | nessuna metrica cambiata, nessuna scrittura in preferenza, nessun cancello spostato, nessuna tolleranza toccata |

---

## 9. Che cosa NON è stato fatto

- **Il renderer non è stato toccato.** È già corretto e porta la sua derivazione.
- **Non è stato disegnato niente.** Nessun PNG di sprite, nessun SVG, nessuna tela.
- **Nessun golden esistente rigenerato**, nessun cancello spostato, nessuna tolleranza cambiata.
- **La versione non è stata incrementata.**
- **`sprites-examples.zip` non è nell'archivio.**
- **Niente pubblicazione, niente tag, niente release.** La verifica finale è del maintainer.
