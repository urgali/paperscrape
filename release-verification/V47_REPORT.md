# PaperScrape v4.7 — release candidate, report

Legenda: **VERIFIED** = eseguito in questa sessione e ne ho visto l'output ·
**OBSERVED** = misurato, è una conseguenza da conoscere, non un pass/fail ·
**INFERRED** = dedotto dal codice, non eseguito · **NOT VERIFIED** = non fatto,
col motivo.

## Baseline

**VERIFIED** — `v4.6.zip` in `~/claudes`, sha256
`5d8a501c51c252d1d6c465e3a863d2ced0cabe2e9675ee68b743a5a196554ee9`, 782 file,
`versionCode = 37`, `versionName = "4.6"`.

**VERIFIED — torna.** Rispetto alla v4.5 cambiano 24 file, e sono esattamente i
soggetti della v4.6: scala di autisti e passeggeri (`SceneSpace`,
`VehicleOccupantScaleTest`, `VehiclePedestrianScaleTest`), overlap pedoni/auto
(`PeopleOcclusionTest`, `PeopleTrafficDepthTest`), UI stale dopo il restore
(`SettingsActivity`, `SettingsScreen`, `CustomThemeStore`), cancellazione e
rollback del backup (`BackupRepository`), background location
(`BackgroundLocationContractTest`, `location/`), più `release-notes/v4.6.md`. I due
soli golden diversi sono `traffic-day` e `traffic-night`, coerenti col lavoro sugli
occupanti.

**VERIFIED** — l'asset Santa in v4.6 è **byte identico alla v4.5** in tutti e
quattro i file, `sprites.json` ha le stesse entry e le costanti del renderer sono
invariate: il pacchetto artwork si applica pulito.

## Cosa è cambiato in v4.7

**VERIFIED** — otto file, nessun altro:

| File | Cambiamento |
|---|---|
| `app/build.gradle.kts` | `versionCode 37 → 38`, `versionName "4.6" → "4.7"` |
| `app/src/main/res/drawable-nodpi/santa_sleigh_scene.png` | nuovo artwork |
| `app/src/main/res/drawable-nodpi/santa_sleigh_trot.png` | nuovo artwork |
| `tools/assets/sources/svg/santa_sleigh_scene.svg` | nuovo sorgente |
| `tools/assets/sources/svg/santa_sleigh_trot.svg` | nuovo sorgente |
| `tools/assets/sources/sprites.json` | `contentBox`, `anchor`, `notes` delle due entry |
| `release-notes/v4.7.md` | **nuovo** — testo utente |
| `RELEASE_HISTORY.md` | voce v4.7 |

**Nessun file Kotlin.** `R.drawable.santa_sleigh_scene` e `santa_sleigh_trot`
mantengono lo stesso nome: nessun riferimento da aggiornare, nessun vecchio asset
rimasto appeso.

## Asset

| | v4.6 | v4.7 |
|---|---|---|
| `santa_sleigh_scene.png` | `649c66d1d394…` | **`b504d6c2792b…`** |
| `santa_sleigh_trot.png` | `3d0d6299acc6…` | **`3e960cea1d4c…`** |
| Canvas | 600 × 153 | **600 × 153, invariato** |
| Content box | (0, 1, 598, 152) | **(0, 19, 592, 140)** |
| Anchor dichiarato | (299, 152) | **(296, 140)** — `anchorRule` resta `CONTENT_BOTTOM_CENTRE` |
| `scale` / `tint` | `SCENE_UNITS` / `FIXED_ART` | invariati |

**VERIFIED** — i due PNG sono **byte identici** ai mockup C2 FINAL approvati, e i
due SVG committati **li rirenderizzano byte per byte** con la pipeline pinnata
(`probe` conforme a `PROBE_EXPECTED_SHA256`): sono la sorgente vera.

**VERIFIED** — `scene` e `trot` differiscono solo in **x 35…265, y 102…139**, cioè
le zampe delle renne. Nessuna differenza accidentale fra le due varianti.

**VERIFIED** — costanti del renderer invariate: `SANTA_SLEIGH_SCALE = 1.5f`,
`SANTA_SLEIGH_ORIGIN_X_UNITS = -99.67f`, `SANTA_SLEIGH_ORIGIN_Y_UNITS = -25.5f`,
`SANTA_TROT_FRAMES_PER_SECOND = 4.5f`. `SantaSleighEffect` non toccato: stessa
traiettoria, stesso timing, stesso fade, stesso gift drop.

### Drift accettato: 1,5 px a sinistra, 1,5 px in basso

