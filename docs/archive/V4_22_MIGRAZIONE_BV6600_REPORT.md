# PaperScrape — migrazione al dispositivo di test nuovo (Blackview BV6600)

**Data:** 2026-09-05 · **Stato:** PREPARED — NOT PUBLISHED
**Versione:** `versionCode 53`, `versionName "4.22"` — **non incrementati**, come richiesto.
**Perimetro:** ristabilire una baseline sul dispositivo nuovo. Nessuna funzionalità aggiunta,
nessun golden rigenerato, nessun cancello spostato, nessun codice spedito modificato.

Ogni affermazione è etichettata **OSSERVATO**, **MISURATO**, **DEDOTTO**, **DICHIARATO** o
**NON ATTRIBUIBILE**.

---

## 0. In due righe, prima di tutto

**Il wallpaper rende sul dispositivo nuovo** (condizione E non scattata) e **i ms di CPU per frame
stanno sotto l'intervallo di 33 ms con più della metà del margine libero: 14,72 ms, il 44,2%**.
Le tre suite danno **1331 / 148 / 108** con **un solo fallimento**, che è **NON ATTRIBUIBILE** e su
cui il pass si è fermato senza toccare nulla. Due debiti storici si chiudono con una misura (voci
20 e 31); ne nasce uno nuovo e stretto (voce 36: il cancello delle persone ha già consumato l'86%
del suo margine).

---

## 1. Baseline verificata — condizione D non scattata

MISURATO, prima di toccare qualsiasi cosa:

| voce | atteso | trovato |
|---|---|---|
| ZIP `PaperScrape_v4_22.zip` SHA-256 | `a8b94b79…4fcb5c` | **combacia** |
| byte | 5 745 260 | **5 745 260** |
| voci d'archivio | 882 | **882** |
| `versionCode` / `versionName` | 53 / "4.22" | **53 / "4.22"** |
| PNG golden committati | 27 | **27** |
| `@Ignore` | zero | **zero** (le 3 occorrenze di `@Ignore` nel testo sono dentro commenti) |
| JVM / strumentati / Python | 1331 / 148 / 108 | **1331 / 148 / 108** |

**Una trappola incontrata subito, che vale per chiunque estragga questo ZIP:** `gradlew` perde il
bit di esecuzione nell'estrazione. La prima esecuzione della suite JVM è tornata «exit code 0» con
`Permission denied` nascosto dentro una pipe verso `tail`, e **zero test eseguiti**. Il conteggio
dei risultati dall'XML è ciò che l'ha smascherata: un `BUILD SUCCESSFUL` non è una prova, il
conteggio sì.

---

## 2. Fase 1 — il dispositivo funziona, e che cosa fa il suo launcher

### 2.1 Il wallpaper rende — OSSERVATO

**Condizione E NON scattata.** L'APK simil-release costruito dallo ZIP si installa su Android 10
(`minSdk = 26`), si avvia, e il wallpaper rende. Non dedotto: guardato, e catturato.

- `catture/f1_01_app_avviata.png` — l'app si avvia, l'anteprima Canvas rende.
- `catture/f1_02_picker.png` — il percorso **GL** rende la scena completa nel picker.
- `catture/f1_03_home_wallpaper.png` — il wallpaper **vivo sul launcher**: cielo, sole, nuvole,
  uccelli, colline, edifici, alberi, persone, strada.
- `catture/f5_notte_autumn.png` — notte: luna, stelle, finestre accese, fari, traffico.
- `catture/f5_inverno_giorno.png` — tema Winter: neve sui tetti e sugli alberi, neve che cade,
  persone vestite da inverno.

Nessun oggetto mancante, nessuna area sbagliata, nessun artefatto GL.

### 2.2 Gli offset di scorrimento — MISURATO, con controllo positivo

**Questo launcher non manda offset. Mai.**

`com.blackview.launcher/com.android.searchlauncher.SearchLauncher`. Una sonda locale su
`onOffsetsChanged` (build `perf` locale, mai committata, mai nello ZIP — §9) ha registrato
**zero chiamate** su decine di swipe in entrambe le direzioni, a schermo sbloccato e col launcher
in primo piano.

**Il controllo positivo è ciò che rende la misura credibile:** la sonda funziona. Il *picker* del
wallpaper la chiama esattamente una volta:

