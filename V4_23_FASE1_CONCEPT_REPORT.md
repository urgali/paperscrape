# PaperScrape v4.23 — Fase 1: concept di sole, luna e stelle

**Consegna: CONCEPT IN ATTESA DI GIUDIZIO, NON UNA RELEASE.** Questo pass si ferma, di proposito,
alle fotografie: tre famiglie celesti costruite attraverso la pipeline vera, installate e
fotografate dal wallpaper vivo sul BV6600. **La scelta è del maintainer e si prende guardando le
catture; la fase 2 (derivazione dei criteri, fasi mancanti, golden, esecuzione) parte solo dopo.**
Nessun golden rigenerato, nessun cancello toccato, nessuno sprite spedito modificato: i sei PNG
celesti in `app/src/main/res/drawable-nodpi/` sono byte-identici a quelli dello ZIP di partenza.

`versionCode 54`, `versionName "4.23"` (il bump richiesto dal §0 del mandato; è l'unica modifica al
codice del progetto in questo pass).

Etichette: **OSSERVATO / MISURATO / DEDOTTO / DICHIARATO**, **NON ATTRIBUIBILE** dove serve.

---

## 0. Baseline — verificata, condizione D non scattata

MISURATO in questa sessione, prima di ogni altra cosa:

| voce | atteso | osservato |
|---|---|---|
| ZIP di partenza SHA-256 | `67390b26…b538f` | identico (`sha256sum`) |
| byte / voci | 5 770 788 / 884 | 5 770 788 / 884 |
| `versionCode` / `versionName` in partenza | 53 / "4.22" | 53 / "4.22" |
| JVM | 1331 | **1331 eseguiti, 0 falliti, 0 errori** (dai XML) |
| strumentati | 148 | **148 eseguiti, 0 falliti** (`am instrument`, 2451 s; `OK (148 tests)`) |
| Python | 108 | **108, OK** (55,6 s) |
| PNG golden | 27 (24 Canvas + 3 GL) | 27 file, 3 `gl-*` |
| `@Ignore` | 0 | 0 (le 3 occorrenze grep sono tutte in commenti) |

Albero di lavoro: `/home/bober/claude-shit/work_v4_23/paperscrape`, estratto dallo ZIP.

---

## 1. I due prerequisiti

### 1a. Il gate `PIXEL_IDENTICAL` — PASSATO, con una precisazione che va letta

Eseguito per prima cosa, sugli SVG **esistenti**, prima di toccare qualunque sorgente:

- **`probe`: MISURATO, conforme.** `probe_sha256 ec77e95d… = probe_expected_sha256`,
  `matches_expected: True`. Il rasterizzatore è esattamente quello pinnato.
- **`render` + `compare` (il gate del progetto): MISURATO, `PIXEL_IDENTICAL: 134` su 134**,
  identico alla classificazione registrata in `tools/assets/reports/fidelity.json` (134
  `PIXEL_IDENTICAL`). Verifica indipendente con numpy: **0 pixel differenti su tutti i 134
  sprite** (RGBA, canale per canale).
- **A livello di *byte*, però, 79 file su 134 differiscono** dai PNG committati. La precisazione:
  la differenza è **solo di codifica PNG**, non di pixel. Su questa macchina Pillow comprime con
  `zlib-ng` (il probe lo stampa: `zlib: 1.3.1.zlib-ng`); i PNG più vecchi furono codificati
  altrove. La controprova è nei file: gli sprite autorati su questa macchina nella v4.21
  (`tree_canopy.png`, `tree_trunk.png`, ecc.) sono **byte-identici**, i 79 che differiscono sono
  tutti anteriori. DEDOTTO con controprova, non congettura.

