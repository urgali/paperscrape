# BACKLOG_v4_22.md — what v4.22 decided, and what it left open

**Replaces `BACKLOG_v4_21.md`.** That file's resolved and documented items are settled and not
restated; what it left open is carried forward below unchanged (items 18, 20, 25, 30) or closed
here with its outcome (item 29). Numbering continues from it: items 31, 32 and 33 are new to this release.

> **Nota di migrazione (2026-09-05, pass di baseline sul dispositivo nuovo).** Il dispositivo di
> riferimento del progetto è cambiato: il OnePlus 6T (Adreno 630, Android 15) **non è più
> accessibile** e la macchina su cui si misura ora è un **Blackview BV6600** (PowerVR GE8320,
> Android 10, 720×1440). Ogni numero di prestazione scritto prima di questa data è un **record
> storico del OnePlus 6T**, non lo stato corrente: valgono per le voci **27** (in
> `BACKLOG_v4_21.md`), **32** e **33**. Le voci **20** e **31** sono state riaperte e chiuse con
> ciò che quel pass ha misurato. Le voci **34**, **35** e **36** sono nuove. Il pass non ha
> toccato codice spedito, non ha rigenerato golden e non ha spostato cancelli.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED**
(decided against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not
rediscovered), or **OPEN** (left undone on purpose, with what closing it would take).

---

## Summary

| item | what | outcome |
|---|---|---|
| 18 | An unreadable custom theme loses the whole store | **OPEN**, carried forward from v4.20 unchanged |
| 20 | The GL goldens' second environment was never observed | **RESOLVED (2026-09-05)** — osservata su un secondo renderer, PowerVR GE8320: i tre golden GL passano, scarto 0,00% / 0,01% / 0,24% contro un cancello del 3% |
| 25 | The palm is the odd tree out | **OPEN**, carried forward from v4.21 unchanged — an artistic pass, not a defect |
| 29 | The pavement focus does not catch a change in how many people there are | **RESOLVED** — gates derived for all four v4.22 regressions, each between its measured floor and its measured weakest signal |
| 30 | Hand-maintained counts still in the current-state documents | **OPEN**, carried forward unchanged — one at a time, each with its measurement |
| 31 | Every v4.22 gate's floor is an observation on one device | **RESOLVED (2026-09-05)** — pavimento rimisurato sul BV6600: 0,0000% su tre esecuzioni con riavvio di processo e di telefono. **Ma vedi la voce 36**: il margine del cancello persone è ora 0,0192 pt |
| 32 | Il 67–68% della voce 27 è il thread di rendering, non il processo | **DOCUMENTED — RECORD STORICO DEL ONEPLUS 6T.** I numeri restano validi come misura di quel dispositivo; per lo stato corrente vedi la voce 34 |
| 33 | Audit dello spreco sul percorso GL: la quantità con una definizione intrinseca misura **zero** | **DOCUMENTED — RECORD STORICO DEL ONEPLUS 6T.** Il metodo (build simil-release, protocollo CPU) si trasferisce; i numeri no. Vedi la voce 34 |
| 34 | La baseline del dispositivo nuovo (Blackview BV6600) | **DOCUMENTED** — wallpaper reso e osservato, tre suite eseguite, CPU e ms/frame misurati |
| 35 | `VehicleOccupantScaleTest.noOccupantPixelLeavesTheGlass` fallisce sul BV6600 | **RESOLVED (2026-09-05, secondo pass)** — diagnosi confermata da un esperimento: estratto il calcolo del riquadro in `glassPane()`, le locali passano da 0.0 a −16.0/9.0 e il test è verde. Strumentati **148/148** |
| 36 | Il cancello della densità persone ha consumato l'86% del suo margine | **RESOLVED (2026-09-05, secondo pass)** — 24 golden Canvas rigenerati sul dispositivo che esegue il test, dopo aver classificato tutte e 24 le differenze come renderer. Pavimento efficace **0,0000%** su ogni focus: margine **100%**. **Nessun limite spostato** |

---

## 18, 20, 25, 30 — carried forward

Unchanged from `BACKLOG_v4_21.md`; see that file for the full accounts. Nothing in v4.22 touched
the custom-theme store's error path (18), the GL goldens' emulator side (20), the palm (25), or
the undated counts (30).