```
I OFFSETPROBE: xOffset=0.0 yOffset=0.0 xStep=0.0 yStep=0.0 xPix=0 yPix=0
```

**Il motivo di fondo, OSSERVATO:** questo launcher ha **una sola pagina home** (più il pannello
Google Discover a sinistra, che non è una pagina del launcher). Cinque swipe in avanti lasciano
l'icona esattamente dov'era. Non c'è nessuna pagina verso cui scorrere, quindi non c'è nessun
offset da mandare.

**Il movimento che si vede non viene dallo swipe.** `scrollProgress = continuousScrollAccum +
swipe`: con `swipe` sempre 0, resta l'accumulatore. MISURATO su 30 s senza alcuna interazione,
lineare:

| banda | deriva |
|---|---|
| edifici + alberi | **0,73–0,80 px/s** |
| colline di sfondo | **0,20 px/s** |

Un primo tentativo di misura aveva attribuito ~3 px «per pagina» allo swipe. Era sbagliato: un
controllo sulla *stessa* pagina, senza swipe, dava la stessa deriva. Il numero è stato buttato e
rifatto. È lo stesso comportamento qualitativo del telefono vecchio, dove pure il launcher non
mandava offset — ma qui è **MISURATO con una sonda**, non dedotto dal movimento.

### 2.3 Risoluzione, densità, proporzione

OSSERVATO: **720×1440**, densità **320**, dpi reali **268,941 × 247,135**, 60 Hz, un solo modo.
La proporzione è **18:9 esatta** contro il 19,5:9 del telefono vecchio. Il rapporto è dichiarato
dal pannello, non calcolato da me.

---

## 3. Fase 2 — le tre suite, voce per voce