**Perché non è la condizione A.** La condizione A esiste per un bersaglio *che si muove*: una
pipeline derivata farebbe autorare gli sprite nuovi contro pixel diversi da quelli spediti. Qui i
pixel sono identici al set spedito su tutti i 134, il probe combacia, e la classificazione del
gate del progetto è esattamente quella registrata. Il bersaglio è fermo; a muoversi è solo
l'involucro zlib dei byte, che nessun test e nessun renderer legge. **Nessun PNG committato è
stato aggiornato.** Se il coordinatore vuole byte-identità piena anche sull'involucro, è una
decisione sua (ricodificare 79 PNG committati), non di questo pass.

### 1b. `V4_22_MISURA_CPU_REPORT.md` è ora dentro l'albero

Copiato da `consegna_misura_cpu/` alla radice del progetto, accanto agli altri report della 4.22,
**prima di ogni lavoro sugli SVG**. SHA-256 della copia identico all'originale
(`82ab45fc…a186cba`). Da questo ZIP in poi il protocollo CPU viaggia col progetto.

---

## 2. Quercia contro palma: che cosa distingue le due generazioni (OSSERVATO)

Letto nei sorgenti (`tree_canopy.svg`/`tree_trunk.svg` contro `palmtree_*.svg`), prima di
disegnare:

1. **Il bordo.** La chioma della Quercia è un orlo smerlato costruito da ~38 cerchi sovrapposti;
   la palma è fatta di poligoni a lati dritti (5 lame triangolari) e un fuso quasi rettilineo.
   La prima è una sagoma *tagliata*, la seconda è una sagoma *costruita col righello*.
2. **La luce.** La Quercia ha una direzione: quattro fasce dal `#dcdcdc` al bianco, il colmo
   decentrato in alto a **sinistra**, ciuffi d'ombra nel fianco basso; il tronco porta la banda
   d'ombra sul fianco **destro**. La palma non ha direzione: due toni piatti per parte,
   simmetrici.
3. **I contorni.** Le chiome non ne hanno, per regola; le fronde della palma portano ancora un
   gruppo outline (`stroke 0.6` verde scuro) — il linguaggio vecchio contornava.
4. **Il dettaglio alla dimensione.** Gli anelli del tronco palma sono rettangoli piatti di 2,4
   unità che a schermo spariscono; la Quercia affida il dettaglio a scarti di tono e a sagome che
   reggono la distanza.
5. **La disciplina della tela.** La Quercia è rifilata al contenuto con la compensazione del blit
   scritta nel commento del sorgente; la palma ha viewBox con offset ereditati e nessun commento
   d'autore.

**Gli sprite celesti attuali stanno *sotto* la palma, non accanto.** Il sole è due cerchi
concentrici col compasso, senza direzione di luce; i crateri della luna piena sono `#fbfbfb` su
`#ffffff` — **4 livelli su 255, invisibili**; la scintilla è un poligono singolo; e i raggi del
sole — l'unica cosa che oggi dà fastidio al maintainer — sono **otto triangoli acuminati
staccati dal disco**, l'equivalente celeste dell'«ottagono su tronco dritto» che la v4.21 ha
mandato in pensione a terra. È esecuzione della prima ondata, non un linguaggio diverso.

---

## 3. I tre concept

Tutti e tre dentro il paper cutout — nessun linguaggio nuovo, nessuna etichetta nuova — e tutti e
tre costruiti per stare accanto alla Quercia larga: condividono la sua direzione di luce
(alto-sinistra) o ne spiegano l'assenza, i suoi rapporti tonali nelle maschere (pavimento
`#DCDCDC`, 13,7 % di mottling), e la sua regola «niente contorni».

Ogni famiglia: `sun_body`, `sun_glow`, `moon_full`, `moon_half`, `moon_crescent`, `star_sparkle`
(sei sprite; `moon_gibbous` e `moon_jack_o_lantern` restano della fase 2 del concept scelto).
Sorgenti, PNG resi, registro e descrizione in `tools/assets/concepts/{a,b,c}/`.

**Vincoli comuni, mantenuti da tutti e tre** (MISURATO su ogni PNG):
- tele identiche alle spedite (240/240, 396, 180) → `SkySpriteAnchoringTest` verde senza toccare
  costanti; footprint decodificato identico per costruzione;