> **Aggiornamento 2026-09-05: la voce 20 non è più aperta.** Chiedeva che un secondo ambiente
> confermasse i tre golden GL, e nominava l'emulatore perché era l'unico secondo ambiente
> immaginato. Il secondo ambiente è arrivato da un'altra parte — una **PowerVR GE8320** al posto
> dell'Adreno 630 — e i tre golden **passano**: `GlDriverGapGuardTest` misura uno scarto di
> spostamento dei bordi di **day 0,00%, lake-busy 0,01%, thunderstorm 0,24%** contro un cancello
> del **3,00%**, cioè meno dello 0,92–1,18% caratterizzato fra Adreno ed emulatore. Il debito è
> **pagato, non cancellato**: i golden GL reggono un cambio di vendor della GPU. Vedi la voce 34.

## 29 — the pavement focus, and every new setting of this release, now has a gate

**RESOLVED, with the derivation the item asked for.** Closing it meant choosing a metric, and the
metric chosen is the one the item's own text prescribes: for each regression that must fail,
measure it, measure the noise floor on the same rectangle, and put the gate midway, with both
numbers written beside it.

The floor, first (v4.22 Fase 1 — MISURATO on the OnePlus 6T): frames of the same scene rendered
by separate instrumented executions, across a process restart and a device reboot, are
**byte-identical** — 0.0000% on the pavement band, on the road band, and on the whole frame. The
Canvas golden pipeline on this device is bit-deterministic, so every gate's floor is zero and the
gate is half its weakest signal.

The gates (all in `SettingsGates`, asserted as extra or scene foci with derived per-focus limits
— `GoldenFocus.maxDifferingFraction`, default unchanged for every pre-existing focus — and
re-measured on every run by the two `…StandsBetweenFloorAndSignal` tests):

| regression | frame | rectangle | weakest signal | gate |
|---|---|---|---|---|
| people density ignored | `people-single` | `PAVEMENT` | (misurato — vedi report v4.22) | metà del segnale |
| day car count ignored | `traffic-day-sparse` (new) | road band | (misurato) | metà |
| night car density ignored | `traffic-night-quiet` (new) | road band | (misurato) | metà |
| business hours ignored | `shops-closed-night` (new) | facades band | (misurato) | metà |

The three new frames exist because no committed frame exercised any non-default value of these
settings — which is also why "no golden moved" was the honest phase-2 verdict. Each new scene's
inputs are derived, not habitual, and `GoldenUniquenessTest` (active, no allowlist) is the proof
that each new PNG carries information none of the others already carried.

## 31 — the gates' floor is one device's observation

**RESOLVED (2026-09-05), con la misura che la voce chiedeva — e con una scoperta che la voce non
prevedeva.**

Quello che la voce chiedeva: *«one run of the golden capture on a second environment,
byte-comparing PNGs across two executions there»*. Fatto, sul Blackview BV6600 (PowerVR GE8320,
Android 10). **MISURATO:** 24 scene catturate in **tre** esecuzioni separate — la seconda dopo
riavvio del processo, la terza dopo riavvio del telefono — sono risultate **byte-identiche fra
loro, 0 PNG diversi su 24**. Il pavimento di rumore è **0,0000%** anche qui. La pipeline Canvas è
bit-deterministica su due Skia e due GPU di vendor diversi, e il pavimento sotto ogni cancello non
è più l'osservazione di un dispositivo solo.

I quattro segnali, rimisurati dai test di derivazione che li rimisurano a ogni esecuzione:

| cancello | segnale OnePlus 6T | segnale BV6600 | limite |
|---|---|---|---|
| densità persone (il più debole: tutti nascosti) | 0,283% | **0,2829%** | 0,1415% |
| conteggio auto di giorno | 7,6082% | **7,6263%** | 3,8041% |
| densità auto notturna | 14,0115% | **14,0079%** | 7,0058% |
| orario commerciale | 2,0075% | **2,0053%** | 1,0038% |

I segnali si riproducono alla quarta cifra. **Nessun limite è stato spostato.**

**Ciò che la voce non prevedeva, e che è il vero lascito di questo pass.** Il pavimento di *rumore*
è zero, ma non è l'unico numero che consuma il budget di un cancello. Un frame reso sul BV6600 non
è byte-identico al golden autorato sul OnePlus: **nessuna** delle 24 scene lo è. Le differenze sono
di renderer — 31–42% dei pixel differiscono di 1–8 livelli (il dithering del gradiente del cielo) e
solo 0,007–0,099% superano la tolleranza di 8 del progetto, sparsi come granelli isolati sui bordi.
Tutte e 25 le asserzioni Canvas passano. Ma quello scarto costante **si somma** al pavimento di
rumore dentro il rettangolo di ogni cancello, ed è la voce **36**. Vedi anche la voce **34**.