| suite | attesi | eseguiti | esito |
|---|---|---|---|
| `:app:testDebugUnitTest` (JVM, sull'host) | 1331 | **1331** | **0 fallimenti, 0 errori, 0 saltati** |
| `:app:connectedDebugAndroidTest` (sul BV6600) | 148 | **148** | **1 fallimento**, 0 errori, 0 saltati |
| Python del tooling | 108 | **108** | **OK** |

La suite JVM è stata **rieseguita con `--rerun`** perché il primo risultato veniva dalla cache di
build: un risultato in cache è legittimo ma non è un'esecuzione, e questo pass ne aveva bisogno.
I 108 test Python girano **solo** con `/home/bober/.venvs/paperscrape-assets/bin/python`.

**Tutti i 25 golden Canvas passano. Tutti e 3 i golden GL passano. Tutti e quattro i cancelli di
`SettingsGates` passano.** L'unico fallimento non è un golden.

### 3.1 L'unico fallimento — `VehicleOccupantScaleTest.noOccupantPixelLeavesTheGlass`

```
PLAIN on lane 0.834 draws 1268 occupant pixels outside its glass,
first at (-13.740388, -1.5515823)
```

**Attribuzione: NON ATTRIBUIBILE. Condizione F scattata: il pass si è fermato su questo caso e non
ha cambiato niente.**

Non è una differenza di renderer e non è una differenza di contenuto. È la geometria
dell'asserzione che valuta male su questo dispositivo.

**Il contenuto reso è corretto — MISURATO, attribuzione pixel per regione.** I pixel che il test
conta come «fuori dal parabrezza» hanno questo riquadro, in unità locali del veicolo:

| | x | y |
|---|---|---|
| pixel contati «fuori» | **[-16,88 , 22,77]** | **[-10,97 , 7,87]** |
| tutti i pixel di occupante nel frame | [-16,88 , 22,77] | [-10,97 , 7,87] |
| **il riquadro vero del vetro** | **[-28 , 34]** | **[-16 , 9]** |

I due primi riquadri **coincidono** — cioè *ogni* pixel di occupante viene contato come fuori — e
stanno **interamente dentro** il terzo. Gli occupanti sono disegnati esattamente dove devono
stare. I colori trovati sono esattamente la tavolozza degli occupanti, non quasi-corrispondenze
da antialiasing: `#F0C9A6` (pelle donna, 780 px), `#A9714B` (pelle bambino, 559), `#2B2A33`
(capelli scuri, 534), `#F7CE64`, `#E4623E`, `#4E9FB5`, `#EFDFC4`.

**Dove sta il difetto.** Le due variabili locali del test valgono **0.0** a runtime:

```
OCCDIAG2: … | PANE L=-28.0 R=34.0 T=0.0 B=0.0 | CONST origin=-16.0 sill=9.0 | …
```

Nella **stessa riga di log**, le costanti da cui `paneT` e `paneB` sono assegnate leggono
correttamente −16.0 e 9.0. Con un riquadro di altezza zero, ogni pixel di occupante è «fuori».

**Che cosa è stato escluso, per non scegliere la spiegazione comoda:**

| ipotesi | prova | esito |
|---|---|---|
| la costante è sbagliata | `t2 = if (isTruck) 99f else CAR_GLASS_ORIGIN_Y_UNITS` scritta accanto | dà **−16.0** → esclusa |
| è stato preso il ramo del camion | `isTruck=false`; quel ramo darebbe −13.0 | non è 0.0 → esclusa |
| il compilatore ha piegato male la costante | disassemblato il dex del test | contiene `const/high16 #c180` (−16.0) e `#4110` (9.0) → esclusa |
| è l'espressione in sé | stessa identica espressione in un test minimo, **su questo stesso dispositivo** | dà `paneT=-16.0 paneB=9.0` (e −13.0/6.0 per il camion) → esclusa |

Resta un **indizio, non una conclusione**: il metodo che fallisce è enorme e il dex gli assegna
oltre 39 registri, e il difetto sparisce in un metodo piccolo. Non è dimostrato. Finché non lo è,
questa resta **NON ATTRIBUIBILE** ed è la voce di backlog **35**.

**Niente è stato cambiato:** né il test, né il codice spedito, né una tolleranza.

### 3.2 I golden passano, ma nessun frame è byte-identico — e questo conta

MISURATO. Nessuna delle 24 scene catturate è byte-identica al golden committato, autorato sul
OnePlus. **Ma tutte le asserzioni passano**, e la forma della differenza dice perché.

Con la metrica del progetto (`CHANNEL_TOLERANCE = 8`, `MAX_DIFFERING_FRACTION = 0,2%`):

| scena | frame diverso | scena | frame diverso |
|---|---|---|---|
| `people-window` | 0,0073% | `people-mixed` | 0,0326% |
| `people-overlap` | 0,0090% | `people-commercial` | 0,0361% |
| `rain` | 0,0194% | `snow` | 0,0368% |
| `thunderstorm` | 0,0194% | `traffic-day-sparse` | 0,0417% |
| `overcast` | 0,0198% | `people-skyscraper` | 0,0469% |
| `day` / `people-single` | 0,0201% | `traffic-day` | 0,0573% |
| `lake-empty` | 0,0201% | `night` | 0,0649% |
| `lake-boats` / `lake-busy` / `lake-dolphin-leap` | 0,0205% | `traffic-night-quiet` | 0,0656% |
| `dusk` | 0,0170% | `shops-closed-night` | 0,0740% |
| `people-skin` | 0,0253% | `theme-city` | 0,0861% |
| | | `traffic-night` | **0,0990%** |

**Attribuzione: RENDERER**, per i tre criteri del prompt insieme.

- **Piccola ampiezza, sparsa su tutta l'immagine.** Il 31–42% dei pixel differisce di **1–8
  livelli** — sotto la tolleranza del progetto. È il gradiente del cielo che una Skia diversa
  distribuisce diversamente (bande orizzontali visibili nell'immagine di differenza).
- **Confinata ai bordi.** Solo lo 0,007–0,099% supera la tolleranza di 8, e su `people-single`
  sono **58 pixel sparsi su 32 righe e 40 colonne** — granelli isolati, non una macchia.
  Nell'immagine di differenza sono stelle, cerchi dei cerchioni, spigoli di finestre.
- **Nessun oggetto intero manca, si sposta o cambia colore.** Le sagome di tutti gli oggetti
  combaciano esattamente.

Immagini di differenza in `diffimg/` (blu = differenza sotto tolleranza, rosso = sopra):
`diff-people-single.png`, `diff-traffic-night.png`, `diff-theme-city.png`.

### 3.3 L'incrocio Canvas/GL, scena per scena — e cosa implica per i tre golden GL

Il prompt prevedeva che i tre golden GL potessero fallire su PowerVR, e che in quel caso il debito
della voce 20 andasse **cancellato** invece che pagato. **Non è andata così: passano tutti e tre**,
e con margine larghissimo.

`GlDriverGapGuardTest` misura lo spostamento dei bordi fra il driver che gira e ciò che è
committato. MISURATO sul PowerVR GE8320, contro golden autorati sull'Adreno 630:

| scena | golden **Canvas** della stessa scena | golden **GL**: spostamento bordi | cancello |
|---|---|---|---|
| `day` | **passa** (0,0201% del frame) | **0,00%** | 3,00% |
| `lake-busy` | **passa** (0,0205%) | **0,01%** | 3,00% |
| `thunderstorm` | **passa** (0,0194%) | **0,24%** | 3,00% |

**L'incrocio è pieno su tutte e tre le scene: il golden Canvas passa, quindi il contenuto è giusto,
e il golden GL passa, quindi anche il backend è dove deve stare.**

**Che cosa implica.** Il divario Adreno-contro-emulatore caratterizzato dalla voce 19 era
0,92–1,18%. Il PowerVR è **più vicino di così** ai golden autorati sull'Adreno. I golden GL
reggono un cambio di **vendor** della GPU, non solo di driver. La voce 20 chiedeva *«one run of
the golden capture on a second environment»*: il secondo ambiente è arrivato, non era l'emulatore
che la voce immaginava, e la risposta è affermativa. **Il debito è pagato, non cancellato.**
Nessuna decisione di rigenerazione è necessaria; la decisione resta comunque del coordinatore.

---

## 4. Fase 3 — i quattro cancelli, ri-derivati da zero

### 4.1 Il pavimento di rumore — MISURATO, ed è ancora zero

Protocollo della Fase 1 di v4.22, riusato: la stessa scena resa in **esecuzioni separate**, dopo
**riavvio del processo** e dopo **riavvio del telefono**, e i PNG confrontati byte a byte.

- Esecuzione **A** — 24 scene catturate.
- Esecuzione **B** — processo terminato e riavviato fra le due.
- Esecuzione **C** — **dopo un riavvio completo del telefono**.

**Risultato: 0 PNG diversi su 24.** Byte-identici in tutte e tre. Il pavimento di rumore è
**0,0000%** su ogni rettangolo usato dai cancelli e sul frame intero. La pipeline Canvas è
bit-deterministica anche qui.

*(Meccanismo: `-e updateGoldens true` scrive il frame reso in una cartella dell'app senza mai
toccare i golden committati. La cartella è stata spostata localmente su `filesDir` perché su questo
dispositivo `getExternalFilesDir` non è disponibile all'app; modifica locale, non nello ZIP.)*

### 4.2 I quattro segnali — MISURATO, e si riproducono alla quarta cifra

Dai test di derivazione, che li rimisurano a ogni esecuzione:

```
GATEDERIVE: people density: hidden=0.2829% ignored=0.7110% gate=0.1415%
GATEDERIVE: car count ignored (35% drawn as 100%): floor=0.0000% signal=7.6263% gate=3.8041%
GATEDERIVE: night car density ignored: floor=0.0000% signal=14.0079% gate=7.0057%
GATEDERIVE: business hours ignored: floor=0.0000% signal=2.0053% gate=1.0038%
```

### 4.3 I due numeri per ogni cancello, e se il limite sta ancora in mezzo

| cancello | pavimento di rumore | segnale BV6600 | (segnale OnePlus) | limite | **sta in mezzo?** |
|---|---|---|---|---|---|
| densità persone | **0,0000%** | **0,2829%** | 0,283% | 0,1415% | **sì** |
| conteggio auto giorno | **0,0000%** | **7,6263%** | 7,6082% | 3,8041% | **sì** |
| densità auto notte | **0,0000%** | **14,0079%** | 14,0115% | 7,0058% | **sì** |
| orario commerciale | **0,0000%** | **2,0053%** | 2,0075% | 1,0038% | **sì** |

**Tutti e quattro i limiti stanno ancora fra il loro pavimento e il loro segnale. Nessun limite è
stato spostato.**

### 4.4 Ma c'è un terzo numero che la derivazione non aveva — ed è stretto

Il pavimento di *rumore* è zero. Non è però l'unica cosa che consuma il budget di un cancello: c'è
anche lo **scarto costante di renderer** fra ciò che questo dispositivo disegna e il golden
autorato sull'altro (§3.2). Quello scarto si misura **dentro il rettangolo del cancello**, ed è
quello che il test confronta davvero.

MISURATO:

| cancello | rettangolo | **scarto di renderer** | limite | **margine residuo** |
|---|---|---|---|---|
| **densità persone** | PAVEMENT su `people-single` | **0,1223%** | **0,1415%** | **0,0192 pt — il 13,6%** |
| conteggio auto giorno | banda strada | 0,2273% | 3,8041% | 94% |
| densità auto notte | banda strada | 0,0000% | 7,0058% | 100% |
| orario commerciale | banda facciate | 0,1068% | 1,0038% | 89% |

**Il cancello della densità persone ha consumato l'86% del suo margine prima che esista una
regressione da catturare.** Passa, per 0,0192 punti percentuali. È esattamente il rischio che il
prompt anticipava — «basta che l'antialiasing di un'altra Skia arrotondi diversamente qualche
pixel» — e che la voce 31 aveva classificato come plausibile ma non osservato. Adesso è osservato,
ed è concentrato su un cancello solo.

**Nessun limite è stato spostato e nessun golden è stato rigenerato.** Le tre strade — rigenerare
`people-single.png` qui, ri-derivare il cancello contro il pavimento efficace, o lasciarlo — sono
del coordinatore. È la voce di backlog **36**.

---

## 5. Fase 4 — la CPU su questa macchina

### 5.1 La risposta che conta, per prima

| | ms di CPU per frame | quota dell'intervallo di 33,3 ms |
|---|---|---|
| processo intero | **14,72 ms** | **44,2%** |
| `PaperScrapeGlTh` | **14,43 ms** | **43,3%** |

**Il margine c'è, ed è più della metà dell'intervallo.** Il costo per frame è circa 1,6× quello del
OnePlus, ma non si avvicina al limite. La conferma indipendente è il frame rate: **29,60 fps**
(sd 0,07, 198 campioni, letto da SurfaceFlinger sul layer del wallpaper) — la cadenza di 30 fps è
**tenuta**, cosa che non accadrebbe se il budget per frame fosse esaurito.

### 5.2 Il protocollo — riusato, non reinventato

Voce 32 e report di audit, invariati: delta di `utime + stime` da `/proc/<pid>/stat`, che è la
**somma su tutti i thread** del thread-group leader; `CLK_TCK = 100` **verificato sul dispositivo**;
clock da `/proc/uptime` (`date +%N` non esiste in toybox); nella ripartizione per thread il `comm`
è tagliato prima dell'`awk` (`sed 's/.*) //' | awk '{print $12+$13}'`) perché contiene spazi.
`/proc` resta leggibile con `debuggable = false`.

| condizione | valore — tutte DICHIARATE |
|---|---|
| build | **simil-release**: `initWith(release)`, R8 e shrink accesi, `isDebuggable = false`, firmata col **`debug.keystore` committato**, `applicationIdSuffix = ".debug"` |
| firma di release del maintainer | **mai letta, mai generata, mai toccata** — condizione H non scattata |
| installazione | APK **verificato byte per byte contro il `pull`** (`425f81ca…`) prima di misurare |
| tema | **Autumn** |
| ora della scena | orario reale del dispositivo (pomeriggio, giorno) |
| preferenze | quelle di default del pacchetto, dichiarate: non esistevano preferenze del maintainer per questo pacchetto |
| visibilità | wallpaper visibile sul launcher, nessuna app in primo piano |
| alimentazione / luminosità | collegato, `stay_on_while_plugged_in = 3`, luminosità manuale |
| riscaldamento | **180 s dichiarati** |
| finestra | **60 s**, ripetizioni consecutive, **n = 3** |
| pid | **verificato uguale prima e dopo ogni finestra** — mai ripiegato sul wallpaper statico |

### 5.3 I numeri, con la dispersione

**Visibile:**

| finestra | durata | tick | % di un core |
|---|---|---|---|
| 1 | 63,36 s | 2724 | 42,99% |
| 2 | 63,38 s | 2772 | 43,74% |
| 3 | 63,32 s | 2783 | 43,95% |

**Media 43,56% di un core, sd 0,50, n = 3.**

**Ripartizione per thread**, con il controllo che chiude:

| thread | % di un core |
|---|---|
| `PaperScrapeGlTh` | **42,71%** |
| `ged-swd` | 0,59% |
| `HeapTaskDaemon` | 0,25% |
| `Jit thread pool` | 0,01% (in pratica assente, com'è giusto senza `debuggable`) |
| **somma per thread** | **43,56%** — contro 43,56% di processo, **scarto 0,000 pt** |

**Nascosto (schermo spento):** 0,175% / 0,125% / 0,110% → **media 0,137% di un core, sd 0,034,
n = 3**. Il motore va in idle correttamente.

**Confronto col record storico del OnePlus 6T** (voce 33, stessa build simil-release, stesso
protocollo) — DICHIARATO come confronto, **NON ATTRIBUIBILE** quanto alla causa, perché il OnePlus
non è più interrogabile:

| | BV6600 | OnePlus 6T |
|---|---|---|
| processo, visibile | 43,56% | 27,72% |
| `PaperScrapeGlTh` | 42,71% | 24,42% |
| nascosto | 0,137% | 0,12% |

Che otto A53 in ordine costino più di quattro A75 è la spiegazione ovvia; è anche esattamente il
tipo di spiegazione comoda che questo pass non può verificare, quindi resta un confronto e non una
causa.

### 5.4 GPU busy — non conoscibile qui, e non stimata

La strada dell'Adreno non esiste su PowerVR. VERIFICATO sul dispositivo:

| strada | esito |
|---|---|
| `/sys/class/kgsl` | **non esiste** |
| tracepoint `kgsl/*` | **non esistono** |
| `/proc/mtk_gpu_utilization` | non esiste |
| `/sys/kernel/ged/hal/gpu_utilization` | non esiste |
| `/sys/kernel/debug/ged/gpu_utilization` | **Permission denied** (root-only) |
| `/sys/module/ged/parameters/gpu_loading` | **Permission denied** (root-only) |
| tracepoint grafici registrati | solo `pvr_fence` — traccia fence, **non** occupazione |

**Non è misurabile su questo dispositivo senza root, e non è stata stimata.** Ricavarla dalle fence
sarebbe una stima, non una misura.

---

## 6. Fase 5 — cosa resta da guardare, e non da correggere

Registrato senza intervenire. **Nessuna correzione proposta**: il giudizio è del maintainer, che
guarderà le catture.

### 6.1 Come si compone la scena su 18:9 a 282 PPI

- **La banda di cielo vuota è la cosa che cambia di più.** La scena mette il contenuto nel ~40%
  inferiore; sopra resta un terzo abbondante di cielo quasi vuoto. Su 18:9 la proporzione è meno
  allungata del 19,5:9, ma la banda vuota resta la parte più grande dell'immagine.
- **Sotto la strada c'è una fascia di terreno piatto** che occupa gli ultimi ~10% e non contiene
  nulla.
- **Le nuvole sono tagliate ai bordi** sinistro e destro.
- **Tutto è fisicamente più grande.** 282 PPI contro 402: a parità di pixel un oggetto è circa
  1,4× più grande sulla retina. I volti degli occupanti e le finestre si leggono meglio; in cambio
  la struttura a pixel dell'artwork è più grossolana.
- Le catture: `f1_03_home_wallpaper.png` (giorno), `f5_notte_autumn.png` (notte),
  `f5_inverno_giorno.png` (Winter).

### 6.2 UI Material 3 su uno schermo più stretto e meno denso

- **Un troncamento vero:** nel gruppo a segmenti *Location* di «Weather & time», l'opzione
  **`Netwo…`** è tagliata. Quattro segmenti non entrano in 720 px. `catture/f5_weather_1.png`.
- **Il foglio dei temi taglia le etichette della seconda riga:** «Winter» e «Desert» sono coperti
  dal bordo inferiore del foglio. `catture/f5_sheet_fresh.png`.
- **Diversi titoli vanno a due righe** dove sul pannello vecchio ne bastava una: «Automatic theme
  by date», i sottotitoli di «World & scene» e «Advanced & about». È comportamento corretto di
  Material 3, non un difetto; è segnalato solo perché cambia il ritmo verticale della schermata.
- Nient'altro si rompe: nessuna sovrapposizione, nessun controllo irraggiungibile, nessun testo
  illeggibile.

---

## 7. Aggiornamento del backlog

| voce | dov'è | cosa è stato fatto |
|---|---|---|
| **20** | `BACKLOG_v4_21.md` | **CHIUSA con un'osservazione.** Il secondo ambiente è arrivato — PowerVR invece dell'emulatore — e i tre golden GL passano. Debito **pagato**. |
| **27** | `BACKLOG_v4_21.md` | Marcata **RECORD STORICO DEL ONEPLUS 6T**, con il rimando ai numeri correnti. |
| **31** | `BACKLOG_v4_22.md` | **RISOLTA** col pavimento rimisurato (0,0000% su tre esecuzioni, riavvio di processo e di telefono) e i quattro segnali riprodotti; rinvia alla 36 per ciò che la derivazione non aveva previsto. |
| **32**, **33** | `BACKLOG_v4_22.md` | Marcate **RECORD STORICO DEL ONEPLUS 6T**: il metodo si trasferisce, i numeri no. |
| **34** | nuova | La baseline del BV6600: hardware, render osservato, offset, CPU, ms/frame, GPU non misurabile. |
| **35** | nuova | Il fallimento **NON ATTRIBUIBILE** di `VehicleOccupantScaleTest`, con tutto ciò che è stato escluso. |
| **36** | nuova | Il margine dell'86% consumato sul cancello delle persone. Nessun limite spostato. |

**Cosa non è più conoscibile, detto esplicitamente:** con il OnePlus 6T non più accessibile,
nessuna differenza fra i due dispositivi è attribuibile per A/B. Il rapporto 1,6× sulla CPU, la
forma esatta dello scarto di renderer, e il fatto che `VehicleOccupantScaleTest` passasse davvero
sull'altro dispositivo con questo stesso albero: sono tutti **non più verificabili**. Dove servivano
sono stati trattati come confronti dichiarati, mai come cause dimostrate.

---

## 8. Contabilità, con l'aritmetica scritta

| | prima | dopo | aritmetica |
|---|---|---|---|
| test JVM | 1331 | **1331** | 1331 + 0 = 1331 — nessun test aggiunto o rimosso |
| test strumentati | 148 | **148** | 148 + 0 = 148 |
| test Python | 108 | **108** | 108 + 0 = 108 |
| PNG golden committati | 27 | **27** | 25 Canvas + 3 GL = 28 asserzioni su **27** PNG (`day.png` è asserito due volte, con foci diversi) |
| `@Ignore` | 0 | **0** | |
| voci d'archivio | 882 | **883** | 882 + 1 (questo report) − 0 = **883** |
| `versionCode` / `versionName` | 53 / 4.22 | **53 / 4.22** | invariati |

---

## 9. Lista intera dei file toccati — dal diff dei due archivi

Derivata confrontando l'archivio consegnato con `PaperScrape_v4_22.zip` (`a8b94b79…`), **non** da
`git status`.

**Aggiunti (1):**

- `V4_22_MIGRAZIONE_BV6600_REPORT.md` — questo report.

**Modificati (3):**

- `BACKLOG_v4_22.md` — nota di migrazione in testa; tabella di riepilogo; voce 31 riscritta; voci
  32 e 33 marcate come record storici; voci **34**, **35**, **36** nuove.
- `BACKLOG_v4_21.md` — voce 20 chiusa, voce 27 marcata record storico.
- `CLAUDE.md` — blocco in testa al §7 col dispositivo nuovo e i numeri che sostituiscono i vecchi;
  conferma sul secondo driver nel punto sui golden GL.

**Rimossi (0).**

**Nessun file di codice spedito è stato modificato.** Le due modifiche locali usate per misurare
sono state rimosse e verificate assenti:

- il build type `perf` in `app/build.gradle.kts` (simil-release, firmato col `debug.keystore`
  committato) — **rimosso**;
- la sonda `OFFSETPROBE` in `PaperWallpaperService.kt` e la diagnostica in
  `VehicleOccupantScaleTest.kt` / `SceneGolden.kt` — **rimosse**, sorgenti verificati
  byte-identici allo ZIP di partenza.

L'albero consegnato è stato verificato file per file contro lo ZIP di partenza: **0 file modificati
oltre ai tre elencati, 0 mancanti, 0 in più.**

---

## 10. Stato del dispositivo alla consegna

Lo stato originale era stato annotato **prima** di cambiare qualsiasi cosa
(`device_original/STATO_ORIGINALE.md`).

**Una cosa importante trovata all'arrivo:** sul dispositivo era **già installata** la release
`com.paperscrape.livewallpaper` 4.22/53, **firmata con la chiave di release del maintainer**
(cert SHA-256 `cf250464…`, diversa dal `debug.keystore` committato `79374f07…`), e **già attiva
come wallpaper**. Non è stata disinstallata, sovrascritta né toccata: la build di questo pass porta
`applicationIdSuffix = ".debug"` ed è quindi un pacchetto separato.

| | originale | alla consegna |
|---|---|---|
| pacchetti PaperScrape | `com.paperscrape.livewallpaper` (release del maintainer) | **identico — solo quello** |
| wallpaper attivo | `com.paperscrape.livewallpaper/…PaperWallpaperService` | **ripristinato, e OSSERVATO mentre rende** (`catture/rip_finale_home.png`) |
| `system.screen_brightness_mode` | 1 | **1** |
| `system.screen_off_timeout` | 60000 | **60000** |
| `global.stay_on_while_plugged_in` | 0 | **0** |
| `global.adb_enabled` | 1 | **1** |
| `global.development_settings_enabled` | 1 | **1** |
| `secure.ui_night_mode` | null | **null** |

- Il pacchetto `com.paperscrape.livewallpaper.debug` e quello di test sono stati **disinstallati**.
- `/data/local/tmp` ripulito dagli script e dai file temporanei di misura.
- `system.screen_brightness` legge **130** invece di 112: la luminosità automatica (`mode = 1`,
  come in origine) riscrive quel valore da sé appena riattivata. Il valore era gestito
  automaticamente anche prima; è detto perché la tabella non tornerebbe altrimenti.
- `global.auto_time` è stato rimesso a **1**. **Va detto che il suo valore originale non era stato
  annotato** prima di modificarlo: 1 è il default di Android e l'orologio del dispositivo è
  corretto, ma questa è l'unica voce di stato che non posso dichiarare ripristinata *al valore
  osservato*, solo *al default*.

---

## 11. Condizioni d'arresto

| | esito |
|---|---|
| **D** — baseline non combacia | **non scattata** — tutto combacia (§1) |
| **E** — il wallpaper non rende | **non scattata** — rende, osservato e catturato (§2.1) |
| **F** — differenza non attribuibile | **SCATTATA** su `VehicleOccupantScaleTest.noOccupantPixelLeavesTheGlass`. Riportata, niente cambiato (§3.1) |
| **G** — golden rigenerato / cancello spostato / codice spedito modificato | **non scattata** — nessuna delle tre |
| **H** — simil-release non producibile senza la firma del maintainer | **non scattata** — prodotta col `debug.keystore` committato (§5.2) |
| **I** — regola di arresto generale | **non scattata**, ma due misure sbagliate sono state buttate e rifatte invece di essere aggiustate: gli offset (§2.2) e la prima esecuzione JVM (§1) |

---

## 12. Artefatto

| | |
|---|---|
| file | `PaperScrape_v4_22.zip` |
| voci | **883** = 882 + 1 aggiunto, 0 rimossi, 3 modificati |
| sostituisce | `a8b94b79cf69ba5a2f62ed17e0158f65a6ae0695c308c25b93762b5f3c4fcb5c`, 5 745 260 byte, 882 voci |

**SHA-256 e dimensione dell'archivio sono nella nota di consegna, non qui**, per la ragione ovvia:
questo file sta *dentro* l'archivio e non può contenere l'impronta di ciò che lo contiene. Una
copia del report è consegnata accanto allo ZIP, e lì la riga c'è.

`versionCode 53` / `versionName "4.22"` invariati.

**Etichetta della consegna: documentazione e baseline, non una release.** Pubblicazione, tag e
release restano al maintainer e non sono state fatte.