- lune: maschere a grigi neutri puri, media pixel opachi ≥ 220 (A 234,6–236,6; B 242,9–243,4;
  C 255,0) → `SpriteTintClassTest` verde;
- sole/alone/scintilla: fixed-art con colore reale → verde anche nell'altra direzione;
- convenzioni di scala e ancore invariate → nessun sito di chiamata cambia.

### Concept A — «Strati» (descrizione completa: `tools/assets/concepts/a/DESCRIZIONE.md`)

La ricetta della Quercia in cielo: pile di 2–4 carte incollate fuori centro verso alto-sinistra;
la carta di sotto che spunta in basso a destra *è* l'ombra. Niente contorni, niente ombra portata,
**niente raggi** — la nota del maintainer presa alla lettera: resta il solo alone radiale
sanzionato. Tavolozza: 4 toni caldi (sole), 4 grigi (lune), crateri finalmente a 20–27 livelli dal
fondo. Le fasi sono fasce che condividono punte e lembo e variano solo l'arco del terminatore.

### Concept B — «Forbici» (`tools/assets/concepts/b/DESCRIZIONE.md`)

La carta tagliata *a mano*: ogni sagoma è un poligono di 20–28 vertici con tremolio deterministico
di ±2–2,6 unità scritto nei punti del sorgente. Il sole galleggia sul cielo con la propria ombra
portata (+6,+8, nero 13 % — luce coerente con la banda d'ombra del tronco Quercia); le lune e la
scintilla **non** la portano, per scelta scritta nel sorgente: di notte non c'è niente da scurire.
I raggi **domati**: dodici petali a punta arrotondata nell'anello 150–198 documentato al sito di
chiamata, al posto degli otto triangoli. Il raggio nominale del disco è 99 invece di 102 (l'ombra
deve stare nella tela): −3 % di diametro, dichiarato.

### Concept C — «Traforo» (`tools/assets/concepts/c/DESCRIZIONE.md`)

La mossa della zucca intagliata generalizzata: il disegno sta in ciò che manca. Crateri **forati**
(il cielo passa attraverso — l'unico concept in cui i crateri restano leggibili sotto *qualunque*
tinta utente); luna piena a **un tono**, mottling zero; sole in un pezzo unico con dodici punte
triangolari fuse nella silhouette (raggi come sagoma, non come ornamento — la terza risposta alla
nota); alone **senza gradiente**: una perforazione ad anello di 24 trattini, la linea che le
forbici stanno per seguire. `fill-rule evenodd` come `moon_jack_o_lantern`, che questo concept
adotterebbe come fratello naturale.

### Come sono stati costruiti (tutti e tre)

SVG sorgente → `paperscrape-assets render` (il comando ufficiale, col rasterizzatore pinnato e
probe verificato) → PNG di staging → copiati in `res/drawable-nodpi` di un albero di lavoro →
`assembleDebug` → installati e fotografati. I PNG negli APK sono verificati byte-per-byte contro
lo staging (18/18). I render "ufficiali" sono byte-identici a quelli del ciclo di authoring
(stessa funzione `raster.render_svg_file`). Dopo ogni build, `res/drawable-nodpi` è stato
ripristinato al set spedito e riverificato byte-per-byte.

Registro: ogni sprite concept ha la sua voce completa (dimensione, contentBox misurato dal PNG,
ancora, scala, classe di tinta) in `tools/assets/concepts/{a,b,c}/sprites.concept.json`, **stesso
schema** di `sources/sprites.json`; le voci vivono lì e non nel registro principale perché quello
descrive il set spedito e `validate` deve restare verde sull'albero consegnato.

Validazione JVM per concept: con i sei PNG di ciascun concept al posto dei spediti,
`SpriteTintClassTest`, `SpriteGeometryTest`, `SkySpriteAnchoringTest`,
`SpriteCanvasConventionTest`, `ThemePreviewSceneTest` e `BackgroundScrollGeometryTest` passano
(verificato: PASS su tutti e tre). Con il set spedito ripristinato, la suite piena resta 1331/1331.

---

## 4. Le catture (§5 del mandato)

Prese **dal wallpaper vivo sul BV6600**, non da montaggi: ogni PNG è uno `screencap` a schermo
intero del launcher con la build concept installata come wallpaper `.debug`. In
`consegna_v4_23_fase1/catture/`.

**Protocollo comune.** Ora fissa (Weather & time → Follow real time OFF → slider): **12:00** per il
giorno, **1:00** per la notte. Temi: **Autumn** (il tema del maintainer) e **Winter**, stessi valori
fra i concept. Giorno con nuvole accese (default del tema). Notte: le catture «fasi reali» mostrano
la fase lunare reale della data (2026-09-05, che l'algoritmo del progetto rende come **mezza luna
calante**, `illuminated 0,357`); le catture «luna piena» hanno le fasi realistiche spente.

**Nota sulle nuvole, dichiarata.** La deriva delle nuvole è continua e non fermabile, e la banda di
nuvole passa esattamente all'altezza del disco lunare/solare a certe ore. Per far leggere la fase, le
catture notturne di luna (mezzaluna e luna piena) sono prese con le **nuvole spente**; le catture di
giorno hanno le nuvole accese e il sole è colto in un varco (a volte parziale — è la scena reale, non
un montaggio). Il riferimento v4.22 è preso con lo stesso protocollo per ciascuna condizione.

**Confronto cielo+alberi nello stesso fotogramma (§5).** Le catture `*_autumn_giorno_1200.png` sono a
schermo intero: contengono in un solo fotogramma il **cielo nuovo in alto e gli alberi della v4.21 in
basso** (la Quercia larga, in versione autunnale). È il confronto che dice se il linguaggio è
coerente, e si guarda lì.

**Elenco (21 file):**

| condizione | riferimento v4.22 | Concept A | Concept B | Concept C |
|---|---|---|---|---|
| giorno Autumn 12:00 (cielo+alberi) | `rif_v4_22_autumn_giorno_1200` | `a_autumn_giorno_1200` | `b_autumn_giorno_1200` | `c_autumn_giorno_1200` |
| notte Autumn 1:00 fase reale (mezzaluna) | `rif_v4_22_autumn_notte_0100_fasi_reali` | `a_autumn_notte_0100_fasi_reali` | `b_autumn_notte_0100_fasi_reali` | `c_autumn_notte_0100_fasi_reali` |
| notte Autumn 1:00 luna piena | `rif_v4_22_autumn_notte_0100_luna_piena` | `a_autumn_notte_0100_luna_piena` | `b_autumn_notte_0100_luna_piena` | `c_autumn_notte_0100_luna_piena` |
| notte Winter 1:00 luna | `rif_v4_22_winter_notte_0100_fasi_reali` | `a_winter_notte_0100_luna_piena` | `b_winter_notte_0100_luna_piena` | `c_winter_notte_0100_luna_piena` |
| giorno Winter 12:00 | `rif_v4_22_winter_giorno_1200` | `a_winter_giorno_1200` | `b_winter_giorno_1200` | `c_winter_giorno_1200` |

I fogli famiglia resi dalla pipeline sono in `anteprime/` come **supplemento** (dettaglio a
dimensione reale e 3×); il giudizio si prende sulle catture di scena.

---

## 5. Tutto ciò che dipende dagli sprite celesti (cercato, non ricordato)

Cercato con grep su `app/src` e `tools/`, poi letto sito per sito. **In questa fase nessun golden
è stato rigenerato e nessuno di questi puntelli è stato toccato**; questo elenco è il prodotto che
la fase 2 riprende.

**Golden e fuochi (strumentati):**
- `SharedGoldenScenes.SUN_GLOW = GoldenFocus(53, 33, 307, 287)` — asserito da `SceneGoldenTest.day`
  e `PeopleGoldenTest.people-at-full-density`, ed esercitato da **tutti e tre i golden GL**
  (`gl-day`, `gl-lake-busy`, `gl-thunderstorm`, tutti `dayBlend = 1` su tema sunset). Qualunque
  concept muove pixel dentro quel rettangolo → i tre GL e i due fuochi Canvas si muovono.
- Golden Canvas notturni con luna e/o stelle: `night`, `theme-city`, `traffic-night`,
  `traffic-night-quiet`, `shops-closed-night` (e ogni frame con `dayBlend < 0,625`, dove
  `drawStars` è attivo).
- `GoldenUniquenessTest`: se due golden rigenerati diventassero byte-identici, fallirebbe.

**Codice (main):**
- `PaperRenderer`: `CELESTIAL_DISC_ORIGIN_UNITS`/`_SCALE` (−120, CANVAS_PIXELS),
  `SUN_GLOW_ORIGIN_UNITS`/`_SCALE` (−198, «ray ring at 150..198» nel commento del sito di
  chiamata), `STAR_SPRITE_ORIGIN_UNITS`/`_SCALE` (SCENE_UNITS, 180 px = 60 unità),
  `CELESTIAL_RADIUS_FRACTION 0,055` (disco ≈ 158 px su questo schermo),
  `MAX_STAR_RADIUS_PX = 5,6` e `STAR_SPRITE_LEFT/RIGHT_EXTENT_PX` (estensione delle tile del
  campo stellare), `STAR_SPARKLE_EVERY` (1 su 5 è scintilla), il crema dei punti-stella
  (`starPointPaint` — un concept che cambiasse il crema della scintilla dovrebbe muovere anche la
  costante Kotlin dei punti), `drawMoonWithPhase` (soglie 0,35/0,65/0,98 su `illuminated`,
  rotazione 180° per la fase calante, disco scuro «earthshine» `blendARGB(litColor, 0xFF10101A,
  0.82)`), `HALLOWEEN_MOON_COLOUR`, `CELESTIAL_GLOW_CENTRE_ALPHA` e `StormAtmosphere.sunAlpha`
  (l'alfa del sole cala col temporale).
- `SceneCustomization`: `StarsConfig(visible, density)` → `regenerateStars`: conteggio
  `(70 × density).toInt()`, raggi 2,4–5,6 px, 1 scintilla ogni 5 — la densità diventa conteggio
  qui e solo qui.
- `ThemePreviewScene` (le anteprime della galleria, con **origini proprie**): luna a (−40,−40),
  `sun_glow` a (−66,−66) e `sun_body` a (−40,−40) tintati `c.sun.color` (nella preview il sole È
  tintato, a differenza della scena), stelle come `PreviewDot` in `theme.starColor`, e — facile
  da dimenticare — **le palline dell'abete di Natale sono blit di `star_sparkle`** a (−26,−110)
  tintate oro (`0xFFF2C14E`, alpha 220): un ridisegno della scintilla riveste anche l'abete della
  preview (il backlog v4.21, voce 26, le dà già «placed by eye»).
- `WallpaperPrefs`/`CustomThemeData`: persistono `moon.realisticPhases`, i colori sole/luna/stelle
  dei temi custom — nessuna geometria, nessun impatto da un cambio d'artwork a tela costante.

**Test (JVM):**
- `SkySpriteAnchoringTest` — legge le dimensioni PNG da disco e le lega alle costanti del
  renderer: **è il test che obbliga a non cambiare tela** (o a cambiare origine nello stesso
  cambio).
- `SpriteTintClassTest` — lune maschere neutre e medie ≥ 220, sole/alone/scintilla con colore.
- `SpriteGeometryTest` — griglia 3× e **budget decodificato** (il tetto dei 29 MiB).
- `SpriteCanvasConventionTest`, `BackgroundScrollGeometryTest` (estensioni sprite del campo
  stellare), `ThemePreviewSceneTest`.
- `GlDriverGapGuardTest` (strumentato) — i tre golden GL contengono il sole col glow.

**Pipeline (tools/):**
- `sources/sprites.json` (voci dei sei sprite; la falce porta la nota storica v76.1),
  `normalize.py` e `callsites.py` li nominano; i report `fidelity.json`/`runtime-inventory.json`
  li elencano; `test_manifest.py` li tocca nei fixture.

**Fuori dal codice:** `DESIGN_NOTES.md` §3 (classi di tinta dei celesti e conseguenze V2 —
«Sun Color non raggiunge più il disco»), §5 (i celesti fuori dalla proiezione a terra).

---

## 6. I due tetti (§6 del mandato)

**Sprite (MISURATO).** Set spedito: 266 PNG, footprint decodificato ARGB_8888
**30 254 580 B** contro il tetto di 29 MiB = 30 408 704 B → margine **154 124 B**, come da
baseline. Tutti e tre i concept tengono **le stesse tele pixel-per-pixel** dei sei sprite che
sostituiscono, quindi il consumo del margine è **0 B per tutti e tre** — non stimato: ricalcolato
sommando L×A×4 dei PNG di staging contro i spediti, delta 0 per a, b e c. I byte *di file* (APK)
cambiano (es. `sun_glow`: 44 502 → A 30 587 / B 41 678 / C 7 267), ma il tetto del progetto
misura il decodificato, non il file. **Condizione E non scattata.**

**CPU (DEDOTTO dalla geometria dei blit, con la derivazione scritta).** Il costo di disegno del
cielo dipende dalla geometria dei blit — quali quad, di che dimensione, quante volte — non dai
pixel dentro la texture: un texel trasparente e uno pieno costano uguale al momento del
campionamento del quad. Nessun concept cambia dimensione, numero o frequenza dei blit celesti
(stesse tele, stessi siti di chiamata, stesse origini): il lavoro per frame è lo stesso per
costruzione, e non c'è «riempimento aggiunto nel cielo» da misurare — il caso che il §6 del
mandato chiede di misurare col protocollo non si dà. La baseline resta quella della voce 34:
**14,72 ms di CPU per frame su 33,33 (43,56 % di un core, simil-release, Autumn)**. Se il
coordinatore vuole comunque il numero per il concept scelto, la fase 2 lo misura col protocollo di
`V4_22_MISURA_CPU_REPORT.md` (ora nell'albero) sulla build simil-release di quel concept.

---

## 7. Contabilità

MISURATO in questa sessione, con l'aritmetica:

- **JVM 1331/0/0.** `testDebugUnitTest`, contati dagli XML: 1331 tests, 0 failures, 0 errors.
- **Strumentati 148/0.** `am instrument` sull'intera suite: `OK (148 tests)`.
- **Python 108/0.** `unittest discover -s tools/assets/tests`: Ran 108 tests, OK.
- **PNG golden 27.** `androidTest/assets/golden/`: 27 file, di cui 3 `gl-*` → 24 Canvas + 3 GL.
- **Voci archivio: 928** = 884 (baseline) + 44 nuove (0 rimosse). Le 44: 2 report in radice
  (`V4_22_MISURA_CPU_REPORT.md`, `V4_23_FASE1_CONCEPT_REPORT.md`) + 3 concept × 14
  (6 SVG + 6 PNG + `sprites.concept.json` + `DESCRIZIONE.md`) = 2 + 42 = 44.
- `@Ignore`: 0 (le 3 occorrenze di grep sono tutte dentro commenti).

Contro il §0 il conteggio combacia su tutte le voci; la sola cifra cambiata di proposito è
`versionCode`/`versionName` 53/"4.22" → **54/"4.23"**.

---

## 8. Stato del dispositivo

**OSSERVATO alla riconsegna.**

- **APK attivo:** `com.paperscrape.livewallpaper` (la **release del maintainer**, `versionCode 53`
  `versionName "4.22"`, firma `d8eb208c` — **≠** `debug.keystore`), rimessa come wallpaper vivo e
  **osservata mentre rende** la scena completa sul launcher (notte Autumn, ora reale). `mEngine`
  legato, non nullo. **Mai toccata né disinstallata** durante il pass.
- **Build concept `.debug` disinstallata:** `com.paperscrape.livewallpaper.debug` non è più sul
  device (`pm list packages` mostra solo il pacchetto release). Le tre build concept erano un pacchetto
  separato (`applicationIdSuffix=".debug"`) e non potevano collidere con la release.
- **Verifica byte contro il pull:** gli APK concept installati erano costruiti dallo ZIP di lavoro e i
  loro sprite celesti verificati byte-per-byte contro lo staging (18/18) prima dell'installazione; la
  release del maintainer non è stata ricostruita né modificata, quindi non c'era nulla da ri-verificare
  su di essa se non che è la stessa (stessa firma e versione del pull iniziale).
- **Impostazioni ripristinate:** `screen_brightness_mode` 1 (auto), `screen_off_timeout` 60000,
  `screen_brightness` 12 — i valori annotati a inizio pass. `auto_time` era ed è 1. Night mode «no».
- **Trappole del device incontrate e gestite:** `am instrument` filtrato NON usato (suite intera, così
  Gradle non disinstalla il pacchetto); `pm clear` ha fatto ripiegare Android sul wallpaper statico una
  volta (rimesso col dialogo `CHANGE_LIVE_WALLPAPER` → «Home screen»); dopo ogni `install -r` il motore
  si rilega in ~5-10 s (`mEngine` passa da null a legato); il launcher Blackview non manda mai
  `onOffsetsChanged` (moto orizzontale dall'accumulatore, come da baseline).

---

## 9. File toccati (derivati dal diff dei due archivi)

Derivata dal **diff dei due archivi** (`67390b26…` → `11d4aedb…`), non da `git status`. **44 file
aggiunti, 0 rimossi, 1 modificato.**

*Modificato (contenuto, stesso path):*
- `app/build.gradle.kts` — bump `versionCode`/`versionName` a 54/"4.23".

*Aggiunti (44):*
- `V4_22_MISURA_CPU_REPORT.md` (prerequisito 1b)
- `V4_23_FASE1_CONCEPT_REPORT.md` (questo report)
- `tools/assets/concepts/{a,b,c}/DESCRIZIONE.md` (3)
- `tools/assets/concepts/{a,b,c}/sprites.concept.json` (3)
- `tools/assets/concepts/{a,b,c}/svg/{sun_body,sun_glow,moon_full,moon_crescent,moon_half,star_sparkle}.svg` (18)
- `tools/assets/concepts/{a,b,c}/svg/{…stessi sei…}.png` (18)

**Nessun file spedito modificato oltre `app/build.gradle.kts`.** In particolare: i sei PNG celesti in
`app/src/main/res/drawable-nodpi/` e i loro sorgenti in `tools/assets/sources/svg/` sono
**byte-identici** agli entry dello ZIP di partenza (verificato contro le voci del ZIP, non contro una
riestrazione). `SettingsGates`, tolleranze, i tre golden GL: intatti.

---

## 10. Che cosa NON è stato fatto, di proposito

- Nessuna scelta fra i concept, nessun criterio numerico derivato per giudicarli: l'ordine
  giusto è guardare prima (il ciclo della v4.18 è il precedente).
- Nessun golden rigenerato, nessuna tolleranza o cancello toccati, i tre GL intatti.
- `moon_gibbous` e `moon_jack_o_lantern` non ridisegnati: sono la fase 2 del concept scelto.
- La palma non toccata (voce 25, fuori perimetro). Nota per il report, non per il codice: il
  crema `#FBF4E6` della scintilla è duplicato nella costante Kotlin dei punti-stella
  (`PaperRenderer`, «STAR_SPARKLE colour») — se la fase 2 cambiasse il crema, i due punti vanno
  mossi nello stesso cambio; nessuno dei tre concept lo cambia.
- `assembleDebug` è stato eseguito (serviva per installare i concept); `test` completo eseguito
  sulla baseline; `lintDebug` non eseguito — pass di concept, non release: *assemble* qui è lo
  strumento di fotografia, non un livello di verifica dichiarato.