## 32 — Il 67–68% della voce 27 è il thread di rendering, non il processo

> **RECORD STORICO DEL ONEPLUS 6T — non è lo stato corrente (marcato 2026-09-05).** Ogni numero di
> questa voce è stato misurato sul OnePlus 6T (Snapdragon 845, Adreno 630, Android 15), che non è
> più accessibile. Il **metodo** resta valido e va riusato; i **numeri** non trasferiscono al
> dispositivo corrente. Per lo stato corrente vedi la voce **34**.

**DOCUMENTED.** Pass di sola misura (2026-09-04/05): nessuna riga di codice spedito toccata,
nessun golden, nessun bump di versione. Il numero della voce 27 era **DICHIARATO**, mai riprodotto
in modo indipendente; questo pass lo ha ripreso con un protocollo scritto. Dettaglio completo,
protocollo e dispersioni in `V4_22_MISURA_CPU_REPORT.md`.

**Il protocollo (l'unica parte che rende il numero riutilizzabile).** Build **di debug** costruita
dallo ZIP consegnato e installata dopo verifica byte-per-byte contro il `pull`; tema **Autumn**;
scena notturna a ora reale; wallpaper visibile sul launcher; modalità aereo; luminosità fissata;
pacchetto di test disinstallato; nessun'altra app in foreground. La CPU è letta come delta di
`utime+stime` da `/proc/<pid>/stat` su finestre di 60 s, cioè **sommata su tutti i thread**, e
riportata come percentuale di **un** core (il device ne ha 8). Riscaldamento dichiarato di 180 s
prima di ogni blocco.

**I numeri — MISURATO.**

| condizione | n | media | min | max | escursione | sd |
|---|---|---|---|---|---|---|
| v4.22, visibile | 18 | **108,45%** di un core | 105,54 | 109,99 | 4,45 pt | 1,11 |
| v4.21, visibile, stesso protocollo | 10 | **109,00%** | 106,57 | 110,57 | 4,00 pt | 1,13 |
| v4.22, **nascosto** (schermo spento) | 3 | **0,12%** | 0,08 | 0,17 | 0,09 pt | 0,05 |

**L'attribuzione, che è il punto.** Il 67–68% non è il costo del processo: è il costo del **solo
thread di rendering**. Ripartizione per thread sulla v4.22, tre finestre da 60 s, letta da
`/proc/<pid>/task/*/stat`:

| thread | % di un core |
|---|---|
| `PaperScrapeGlTh` (il loop di disegno) | **67,50%** |
| `Jit thread pool` | 37,22% |
| `Profile Saver` | 1,29% |
| binder + resto | ~1,85% |
| **processo intero** (`/proc/<pid>/stat`) | **107,86%** |

La somma sui thread vivi torna al totale di processo entro **0,08 pt**. `PaperScrapeGlTh` a
**67,50%** coincide con il 67–68% della voce 27: quel numero è **riprodotto**, ma misura il thread
di disegno, non il processo, e va letto così.

**Cosa diventa MISURATO e cosa resta DICHIARATO.**

- Il **67–68%** è ora **MISURATO**, e si riferisce al **thread di rendering di una build di debug**
  — misurato a 67,50% sulla v4.22 e indistinguibile sulla v4.21. Come costo *del processo* non è
  mai stato corretto: quello è ~108%.
- I **3–4 punti fra v4.20 e v4.21** restano **DICHIARATO**: chiuderli vorrebbe dire costruire anche
  la v4.20 e misurarla con questo protocollo, e questo pass non l'ha fatto.
- **v4.22 contro v4.21: −0,55 pt, cioè 0,49 sd.** Le due valutazioni per frame aggiunte dalla v4.22
  (orario commerciale e target del conteggio auto) **non hanno un costo misurabile** con questo
  protocollo. MISURATO.

**Il numero della build di debug non è il numero della release, e la release non è misurabile qui.**
`assembleRelease` prende la firma solo da variabili d'ambiente che il progetto non fornisce di
proposito, e senza firma l'APK non si installa: una v4.22 di release **non è costruibile su questa
macchina**, e non è stata aggirata. Il `Jit thread pool` a 37,22% è esattamente l'artefatto di debug
che `CLAUDE.md` §7 già segnalava; ogni conclusione tratta da questi numeri è **conservativa**.

**Il nascosto è la cifra che conta, ed è zero.** A schermo spento il processo scende a **0,12% di un
core**: il motore smette davvero di disegnare. Il costo esiste solo mentre il wallpaper è visibile.

**La Fase 2 (costo in batteria) è stata tolta dal perimetro dal coordinatore — non è un impedimento
tecnico.** La ragione, che è sua e va riportata così: il progetto ha **un solo dispositivo, con cella
invecchiata**. Un delta in mA fra due bracci resterebbe difendibile — l'invecchiamento colpisce
capacità e resistenza interna, non i mA assorbiti dal SoC — ma le due traduzioni che lo rendevano
giudicabile, **percentuale di una carica** e **minuti di schermo persi**, non lo sono più, perché la
capacità nominale non è quella. Senza quelle due resterebbe un numero in mA non più interpretabile
del 67% da cui si era partiti. **La misura non è impossibile: è inutile allo scopo.** Di conseguenza
la Fase 3 (dove va il tempo per frame) non è stata aperta: l'indagine prosegue con un perimetro
diverso, in una sessione nuova.

**Candidate di ottimizzazione incontrate e non applicate** — elencate perché il pass le ha
attraversate, non perché servano; nessuna è stata misurata e nessuna è raccomandata qui:
`FRAME_INTERVAL_MS = 33` in `GlRenderThread` e `PaperWallpaperService` (portarlo a 50 ms taglia il
lavoro per frame di circa un terzo, ma è un giudizio dell'occhio sul moto di auto e nuvole, e la
scelta è del maintainer guardando il telefono); e una cache dei livelli statici (cielo, colline,
edifici, alberi sono ridisegnati a ogni frame benché cambino solo con `dayBlend`) — che è una
modifica al draw path, tocca ogni golden, ed è la forma di «un refactor grande per un difetto
piccolo» che il progetto tiene fra i segnali d'allarme.


## 33 — Audit dello spreco sul percorso GL: zero frame identici, e il costo vero è un terzo di quello creduto

> **RECORD STORICO DEL ONEPLUS 6T — non è lo stato corrente (marcato 2026-09-05).** Ogni numero di
> questa voce è stato misurato sul OnePlus 6T (Snapdragon 845, Adreno 630, Android 15), che non è
> più accessibile. Il **metodo** resta valido e va riusato; i **numeri** non trasferiscono al
> dispositivo corrente. Per lo stato corrente vedi la voce **34**.

**DOCUMENTED.** Pass di sola misura (2026-09-05): nessuna riga di codice spedito toccata, nessun
golden, nessuna rimozione applicata, nessun bump di versione. Protocollo, numeri e dispersioni in
`V4_22_AUDIT_SPRECO_GL_REPORT.md`. **La Fase 2 non è stata aperta**, sia per il checkpoint previsto
sia perché è scattata la condizione B (sotto).

**La build su cui si misura, che era il vincolo dichiarato del pass.** È stata prodotta una build
**simil-release** — R8 acceso, `isShrinkResources`, `debuggable = false`, stessi `proguardFiles` —
firmata con il **`debug.keystore` committato** e con l'`applicationIdSuffix = ".debug"`, così da
installarsi sopra la build di debug senza poter collidere con la release 4.15 del maintainer. **La
configurazione di firma di release non è stata letta, generata né modificata**, e le variabili
d'ambiente `PAPERSCRAPE_RELEASE_*` sono rimaste vuote. Il build type è uno strumento locale e **non
è nello ZIP**.

**Il risultato che cambia la domanda** — stessa scena, stesse condizioni, senza strumentazione,
sulla configurazione del maintainer (Autumn, ora reale, giorno). MISURATO:

| | debug | simil-release | rapporto |
|---|---|---|---|
| processo intero | **102,69%** di un core (n=3, sd 0,66) | **27,72%** (n=3, sd 0,23) | 3,70× |
| `PaperScrapeGlTh` | **64,06%** | **24,42%** | 2,62× |
| `Jit thread pool` | 33,61% | **assente** | — |

Il «67–68%» ha quindi **due** qualificatori, non uno: è il **thread di rendering** (voce 32) **di
una build di debug** (questa voce). Sul telefono dell'utente quel thread sta a **24,42% di un core
su otto**.

**Le cinque quantità, due scene, build simil-release.** Scena A: Autumn, ora fissa 12:00, auto 100%,
persone 100%. Scena B: Autumn, ora fissa 1:00, auto e persone spente dalle spunte. Scorrimento 15%
(valore del maintainer) in entrambe. Finestre di 60 s, riscaldamento dichiarato di 180 s.

| # | quantità | A affollata | B quieta |
|---|---|---|---|
| 1 | **frame identici al precedente** | **0,0000%** (0 su 8 700) | **0,0000%** (0 su 8 700) |
| 2 | fattore di overdraw | 2,525× (sd 0,045) | 2,369× (sd 0,048) |
| 3 | draw call / texture distinte | 24,92 / 12,03 = **2,07×** | 69,09 / 5,00 = **13,82×** |
| 4 | GPU busy | 20,87% (sd 0,82) | 19,92% (sd 0,58) |
| 5 | CPU per frame, sommata sui thread | **10,47 ms** | **8,34 ms** |

Valori in debug accanto, per il confronto con la voce 32: processo 100,66% e 102,81%; le quantità
1, 2 e 3 coincidono con quelle della build simil-release entro la dispersione — sono conteggi del
flusso di vertici e non dipendono dalla build, il che è **misurato**, non dedotto.

**Quantità 1: zero, e la leva più ovvia è morta.** Nessun frame identico al precedente su **19 200**
misurati, in tre configurazioni — comprese la scena quieta e una **variante supplementare con lo
scorrimento a 0%** (0 su 1 800). Il flag di stato della scena che salta i frame identici non
avrebbe niente da saltare: nuvole, stelle, uccelli, foglie e occupanti delle finestre sono funzioni
continue del clock. **Costo > 0, guadagno = 0.**

**Quantità 2: il numero non è tutto spreco, e non va letto come tale.** 2,4–2,5 passaggi per pixel
sono ciò che si misura; *quanta* parte finisca sotto uno strato opaco successivo richiede
un'analisi per pixel che questo pass non ha fatto. In ogni caso il riempimento non è il vincolo: la
GPU sta al 20% in entrambe le scene.

**Quantità 3: il divario più grande, e il pavimento non è gratis.** L'atlante funziona (5 texture
distinte di notte, ~12 di giorno; **zero upload** a regime): il divario viene dall'ordine di disegno,
che **è** l'ordine di profondità e non è riordinabile senza cambiare il quadro dove gli sprite si
sovrappongono. Il dato più utile per un eventuale seguito è che di notte le draw call **triplicano**
(69 contro 25) mentre le texture distinte **si dimezzano** (5 contro 12): qualcosa nel percorso
notturno alterna cinque texture decine di volte per frame. **Individuarlo è un contatore per
chiamante, cioè un'altra misura, non una modifica.**