**OBSERVED, e ACCETTATO dal maintainer — nessuna compensazione applicata.**

Il nuovo `contentBox` è `(0, 19, 592, 140)` contro `(0, 1, 598, 152)`: la
composizione allinea le basi del tiro — zoccoli e pattino sulla stessa linea — e il
gruppo non tocca più i bordi del canvas. Il centro del contenuto passa da
`(299,0 , 76,5)` a `(296,0 , 79,5)`, cioè **(−3, +3) pixel dello sprite**.

Il renderer piazza lo sprite tramite `SANTA_SLEIGH_ORIGIN_X_UNITS` e
`SANTA_SLEIGH_ORIGIN_Y_UNITS`, che indirizzano l'angolo del **canvas**, non il
content box. Il canvas è invariato a 600 × 153, quindi il blit cade esattamente
dove cadeva prima. Quello che si sposta è il disegno **dentro** il canvas, e dopo
il blit a 0,5× il risultato a schermo è:

> **1,5 px verso sinistra e 1,5 px verso il basso.**

**Decisione: il drift è accettato così com'è.** `SANTA_SLEIGH_SCALE`, i due
`SANTA_SLEIGH_ORIGIN_*_UNITS`, `SANTA_TROT_FRAMES_PER_SECOND` e ogni riga di
`SantaSleighEffect` **non sono stati toccati** e non vanno toccati per
compensarlo: compensare significherebbe introdurre una costante correttiva al call
site per un errore che non esiste, che è esattamente ciò che
`AI_PROJECT_RULES.md` §7.3 vieta. Lo scostamento è sotto i due pixel su uno sprite
che vola nel cielo qualche volta all'ora.

**INFERRED** — nessun Kotlin legge `sprites.json`: la pipeline è tooling offline e
il suo README dice che Gradle non la esegue mai.

## Golden

**VERIFIED — nessun golden contiene la slitta.** Scansionati tutti e 27 cercando
la co-occorrenza dei colori firma (scafo `#F0A03C`, renna `#8C5A38`) nella fascia
di cielo del volo: zero risultati, coerente col fatto che l'effetto parte su timer
casuale. **Nessun golden rigenerato**, e la suite instrumented — che li verifica —
passa.

## Test e build

Eseguiti due volte: sull'albero di lavoro, e di nuovo **dall'estrazione pulita
dello ZIP di consegna**. Stessi numeri in entrambi i giri.

| | Albero di lavoro | Estrazione pulita dello ZIP |
|---|---|---|
| `paperscrape_assets probe` | **VERIFIED** `matches_expected: True` | — |
| `paperscrape_assets validate` | **VERIFIED** output byte identico a quello su v4.6; nessuna riga su santa (i 96 `FAIL` sono preesistenti: varianti skin senza entry) | — |
| `testDebugUnitTest --rerun-tasks` | **VERIFIED** 1042 test, 0 failures | **VERIFIED** **1042 test, 0 failures, 0 errors, 0 skipped** |
| `lint` | **VERIFIED** BUILD SUCCESSFUL | **VERIFIED** BUILD SUCCESSFUL, report generato |
| `assembleDebug` | **VERIFIED** BUILD SUCCESSFUL | **VERIFIED** BUILD SUCCESSFUL |
| `assembleDebugAndroidTest` | **VERIFIED** BUILD SUCCESSFUL | **VERIFIED** BUILD SUCCESSFUL |
| `assembleRelease` | **VERIFIED** BUILD SUCCESSFUL | **VERIFIED** BUILD SUCCESSFUL — `app-release-unsigned.apk` (la firma di release legge da variabili d'ambiente, assenti qui: l'APK esce non firmato, come previsto) |
| `connectedDebugAndroidTest` | **VERIFIED** 98 test, 0 failures | **VERIFIED** **98 test, 0 failures**, `Pixel_9(AVD)` API 37 |

Esito del giro dall'estrazione pulita: `BUILD SUCCESSFUL in 14m 54s`, `EXIT=0`.

## ZIP ed estrazione pulita

Contenuto: **tutto il progetto** più una cartella `release-verification/` con
questo report e gli screenshot runtime.

Presenti e verificati esplicitamente nell'archivio:

| | |
|---|---|
| `.gitignore` | 1 572 byte |
| `.github/workflows/android-build.yml` | 14 725 byte |
| `.github/workflows/dependency-submission.yml` | 1 845 byte |
| `CLAUDE.md` | 21 762 byte |
| `AI_PROJECT_RULES.md` | 41 329 byte |
| `debug.keystore` | presente |
| `release-verification/V47_REPORT.md` | questo file |
| `release-verification/screenshots/` | i quattro screenshot runtime |

**Nota su `CLAUDE.md`.** Il file compare nel `.gitignore` (riga 44), ma
l'archivio è costruito dal filesystem con `zip -r`, non da `git archive`, quindi
la regola non si applica e il file **è incluso**. Lo era già nel primo pacchetto
v4.7; la verifica sopra lo conferma per byte.

Esclusi: `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `local.properties`,
`__pycache__/`, `tools/assets/staging/`, log e temporanei.

**VERIFIED** — zero `.apk`, zero `.aab`, zero voci `.git/` nell'archivio.
Struttura di root identica alla v4.6 (flat, senza cartella wrapper).

**VERIFIED — estrazione pulita**: estratto in directory vuota, `diff -rq` contro
l'albero di lavoro non mostra differenze fuori dalle directory non versionate; da
lì è stato ricostruito e testato — i risultati sono nella tabella della sezione
precedente.

**VERIFIED — secret scan** sull'estrazione: nessuna chiave privata, nessun token
GitHub/AWS/Slack. L'unico hit su pattern password è `storePassword = "android"` in
`app/build.gradle.kts`: è la password pubblica del debug keystore Android,
committata di proposito e già presente in v4.5 e v4.6. La firma di release legge
solo da variabili d'ambiente.

Lo **SHA-256 dell'archivio** è pubblicato insieme alla consegna, in
`SHA256SUMS.txt` accanto allo ZIP: un file non può contenere l'hash del contenitore
che lo contiene.

## Runtime

**VERIFIED** — emulatore avviato da questa sessione: AVD `Pixel_9`,
`ro.build.version.sdk = 37`, modello `sdk_gphone64_x86_64`. **OBSERVED** — è un AVD
di classe Pixel, non un Pixel 9 fisico.

Percorso: installato l'APK debug della v4.7, selezionato il tema **Christmas**,
aperto il live wallpaper a schermo intero, catturate due raffiche da 45 screenshot
e isolati i frame in cui la slitta è realmente in volo. Per la notte ho usato
l'impostazione dell'app **Weather & time → Time of day → Fixed time = 23:00**
(cielo medio misurato `(41, 50, 75)`).

**VERIFIED** — il nuovo artwork gira nel renderer reale e corrisponde al mockup
approvato: renne nuove con collare e campanella, slitta con prua/pattino/schienale,
Santa B seduto con la muffola sul bordo, sacco bordeaux C2 FINAL con i pacchi
dietro. Verificato di giorno e di notte.

**OBSERVED** — nel confronto prima/dopo la slitta "prima" vola specchiata: è la
direzione casuale che `SantaSleighEffect` sceglie a ogni volo, non una differenza
fra gli asset.

## Git

**NOT VERIFIED / non fatto** — nessun commit e nessun branch: lo ZIP v4.6 non
contiene una `.git`, quindi in questa sessione non esiste un repository in cui
committare. Un `git init` creerebbe una storia finta, quindi non l'ho fatto. Il
commit va fatto nel checkout reale del maintainer.

**Nessun push, nessun tag, nessuna GitHub Release, nessuna credenziale usata.**

## Criterio di successo — stato

| | |
|---|---|
| 1. Santa B invariato | ✅ stesse coordinate e stesse forme, coperto in basso dalla fiancata come da design approvato |
| 2. Renne D2 presenti | ✅ |
| 3. Slitta D2 presente | ✅ |
| 4. Sacco/regali C2 FINAL | ✅ |
| 5. Canvas 600 × 153 | ✅ invariato |
| 6. Anchor/scala/posizione | ✅ costanti invariate; anchor dichiarato aggiornato al content box reale; 1,5 px di spostamento apparente documentato |
| 7. Layering corretto | ✅ verificato a runtime |
| 8. Scena coerente a dimensione reale | ✅ screenshot 1:1 |
| 9. Nessun altro asset o tema modificato | ✅ otto file, cinque dei quali l'asset |
| 10. Suite verdi | ✅ 1042 JVM + 98 instrumented + lint + tutti gli assemble |
| 11. ZIP pulito verificato | ✅ estratto, ricostruito, testato, scansionato |
| 12. Screenshot runtime | ✅ giorno, notte, dimensione reale, prima/dopo |

## Cosa resta al maintainer

1. Applicare lo ZIP sul checkout reale (o `git apply` degli otto file).
2. Commit locale.
3. Tag `4.7` — la CI verifica che il tag corrisponda a `versionName`.
4. Push e Release quando decidi tu.