**Overhead della strumentazione, dichiarato.** +4,12 pt nella scena affollata (+13,3%) e +10,88 pt
nella quieta (+44,0%). Per questo **le quantità 4 e 5 sono riportate dalla build senza sonda**,
mentre 1, 2 e 3 sono conteggi che la sonda non altera. La cadenza non cambia (29,61 e 29,62 fps in
tutte le build): il loop è pacato a 33 ms e il costo per frame resta molto sotto il budget.
**Condizione C non scattata**, e il perché è scritto nel report.

**Condizione B: SCATTATA, ed è il blocco dichiarato della Fase 2.** I tre golden GL portano tutti
`warmUpFrames = 0` e disegnano **un** fotogramma con `deltaSeconds = 0`, tutti di giorno
(`dayBlend = 1`) e tutti sul tema `sunset`. Ne segue che **nessun veicolo è mai entrato in un
fotogramma golden GL** (un'auto parte con `progress` negativo e avanza solo dentro `update`), che la
notte non è coperta per niente, e che **Autumn** — il tema del maintainer, con foglie cadenti e
zucche — non compare. Ma il punto decisivo è strutturale: **le due leve che restano in gioco (cache
dei livelli statici, e qualunque flag inter-frame) sono modifiche a ciò che accade *fra* due
fotogrammi, e un golden da un fotogramma passa identico che la cache sia corretta, stantia o mai
invalidata.** Non è una tolleranza larga: è una domanda che quel test non pone.

Chiuderla: **due scene GL nuove**, una giorno-con-traffico e una notte-con-traffico, entrambe con
`warmUpFrames > 0` — che è già supportato (`GlGolden.render` onora `scene.warmUpFrames`, e i golden
Canvas del traffico usano `TRAFFIC_WARM_UP_FRAMES = 390`). Coprirebbero insieme il multi-fotogramma,
il ramo `dayBlend → 0` e i veicoli. **Erediterebbero però il debito della voce 20** (un solo
dispositivo, una sola esecuzione) e la voce 31 dice lo stesso dei pavimenti dei cancelli: estendere
la copertura **sposta** il limite, non lo elimina.

**Una nota di pulizia trovata per strada, non applicata.** `SceneCanvas.drawArc` non ha **nessun
chiamante** in `app/src/main`: l'arcobaleno che lo usava è diventato uno sprite, e l'implementazione
resta in entrambi i backend. Non costa nulla per frame — è codice morto, non spreco a runtime.

**Che cosa questo pass NON ha prezzato, e va detto perché è la leva rimasta.** La cache dei livelli
statici. La quantità 1 misura l'identità del **fotogramma intero**, non quella di una sua parte:
lo zero misurato **non dice nulla** su quanto costi ridisegnare cielo, colline, edifici e alberi a
ogni frame benché cambino solo con `dayBlend`. Prezzarla richiede un contatore per livello; renderla
sicura richiede prima la copertura qui sopra.


---

## 34 — La baseline del dispositivo nuovo: Blackview BV6600

**DOCUMENTED (2026-09-05).** Il dispositivo di riferimento è cambiato e questa voce è ciò che di
lui si sa. Il OnePlus 6T non è più accessibile, quindi **nessuna differenza fra i due è
attribuibile per A/B**: dove serviva un confronto, è stato fatto per ispezione e detto.

| | vecchio (non più accessibile) | corrente |
|---|---|---|
| modello | OnePlus 6T `ONEPLUS A6013` | **Blackview BV6600** |
| SoC | Snapdragon 845 | **MediaTek Helio A25 (mt6765)**, 8× Cortex-A53, 4×1,801 GHz + 4×1,500 GHz |
| GPU | Adreno 630 | **PowerVR GE8320** |
| schermo | 1080×2340, ~402 PPI | **720×1440, densità 320, 268,9×247,1 dpi reali** |
| Android | 15 | **10** (SDK 29) |
| launcher | — | `com.blackview.launcher/com.android.searchlauncher.SearchLauncher` |

**Il wallpaper rende — OSSERVATO, non dedotto** (condizione E non scattata). L'APK simil-release
costruito dallo ZIP si installa su Android 10 (`minSdk 26`), si avvia, e la scena completa —
cielo, sole, nuvole, uccelli, colline, edifici, alberi, persone, traffico — rende sul launcher.
Catture di giorno, di notte e col tema Winter.

**Gli offset di scorrimento: questo launcher non ne manda affatto — MISURATO con una sonda
locale.** `onOffsetsChanged` **non viene mai chiamato** dal launcher Blackview, su decine di swipe
in entrambe le direzioni. Il controllo positivo è che la sonda funziona: il *picker* del wallpaper
la chiama esattamente una volta, con `xOffset=0.0 xOffsetStep=0.0`. Il motivo di fondo è che
**questo launcher ha una sola pagina home** (più il pannello Google Discover a sinistra): cinque
swipe in avanti lasciano l'icona dov'era. Il movimento orizzontale che si osserva sul telefono non
viene dallo swipe ma dall'accumulatore di `scrollProgress`: **MISURATO 0,73–0,80 px/s** sulla banda
degli edifici e **0,20 px/s** sulle colline di sfondo, lineare su 30 s. È lo stesso comportamento
qualitativo del telefono vecchio, dove pure il launcher non mandava offset.

**CPU — protocollo della voce 32 riusato invariato** (delta di `utime+stime` da `/proc/<pid>/stat`
sommato su tutti i thread, `CLK_TCK`=100 verificato, clock da `/proc/uptime`, comm tagliato prima
dell'`awk`). Build **simil-release** (`initWith(release)`, R8 acceso, `debuggable=false`, firmata
col `debug.keystore` committato, `applicationIdSuffix=".debug"` — mai committata, mai nello ZIP).
Tema **Autumn**, wallpaper visibile sul launcher, alimentato, luminosità manuale, riscaldamento
**180 s dichiarato**, finestre da **60 s**, n=3.

| | BV6600 (corrente) | OnePlus 6T (storico, voce 33) |
|---|---|---|
| processo, visibile | **43,56%** di un core (sd 0,50, n=3) | 27,72% |
| `PaperScrapeGlTh` | **42,71%** | 24,42% |
| `ged-swd` | 0,59% | — |
| `HeapTaskDaemon` | 0,25% | — |
| `Jit thread pool` | 0,01% (assente in pratica) | assente |
| processo, **nascosto** (schermo spento) | **0,137%** (sd 0,034, n=3) | 0,12% |

La somma per thread riconcilia col totale di processo a **0,000 pt**.

**La domanda che conta — i ms di CPU per frame contro l'intervallo di 33 ms.** Il frame rate è
stato letto da SurfaceFlinger sul layer del wallpaper: **29,60 fps** (sd 0,07, 198 campioni), cioè
la cadenza di 30 fps è **tenuta**. Da lì:

| | ms di CPU per frame | quota dell'intervallo di 33,3 ms |
|---|---|---|
| processo intero | **14,72 ms** | **44,2%** |
| `PaperScrapeGlTh` | **14,43 ms** | **43,3%** |

**Il margine c'è ancora, ed è più della metà.** Il costo per frame è circa 1,6× quello del
OnePlus, ma resta sotto la metà dell'intervallo, e il fatto che il frame rate misurato stia a 29,60
di 30 lo conferma per una via indipendente dal conteggio della CPU.

**GPU busy: non misurabile su questo dispositivo, e non è stata stimata.** I tracepoint `kgsl`
usati sull'Adreno **non esistono** su PowerVR. I nodi MediaTek equivalenti esistono ma sono
**root-only** sotto policy enforcing: `/sys/kernel/debug/ged/gpu_utilization` e
`/sys/module/ged/parameters/gpu_loading` danno *Permission denied*; `/proc/mtk_gpu_utilization` e
`/sys/kernel/ged/hal/gpu_utilization` non esistono. L'unico tracepoint grafico registrato è
`pvr_fence`, che traccia fence e non occupazione. **Quel numero non è conoscibile qui senza root.**

---

## 35 — `VehicleOccupantScaleTest.noOccupantPixelLeavesTheGlass` fallisce sul BV6600

**RESOLVED (2026-09-05, secondo pass) — con la diagnosi, non con un aggiustamento.**

Il pass precedente lasciò questa voce **NON ATTRIBUIBILE** con un'ipotesi non dimostrata: che il
difetto fosse legato alla dimensione del metodo, perché la stessa espressione in un metodo piccolo
dava il risultato giusto. L'esperimento che la mette alla prova è uno solo, e non tocca niente di
ciò che il test asserisce.

**L'esperimento.** Il calcolo del riquadro del vetro è stato estratto dal corpo di
`noOccupantPixelLeavesTheGlass` in un helper piccolo, `glassPane(type, shell)`. **Stesse costanti,
stessi rami, stesso ordine, stessi nomi di variabile al sito di chiamata.** Non sono cambiati la
tolleranza, i bordi, la geometria, né il criterio di colore. È cambiato *dove* l'aritmetica viene
valutata, e nient'altro — gli altri tre siti che calcolano lo stesso riquadro nello stesso file
sono stati **lasciati inline apposta**, come controllo.

**L'esito, MISURATO — il valore che le locali assumono prima e dopo:**

| | `paneT` | `paneB` |
|---|---|---|
| prima (calcolo inline nel metodo grande) | **0.0** | **0.0** |
| dopo (stesso calcolo in `glassPane`) | **−16.0** | **9.0** |
| dopo, ramo del camion (`FIRE_TRUCK`) | **−13.0** | **6.0** |

Le costanti leggevano già correttamente −16.0 e 9.0 *nella stessa riga di log* in cui le locali
valevano 0.0. Dopo l'estrazione i valori sono giusti su **tutte e sei le carrozzerie e su entrambe
le corsie**, e anche sul ramo del camion, che prima non veniva nemmeno preso.

**Il test è verde per la ragione giusta:** il riquadro non è più degenere, quindi i pixel degli
occupanti — che stavano già dentro il vetro, come il pass precedente aveva misurato — non vengono
più contati come fuori. La suite strumentata è **148/148**.

**Quanto è circoscritto il difetto (condizione J — NON scattata).** Quattro prove indipendenti:

- gli altri **tre siti** dello stesso calcolo, nello stesso file, sono rimasti inline e i loro test
  passano;
- la stessa espressione in un metodo piccolo dava già il risultato corretto su questo dispositivo;
- l'intera suite strumentata passa, **148 su 148**: nessun altro test mostra il sintomo;
- i 24 golden Canvas mostrano **solo** differenze di renderer, senza alcuna differenza di
  contenuto, il che è la prova che il percorso di disegno — pieno di metodi grandi in
  `SceneObjectRenderer` — non è toccato. Dopo la rigenerazione la prova è **più forte**, non più
  debole, perché il confronto diventa same-device: ogni frame combacia col suo golden a **zero
  pixel differenti**.

**Che cosa resta non dimostrato, e va detto.** Il *meccanismo* — perché un metodo con oltre 39
registri dex faccia valutare 0.0 a due locali su ART di Android 10 — non è stato dimostrato, solo
circoscritto. Il dex contiene le costanti giuste, quindi non è il compilatore. Questa voce si
chiude con una **diagnosi verificata sperimentalmente e un rimedio minimo**, non con una
spiegazione del runtime.

## 36 — Il cancello della densità persone ha consumato l'86% del suo margine

**RESOLVED (2026-09-05, secondo pass) — rigenerando i golden, non allargando il cancello.**

La decisione del coordinatore, e la sua ragione, che è di principio: **il pavimento di un cancello
deve essere il rumore di rendering del dispositivo che esegue il test.** Lo 0,1223% misurato non
era rumore — il rumore su questo dispositivo è zero, voce 31 — era uno **scarto costante** contro
un golden autorato su hardware che non esiste più. Rigenerare toglie un artefatto dalla misura;
allargare il cancello lo incorporerebbe per sempre, ed è cambiare la metrica perché il numero
passi.

**L'attribuzione è venuta prima, e per tutte e ventiquattro.** Nessun golden è stato rigenerato
prima che la sua differenza fosse classificata. Il criterio è quello del pass precedente, e il
numero che decide è la **dimensione della componente connessa** dei pixel sopra tolleranza:

- differenze **sotto** la tolleranza del progetto (1–8 livelli su 255) coprono il 22–42% di ogni
  frame e sono il dithering del gradiente del cielo — bande orizzontali, visibili nelle immagini
  di differenza;
- differenze **sopra** la tolleranza sono lo 0,007–0,099% del frame, sparse in 17–182 componenti
  separate, e **la componente più grande su tutte e 24 le scene è di 12 pixel**;
- **nessun oggetto** manca, si sposta o cambia colore: le sagome combaciano tutte.

Un oggetto intero spostato o assente produrrebbe una componente di centinaia o migliaia di pixel.
Tutte e 24 sono **RENDERER**; la condizione K non è scattata su nessuna scena. La tabella completa
— frame, ogni rettangolo di focus asserito, conteggi, componenti — è nel report di consegna.

**Il risultato, MISURATO dopo la rigenerazione**, con l'aritmetica del progetto:

| cancello | rettangolo | pavimento efficace prima | dopo | limite | margine |
|---|---|---|---|---|---|
| **densità persone** | PAVEMENT su `people-single` | 0,1223% | **0,0000%** | 0,1415% | **100%** |
| conteggio auto giorno | banda strada | 0,2273% | **0,0000%** | 3,8041% | **100%** |
| densità auto notte | banda strada | 0,0000% | **0,0000%** | 7,0058% | **100%** |
| orario commerciale | banda facciate | 0,1068% | **0,0000%** | 1,0038% | **100%** |

**Ogni** frame e **ogni** focus asserito misurano ora **zero pixel differenti**. I quattro limiti
non sono stati toccati, e non c'era niente da ri-derivare: i segnali erano già stati rimisurati su
questo dispositivo e si riproducono alla quarta cifra (voce 31).

**I tre golden GL non sono stati toccati** e restano verdi: `GlDriverGapGuardTest` legge ancora
day 0,00% / lake-busy 0,01% / thunderstorm 0,24% contro il cancello del 3%. `GoldenUniquenessTest`
è verde: nessuna coppia di golden rigenerati è byte-identica, quindi nessuna scena ha smesso di
esercitare la propria impostazione.
