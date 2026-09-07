# PaperScrape v4.22 — Audit dello spreco, percorso GL. **Fase 1, checkpoint.**

**Consegna: DOCUMENTAZIONE, NON UNA RELEASE.** `versionCode 53`, `versionName "4.22"` invariati.
Nessuna riga di codice spedito toccata, nessun golden toccato, nessuna rimozione applicata, nessun
tag, nessuna release, nessun push.

**Il pass si ferma alla Fase 1, come prescritto.** In più della fermata prevista al checkpoint, è
scattata la **condizione B**: i tre golden GL non bastano a dimostrare neutra una modifica al draw
path, e questo rende l'estensione della copertura un **prerequisito** della Fase 2.

Etichette ovunque: **OSSERVATO / MISURATO / DEDOTTO / DICHIARATO**.

---

## 0. Baseline — verificata, condizione D non scattata

MISURATO, tutto ricontrollato in questa sessione:

| voce | atteso | osservato |
|---|---|---|
| ZIP di partenza SHA-256 | `37ec53cf…acc9` | identico |
| byte / voci | 5 708 828 / 881 | 5 708 828 / 881 |
| `versionCode` / `versionName` | 53 / "4.22" | 53 / "4.22" |
| JVM | 1331 | **1331 eseguiti, 0 falliti, 0 errori** (126 XML) |
| strumentati | 148 | 148 (conteggio statico di `@Test`) |
| Python asset | 108 | **108 eseguiti, OK** (venv `paperscrape-assets`) |
| PNG golden | 27 | 27 (di cui 3 `gl-*.png`) |
| `@Ignore` | zero | zero |

Una precisazione sull'ultima riga, perché un grep ingenuo dice il contrario: `grep -c @Ignore` su
`app/src` restituisce **3**, ma tutte e tre le occorrenze sono **prosa dentro commenti**
(`GoldenUniquenessTest` che racconta come la v4.21 fu consegnata `@Ignore`d, e due righe di
`PeopleGoldenTest`). **Annotazioni `@Ignore` attive: zero.**

---

## 1. Su quale build si è misurato — **condizione H NON scattata**

Il §0-bis del prompt chiedeva una build simil-release e prevedeva l'arresto se non fosse
producibile senza toccare la firma del maintainer. **È producibile, e la firma del maintainer non è
stata sfiorata.**

Il progetto committa già un `debug.keystore` (`app/build.gradle.kts` lo documenta come deliberato).
Lo strumento di misura è quindi un build type locale `perf` che:

- fa `initWith(release)` — **R8 acceso, `isShrinkResources` acceso, `isDebuggable = false`**, gli
  stessi `proguardFiles`;
- firma con il **`debug.keystore` committato**, non con una chiave di release;
- porta l'`applicationIdSuffix = ".debug"` della build di debug, quindi si installa **sopra la
  build di debug** e non può collidere con — né sovrascrivere — la release 4.15 del maintainer.

**La configurazione di firma di release non è stata letta, generata né modificata.** Le variabili
d'ambiente `PAPERSCRAPE_RELEASE_*` non sono state impostate, e il ramo `if (!…isNullOrBlank())` di
`build.gradle.kts` è rimasto inerte come in origine.

Il build type `perf` e la strumentazione **non sono nello ZIP consegnato** (§8).

### 1.1 Perché questo era il vincolo più importante — è confermato dai numeri

MISURATO, stessa scena, stesse condizioni, stesso minuto del pomeriggio, **senza strumentazione**,
sulla configurazione del maintainer ripristinata (Autumn, ora reale, giorno):

| | build **debug** | build **simil-release** | rapporto |
|---|---|---|---|
| processo intero | **102,69%** di un core (n=3, sd 0,66) | **27,72%** (n=3, sd 0,23) | **3,70×** |
| `PaperScrapeGlTh` (loop di disegno) | **64,06%** | **24,42%** | **2,62×** |
| `Jit thread pool` | **33,61%** | **assente** | — |
| `Profile Saver` | 1,14% | assente | — |
| binder + resto | 1,99% | 2,55% | — |

Il `Jit thread pool` **non compare affatto** nella build simil-release: `debuggable = false`
riattiva l'AOT e l'artefatto sparisce, esattamente come `CLAUDE.md` §7 e la voce 32 prevedevano.

**Conseguenza per la voce 27, e va scritta chiaramente.** Il «67–68%» ha ora due qualificatori, non
uno: è il **thread di rendering** (lo stabilisce la voce 32) **di una build di debug** (lo
stabilisce questo pass). Sul telefono dell'utente, che esegue una build di release, lo stesso thread
sta a **24,42% di un core** nella stessa scena. **Misurare lo spreco in debug avrebbe sovrastimato
il bersaglio di un fattore ~2,6.**

---

## 2. Il protocollo, per esteso, prima dei numeri

### 2.1 Che cosa viene misurato, con quale strumento

**CPU (quantità 5).** Delta di `utime + stime` da `/proc/<pid>/stat` fra i due estremi della
finestra, diviso per il tempo di parete della stessa finestra. È il totale del **thread-group
leader**, cioè la **somma su tutti i thread** — la grandezza che la voce 27 prescrive, non «il solo
pid» nel senso del thread principale. `CLK_TCK` = 100 (verificato), quindi tick/secondo è
direttamente la percentuale di **un** core; il device ne ha 8.

Il `/proc` del processo **resta leggibile anche con `debuggable = false`** (verificato): il
protocollo della voce 32 si trasferisce alla build simil-release senza modifiche.

**Ripartizione per thread.** Da `/proc/<pid>/task/<tid>/stat`, tagliando prima la parentesi del
`comm` (`sed 's/.*) //' | awk '{print $12+$13}'`): `Jit thread pool` contiene spazi e un `awk` sulle
colonne grezze lo riporta a zero. Trappola già registrata dalla voce 32, riconfermata qui.

**GPU busy (quantità 4) — la strada è nuova e va scritta, perché la prima non esiste su questo
device.** I nodi sysfs kgsl (`/sys/class/kgsl/kgsl-3d0/gpubusy` e compagni) sono **root-only sotto
policy enforcing** e `adb root` è **disabilitato dalla build** (LineageOS di produzione): letti da
shell danno *Permission denied*. Anche la data source `gpu.counters` di perfetto **non è
disponibile** — il binario `gpu_counter_producer` esiste ma non è connesso come producer.

Quello che **è** disponibile è il tracepoint del kernel `kgsl/kgsl_pwrstats`, che perfetto registra
come **evento ftrace generico** — cioè con i nomi dei campi come stringhe, quindi leggibile senza
proto generati. Ogni campione porta `total_time` e `busy_time` in microsecondi; sommati sulla
finestra danno la frazione di occupazione. Cadenza osservata ~35 campioni/s, ~2 090 campioni per
finestra da 60 s.

Tre letture indipendenti concordano, ed è il controllo che rende credibile la misura:
`kgsl_pwrstats` sommato, il tracepoint separato `kgsl_gpubusy` (`busy`/`elapsed`), e
`busy_time`/tempo di parete — 20,45% / 19,2–20,5% / 20,61% sulla stessa finestra di prova.

**Quantità 1, 2, 3 — la sonda.** Un oggetto locale (`GlWasteProbe`) agganciato in **cinque punti**
di `GlSceneTarget` (`beginFrame`, `endFrame`, `flush`, `onSurfaceSizeChanged`, l'upload in
`drawSprite`). Accumula per frame e stampa **una riga di logcat ogni 300 frame** (~10 s), non una
per frame.

- **Identità del frame (quantità 1).** I pixel di un frame sono funzione del flusso di vertici, della
  texture legata a ogni batch, dell'uniforme di proiezione e del colore di clear. Gli ultimi due
  sono costanti fra due cambi di dimensione della superficie, quindi **una hash FNV-1a su ogni float
  che entra nel vertex buffer, più l'handle di texture e il conteggio vertici di ogni batch, decide
  l'identità senza `glReadPixels`.** Il contenuto delle texture è il solo ingresso che la hash non
  vede: **un frame con un upload è perciò squalificato d'ufficio** dall'essere dichiarato identico.
  Il test è **conservativo**: può chiamare diverso un frame identico, non può chiamare identico un
  frame diverso.
- **Pixel coperti (quantità 2).** Ogni triangolo emesso viene **ritagliato al viewport**
  (Sutherland-Hodgman sui quattro semipiani, con via rapida per il caso interamente interno) e la
  sua area sommata. Diviso per l'area della superficie è il fattore di overdraw. Il `glClear` **non
  è contato**: scrive ogni pixel una volta per la via rapida del driver, ed è un +1 costante.
- **Draw call e pavimento (quantità 3).** Le draw call sono le `flush()` che portavano vertici. Il
  pavimento accanto è il numero di **texture distinte** legate nel frame.

### 2.2 Condizioni fissate, tutte dichiarate

| condizione | valore |
|---|---|
| device | OnePlus 6T, `ONEPLUS A6013`, Android 15, Adreno 630 |
| build misurate | `perf` (simil-release) con e senza sonda; `debug` con e senza sonda |
| installazione | ogni APK **verificato byte per byte contro il `pull`** prima di misurare |
| tema | **Autumn** (quello del maintainer; mai cambiato) |
| scena A | ora fissa **12:00**, auto visibili 100%, persone visibili 100% |
| scena B | ora fissa **1:00**, auto **spente dalla spunta**, persone **spente dalla spunta** |
| scorrimento | **15%** in entrambe (valore del maintainer), parallasse 1.0x, sfondo non scorrevole |
| visibilità | wallpaper **visibile** sul launcher Nova, nessun'altra app in foreground |
| modalità aereo | attiva (era già attiva) |
| luminosità | manuale, valore 33 |
| alimentazione | collegato, `stay_on_while_plugged_in = 2` |
| riscaldamento | **180 s dichiarati** prima della prima finestra di ogni blocco |
| finestra | **60 s**, ripetizioni consecutive; **n=5** per i blocchi `perf`, **n=3** per quelli `debug` |

Il wallpaper è verificato **vivo e con lo stesso pid prima e dopo ogni blocco**: una raffica di
`am instrument` o una decisione del sistema possono far ripiegare Android sullo sfondo statico, e un
blocco misurato dopo quel momento avrebbe misurato zero. Nessun blocco ha dato l'avviso.

### 2.3 Due difetti dello strumento trovati e corretti, non nascosti

1. **`date +%N` non esiste in toybox.** La prima esecuzione ha prodotto finestre di durata
   **negativa**. Il clock è stato spostato su `/proc/uptime` (risoluzione centesimi di secondo:
   0,017% su 60 s) e **tutte le finestre sono state rifatte**. Nessun numero della prima esecuzione
   è riportato qui.
2. **`adb logcat -s PSWaste:I` non aggancia questo tag** e restituiva zero righe con la sonda
   perfettamente funzionante. Sostituito da un filtro sullo stream.

---

## 3. Le cinque quantità × due scene

**Mai una media fra le due scene.** Dispersione sempre accanto alla media.

### 3.1 Scena A — affollata (Autumn, 12:00, auto 100%, persone 100%)

| # | quantità | valore | dispersione | fonte |
|---|---|---|---|---|
| 1 | **frazione di frame identici al precedente** | **0,0000%** | **0 su 8 700 frame** | sonda, build `perf` |
| 2 | fattore di overdraw | **2,525×** | sd 0,045, [2,454 – 2,588], n=29 | sonda |
| 3a | draw call per frame | **24,92** | sd 2,64, [20,48 – 29,26] | sonda |
| 3b | texture distinte per frame (**il pavimento**) | **12,03** | sd 1,14, [9,90 – 14,02] | sonda |
| 3c | rapporto 3a/3b | **2,07×** | — | derivato |
| 4 | **GPU busy** | **20,87%** | sd 0,82, [19,90 – 22,03], n=5 | kgsl, build `perf` **senza sonda** |
| 5 | **CPU per frame, sommata sui thread** | **10,47 ms** | da 31,008% di un core (sd 0,386) a 29,61 fps | build `perf` **senza sonda** |

Valore in **debug** accanto, come richiesto: processo **100,66%** di un core (sd 0,485, n=3, con
sonda), GPU busy **21,71%** (sd 0,692), CPU per frame **34,03 ms**. Quantità 1, 2, 3 in debug:
**0,0000%** (0 su 5 100), **2,507** (sd 0,058), **24,98 / 12,10 = 2,06×** — cioè le stesse della
build `perf` entro la dispersione.

### 3.2 Scena B — quieta (Autumn, 1:00, auto spente, persone spente)

| # | quantità | valore | dispersione | fonte |
|---|---|---|---|---|
| 1 | **frazione di frame identici al precedente** | **0,0000%** | **0 su 8 700 frame** | sonda, build `perf` |
| 2 | fattore di overdraw | **2,369×** | sd 0,048, [2,309 – 2,453], n=29 | sonda |
| 3a | draw call per frame | **69,09** | sd 3,96, [64,80 – 72,95] | sonda |
| 3b | texture distinte per frame (**il pavimento**) | **5,00** | sd **0,000** | sonda |
| 3c | rapporto 3a/3b | **13,82×** | — | derivato |
| 4 | **GPU busy** | **19,92%** | sd 0,58, [19,02 – 20,62], n=5 | kgsl, build `perf` **senza sonda** |
| 5 | **CPU per frame, sommata sui thread** | **8,34 ms** | da 24,701% di un core (sd 0,306) a 29,62 fps | build `perf` **senza sonda** |

Valore in **debug** accanto: processo **102,81%** (sd 1,074, n=3, con sonda), GPU busy **19,65%**
(sd 0,537), CPU per frame **34,70 ms**. Quantità 1, 2, 3 in debug: **0,0000%** (0 su 5 400),
**2,337** (sd 0,052), **55,26 / 5,00 = 11,05×**.

### 3.3 Ripartizione per thread, per scena (build `perf`, con sonda)

| | scena A | scena B |
|---|---|---|
| processo | 34,54% di un core | 34,21% |
| `PaperScrapeGlTh` | **31,17%** (90,2% del processo) | **30,74%** (89,8%) |
| binder + resto | 2,47% | 2,52% |
| non attribuito | 0,91% | 0,94% |

Il residuo non attribuito (~0,9 pt, contro gli 0,08 pt della voce 32) è **dichiarato, non
nascosto**: viene dai thread binder che nascono e muoiono dentro la finestra, i cui tick restano nel
totale di processo ma spariscono dallo snapshot finale.

### 3.4 Overhead della strumentazione — misurato, e **la condizione C non scatta**

MISURATO per differenza fra la build `perf` con sonda e la build `perf` **senza sonda**, stesse
scene, stesso protocollo:

| scena | senza sonda | con sonda | overhead | in % del pulito | per frame |
|---|---|---|---|---|---|
| A affollata | 31,008% (sd 0,386) | 35,126% (sd 0,201) | **+4,12 pt** | +13,3% | +1,39 ms |
| B quieta | 24,701% (sd 0,306) | 35,578% (sd 1,202) | **+10,88 pt** | **+44,0%** | +3,67 ms |

**È un overhead grande, e per questo la struttura del report è quella che è:**

- **Le quantità 4 e 5 sono riportate dalla build senza sonda.** Non sono contaminate.
- **Le quantità 1, 2 e 3 sono conteggi del flusso di vertici**, che la sonda non altera: legge, non
  scrive. Che siano indipendenti dalla build è **misurato**, non dedotto — le stesse tre quantità
  prese sulla build di debug danno gli stessi valori entro la dispersione (§3.1, §3.2).
- **La sonda non cambia la cadenza, quindi non cambia l'evoluzione della scena.** Il loop è
  *pacato* a 33 ms e sottrae il costo del frame prima di dormire: con sonda il costo è 11,9–12,0 ms
  di CPU per frame, cioè **molto sotto il budget**, e gli fps misurati restano **29,61 e 29,62**,
  identici fra le due scene e fra le build. MISURATO.

La condizione C dice di non riportare una quantità il cui overhead sia dello stesso ordine. Qui
l'overhead colpisce una grandezza (la CPU) che è **misurata senza lo strumento che la disturba**, e
le grandezze misurate *con* lo strumento sono conteggi che lo strumento non muove. **Condizione C:
non scattata.** L'overhead resta comunque dichiarato sopra, perché è il numero che permette a chi
rifà la misura di riconoscere se ha montato la sonda o no.

### 3.5 Misura supplementare — la scena davvero ferma

La scena B porta comunque lo **scorrimento automatico al 15%**, che è il valore del maintainer:
`continuousScrollAccum` cresce a ogni frame e ogni livello si sposta, quindi «quieta» non vuol dire
«ferma». Poiché la quantità 1 è l'unica con una definizione intrinseca di spreco, valeva la pena
chiudere anche il caso limite.

**Scena B con scorrimento a 0%** (notte fonda, auto spente, persone spente, deriva disattivata),
MISURATO: **0 frame identici su 1 800**. Overdraw 2,335 (sd 0,026); draw call 64,88 (sd 0,024);
pavimento 5,00; rapporto 12,98×.

Restano in movimento, e bastano da soli a rendere ogni frame diverso dal precedente: le nuvole, lo
scintillio delle stelle, gli uccelli, le foglie che cadono (Autumn accende `fallColorsEnabled`) e
gli occupanti delle finestre illuminate.

---

## 4. Come vanno letti questi numeri

### 4.1 Quantità 1 — **spreco zero, e la leva più ovvia è morta**

**0 frame identici su 19 200 misurati**, su tre configurazioni (A affollata, B quieta, B con
scorrimento a zero) e due build. Non «pochi»: **zero**.

Questa è la quantità che il prompt indicava come la definizione operativa dello spreco, e quella da
cui ci si aspettava che la scena quieta dicesse qualcosa. **Dice qualcosa, ed è un no.** Il flag di
stato della scena che salta i frame identici — la strada che il §4 del prompt indicava come quella
giusta rispetto al `glReadPixels` — **non avrebbe niente da saltare in nessuna configurazione
raggiungibile dalle impostazioni**. Aggiungerlo sarebbe costo puro.

Vale la pena dire *perché*, perché è strutturale e non un caso: la scena non ha uno stato statico.
Nuvole, stelle, uccelli e foglie sono funzioni continue del clock, e la deriva di scorrimento —
quando è accesa — muove ogni livello. Un flag «nulla è cambiato» sarebbe falso a ogni frame.

### 4.2 Quantità 2 — l'overdraw è 2,4–2,5×, ma **quanto ne sia sprecato non è misurato**

Il numero misurato è quello che il prompt chiede: pixel colorati per pixel a schermo, ritagliati al
viewport. **2,525×** affollata, **2,369×** quieta.

**Ciò che questo numero non dice, e non va fatto dire:** quanta parte di quei 2,5 passaggi finisce
sotto uno strato opaco successivo. Separare il coperto-e-poi-nascosto dal coperto-e-visibile
richiede un'analisi per pixel che questo strumento non fa. **Chiamare «spreco» l'intero 1,5× in
eccesso sarebbe un errore**: gli sprite paper-cutout hanno bordi trasparenti, il cielo è un
gradiente sotto tutto, e la fusione è accesa proprio perché quei passaggi si sommano.

Quello che si può dire con i dati in mano è che **il riempimento non è il vincolo**: la GPU è al
**20%** in entrambe le scene. Anche azzerando del tutto l'overdraw eccedente, il collo di bottiglia
resterebbe dov'è, cioè sulla CPU del thread di disegno.

### 4.3 Quantità 3 — il divario più grande, ma **il pavimento non è raggiungibile a costo zero**

È qui che il numero è vistoso: nella scena quieta **69 draw call contro un pavimento di 5**, cioè
**13,8×**. Nella scena affollata **24,9 contro 12,0**, cioè 2,07×.

Due cose vanno dette insieme, o il numero inganna.

**Primo: l'atlante funziona.** Il pavimento è 5 texture di notte e ~12 di giorno su un set di
centinaia di sprite, e gli **upload sono zero in ogni finestra a regime**: a scena assestata nulla
viene ricaricato. Il divario non è «l'atlante non c'è», è «l'ordine di disegno rompe il batch».

**Secondo, ed è il vincolo: l'ordine di disegno *è* l'ordine di profondità.** `GlTextureAtlas` lo
dice già nel suo commento — l'atlante rimuove le transizioni invece di riordinare, «perché l'ordine
di disegno è l'ordine di profondità e non può essere cambiato». Con la fusione accesa e i pixel
sovrapposti, raggruppare per texture **cambia il quadro** ogni volta che due sprite di texture
diversa si sovrappongono. Il pavimento di 5 è quindi un **limite inferiore teorico, non un
obiettivo sicuro**: la parte raggiungibile è solo quella fra sprite che provatamente non si
sovrappongono, e quanto valga non è misurato qui.

Che di notte le draw call **triplichino** rispetto al giorno (69 contro 25) mentre le texture
distinte **si dimezzano** (5 contro 12) è il dato più informativo del pass per un'eventuale Fase 2:
qualcosa nel percorso notturno alterna un pugno di texture decine di volte per frame. Individuare
*quale* ciclo lo fa è un contatore per chiamante, ed è una misura che questo pass non ha fatto.

### 4.4 Quantità 4 e 5 — il costo vero, e il contesto che mancava

**GPU: 20,9% / 19,9%.** Praticamente identico fra le due scene, e ampiamente lontano dalla
saturazione.

**CPU: 10,47 ms / 8,34 ms per frame** contro un intervallo di 33 ms. Il thread di disegno prende
~90% del processo. La scena quieta costa il **20% in meno** della affollata (24,70% contro 31,01% di
un core): spegnere auto e persone si vede, ma non porta il costo vicino a zero, perché ciò che resta
— cielo, colline, edifici, alberi, nuvole, stelle, finestre illuminate — è il grosso.

**Il contesto che questo pass aggiunge, e che cambia la domanda:** il numero da cui è partita tutta
questa linea di lavoro, il «67–68%», è **il thread di rendering di una build di debug**. Sul
telefono dell'utente lo stesso thread sta a **24,42%**, e il processo intero a **27,72%**, di **un**
core su otto. A schermo spento la voce 32 ha già misurato **0,12%**.

---

## 5. §1 — Che cosa vedono davvero i tre golden GL. **Condizione B: SCATTATA**

### 5.1 Che cosa sono

OSSERVATO in `GlSceneGoldenTest.kt` e `SharedGoldenScenes.kt`, e confermato aprendo le tre PNG:

| golden | `dayPhase` | `warmUpFrames` | tema | extra |
|---|---|---|---|---|
| `gl-day` | `day()` = ora 13 | **0** | `sunset` | focus `SUN_GLOW` (53,33)-(307,287) |
| `gl-lake-busy` | `day()` = ora 13 | **0** | `sunset` | lago, barche e delfini a densità 1 |
| `gl-thunderstorm` | `day()` = ora 13 | **0** | `sunset` | pioggia 1, nuvole 1, temporale |

Tutti e tre: `sceneSeconds = 120.0`, `homeScreenOffset = 0`, `swipeScrollEnabled = false`,
`scrollSpeed = 0`, **un solo fotogramma con `deltaSeconds = 0`**.

### 5.2 Che cosa coprono

Bene, e per quello per cui erano stati scelti (P1-4): `drawVerticalGradientRect` (cielo),
`drawVerticalGradientShape` (colline), `drawShape` (nuvole, monti), `drawRect` fill e stroke,
`drawOval` (ombre), `drawWedge` (le strisce del parasole), `drawCircle` e `fillDisc`, `drawLine` con
cap tondo (pioggia, solo `thunderstorm`), `drawRadialGlow` (alone del sole, con focus dedicato) e
`drawSprite` via atlante. **Per i livelli statici — cielo, colline, edifici, alberi — la copertura
c'è.**

### 5.3 Che cosa non coprono

1. **Ogni veicolo, in ogni scena.** `warmUpFrames = 0` su tutti e tre; un'auto parte con `progress`
   **negativo** e avanza solo dentro `update(deltaSeconds)`, chiamato qui una volta con
   `deltaSeconds = 0`. **Nessun veicolo è mai entrato in un fotogramma golden GL** — e le tre PNG lo
   confermano, strada vuota in tutte e tre. (Il commento di `lakeBusy` dice «every lane occupied»: le
   corsie piene sono quelle del **lago**, non della strada.)
2. **La notte per intero.** `dayBlend = 1` in tutti e tre: fuori la luna e `drawMoonWithPhase`, le
   stelle, le finestre illuminate e i loro occupanti, `nightGlow`, l'**orario commerciale** e
   `carsNightDensity`/`peopleNightDensity` — cioè entrambe le funzioni nuove della v4.22.
3. **Ogni tema tranne `sunset`**, inclusa **Autumn**, che è il tema del maintainer e quello su cui
   questo pass ha misurato: accende foglie cadenti e zucche, che nessun golden GL contiene.
4. **La presentazione invernale** (neve sugli alberi e sui tetti, pupazzi, pinguini, vestiti).
5. **Natale / Capodanno / Halloween**, e con essi **la slitta**: 1563×434, oltre il limite di 1024
   dell'atlante, quindi **precisamente lo sprite che prende per forza una texture propria e spezza
   il batch** — e nessun golden lo contiene.
6. **Arcobaleno, lampo, fiori, cumuli a terra.**
7. **Scorrimento e parallasse**: tutti e quattro gli ingressi pinnati al ramo banale.
8. **Il movimento stesso**: un fotogramma, `deltaSeconds = 0`.

### 5.4 Perché non bastano, e non è una questione di tolleranza

I tre golden dimostrano che una modifica non cambia **un fotogramma statico, di giorno, su
`sunset`, senza veicoli e senza scorrimento**. È vero e utile.

**Non dimostrano neutra una modifica al draw path di questo pass**, per il punto 8, che è
strutturale:

> Le due leve nominate dal prompt — il flag di stato della scena e la cache dei livelli statici —
> sono per costruzione modifiche a ciò che accade **fra** un fotogramma e il successivo. Un golden
> che disegna **un** fotogramma con `deltaSeconds = 0` passa identico sia che la cache sia corretta,
> sia che sia stantia, sia che non venga **mai invalidata**. Non è una tolleranza troppo larga: è
> una domanda che quel test non pone.

Si somma il punto 1 (la popolazione che si muove di più, i veicoli, non c'è) e il punto 2 (una
cache invalidata su `dayBlend` verrebbe esercitata proprio dove non c'è copertura).

**Condizione B: SCATTATA.** Il minimo che renderebbe la regola «i golden restano byte-identici»
sufficiente per quelle leve:

- **almeno un golden GL multi-fotogramma** (`warmUpFrames > 0`), confrontato **dopo** il
  riscaldamento — già supportato: `GlGolden.render` onora `scene.warmUpFrames`, e i golden Canvas
  del traffico usano `TRAFFIC_WARM_UP_FRAMES = 390`;
- **almeno un golden GL notturno**, per il ramo `dayBlend → 0`;
- **almeno un golden GL con traffico** — che il primo punto ottiene già di conseguenza, perché 390
  frame di riscaldamento sono esattamente ciò che porta i veicoli in strada.

Due scene nuove (giorno-con-traffico riscaldata, notte-con-traffico riscaldata) le coprono tutte e
tre, ed entrambe sono autorabili sull'Adreno come le tre esistenti. **Va detto insieme:** quelle PNG
erediterebbero il debito della **voce 20** (un dispositivo, una sola esecuzione) e la **voce 31**
dice la stessa cosa dei pavimenti dei cancelli. Estendere la copertura **sposta** il limite, non lo
elimina.

---

## 6. Candidate — **elencate, non applicate**

Fase 1: nessuna modifica è stata fatta. La decisione è del maintainer.

1. **Il flag di stato della scena (frame identici) — RACCOMANDATO DI NON FARE.** Misurato a **0
   frame identici su 19 200** in tre configurazioni. Non c'è niente da saltare. Costo > 0,
   guadagno = 0.
2. **Raggruppamento per texture (quantità 3).** Divario misurato: 13,8× nella scena quieta, 2,07×
   nell'affollata. **Rischio alto e guadagno non quantificato**: l'ordine di disegno è l'ordine di
   profondità, quindi la parte sicura è solo quella fra sprite che non si sovrappongono, e questo
   pass non l'ha separata. Prima di qualunque implementazione servirebbe un contatore per chiamante
   che dica *quale* ciclo notturno alterna 5 texture 69 volte per frame — è una misura, non una
   modifica, ed è il passo successivo naturale se il maintainer vuole tirare questo filo.
3. **La cadenza `FRAME_INTERVAL_MS = 33`.** Come la voce 32 la lasciava: è **meno lavoro utile, non
   spreco**. Risparmio stimabile ~1/3 del lavoro per frame. **Non implementata, e non da
   implementare senza che il maintainer guardi il telefono**: auto e nuvole si muovono e a 20 fps il
   moto potrebbe leggersi a scatti.
4. **La cache dei livelli statici.** Resta la leva strutturale, e **questo pass non l'ha prezzata**:
   la quantità 1 misura l'identità del *fotogramma intero*, non quella di una sua parte, quindi lo
   zero misurato **non dice nulla** su quanto costi ridisegnare cielo, colline, edifici e alberi a
   ogni frame. Prezzarla richiede un contatore per livello, e la sua neutralità richiede prima la
   copertura del §5.4. **Rischio dichiarato:** tocca il draw path, e la sua sicurezza poggia oggi su
   tre golden che non vedono il movimento.
5. **`drawArc` è codice morto sul percorso spedito.** OSSERVATO: `SceneCanvas.drawArc` non ha
   **nessun chiamante** in `app/src/main` — l'arcobaleno che lo usava è diventato uno sprite. Resta
   implementato in entrambi i backend. Non costa nulla per frame; è pulizia, non spreco, e non è
   stata fatta perché fuori dal perimetro dichiarato.

**Il quadro d'insieme, che è il vero risultato della Fase 1.** Sulla build che gira davvero sul
telefono, il wallpaper visibile costa **27,7% di un core su otto** nella configurazione del
maintainer, **31,0%** nella scena più affollata, con la GPU al **20%** e **0,12%** a schermo spento.
La quantità con una definizione intrinseca di spreco misura **zero**. Non è la condizione A — la
quantità 3 mostra un divario reale e non spiegato — ma è vicina, e la distanza fra «c'è un divario»
e «c'è uno spreco che vale la pena togliere» è esattamente la misura per chiamante che il punto 2
descrive.

---

## 7. Contabilità

| | valore | aritmetica |
|---|---|---|
| test JVM | **1331** eseguiti, 0 falliti, 0 errori | somma su 126 XML di `testDebugUnitTest` |
| test strumentati | **148** | conteggio statico di `@Test` in `app/src/androidTest` (33 file) |
| test Python | **108** eseguiti, OK | `unittest discover` con il venv `paperscrape-assets` |
| PNG golden committati | **27** | 24 Canvas + 3 `gl-*.png` |
| voci dell'archivio | **881** | 881 di partenza + 1 aggiunto (questo report) = **882** |

**Gli strumentati non sono stati eseguiti, di proposito**, per la stessa ragione della voce 32: le
raffiche di `am instrument` fanno ripiegare Android sul wallpaper statico e l'effetto persiste, e
questo pass doveva tenere il device in uno stato di misura stabile per oltre due ore. Nessuna
affermazione di questo report dipende da loro. **Nessun golden è stato eseguito né rigenerato**, e
nessuna modifica al codice spedito è stata fatta che potesse muoverne uno.

**Verifica di livello.** La modifica consegnata è di **Livello 1** per `CLAUDE.md` §5.1 — solo
documentazione non eseguibile. `assembleDebug` non sarebbe richiesto; è stato comunque eseguito
(più volte) perché serviva alla misura, insieme a `assemblePerf`, tutti **BUILD SUCCESSFUL**.

---

## 8. File toccati — dal diff dei due archivi

**0 rimossi, 1 aggiunto, 3 modificati.**

| file | che cosa |
|---|---|
| `V4_22_AUDIT_SPRECO_GL_REPORT.md` | **aggiunto** — questo documento |
| `BACKLOG_v4_22.md` | modificato — nuova **voce 33**, riga nella tabella di sintesi, intestazione |
| `BACKLOG_v4_21.md` | modificato — **voce 27** con la seconda nota datata |
| `CLAUDE.md` | modificato — §7, i numeri della build simil-release |

**La strumentazione non è nello ZIP.** Verificato per differenza degli archivi: `GlWasteProbe.kt`
non esiste, i cinque agganci in `GlSceneTarget.kt` non ci sono, e il build type `perf` non è in
`app/build.gradle.kts` — quel file è **byte-identico** a quello dello ZIP di partenza, come ogni
altro file del codice spedito.

---

## 9. Stato del device alla consegna

OSSERVATO, verificato dopo l'ultima misura:

| voce | stato |
|---|---|
| APK del wallpaper | debug **4.22 / versionCode 53**, SHA-256 `9c684dd4…c868`, **`pull` byte-identico** — ed è **lo stesso APK trovato a inizio sessione** |
| wallpaper attivo | `com.paperscrape.livewallpaper.debug/…PaperWallpaperService`, **osservato mentre rende** (Autumn, ora reale, giorno, sole, traffico, persone) |
| impostazioni dell'app | **ripristinate byte per byte**: DataStore `99f1b214…0517`, identico al backup preso prima di toccare qualsiasi cosa |
| release del maintainer | `com.paperscrape.livewallpaper` **4.15 / 46**, intatta, mai toccata |
| pacchetto di test | non installato (non lo era all'inizio, non lo è ora) |
| `screen_off_timeout` | **120000**, ripristinato |
| `screen_brightness_mode` | **1 (automatico)**, ripristinato |
| `stay_on_while_plugged_in` | **2**, come all'inizio |
| `airplane_mode_on` | **1**, mai toccato |
| file spinti sul device | **rimossi** (script in `/data/local/tmp`, config e tracce perfetto); i residui elencati là — `dalvik-cache`, `main.jar`, `swipe*.mp4` — erano già presenti e non sono miei |

**I valori originali erano stati annotati prima di essere cambiati**: `screen_off_timeout=120000`,
`screen_brightness_mode=1`, `screen_brightness=54`, `stay_on_while_plugged_in=2`,
`airplane_mode_on=1`, e le preferenze dell'app (tema `autumn`, `sync_real_time=true`,
`fixed_hour=12.0`, `scroll_speed≈0,15673`, `obj_CARS_density=1.0`, `business_hours_enabled=false`).

Due precisazioni, perché non tornerebbero da sole:

- **`screen_brightness` legge 1, non 54.** La luminosità automatica è stata riattivata — era il suo
  stato originale — e il sistema ha subito ripreso a decidere il valore. Il **regime** è
  ripristinato; il numero è gestito dal sistema. È la stessa nota che il pass precedente aveva
  lasciato.
- **Le impostazioni dell'app sono state ripristinate scrivendo il file DataStore, non dall'interfaccia.**
  Motivo: lo slider dello scorrimento ha passi dell'1% e non può riprodurre lo `0,15673` memorizzato.
  Il file di backup era stato letto con `run-as` prima di ogni modifica e riscritto identico
  (SHA-256 verificato dopo il riavvio del processo). Nessun `force-stop`: il processo è stato fatto
  ripartire reinstallando lo stesso APK, per non incorrere nella trappola del wallpaper statico.

---

## 10. Artefatto

| | |
|---|---|
| file | `PaperScrape_v4_22.zip` |
| voci | **882** = 881 + 1 aggiunto, 0 rimossi, 3 modificati |
| sostituisce | `37ec53cf13542e4b2fd0ae2d116dae4e9433fac8199ee8a0064c1b8da862acc9`, 5 708 828 byte, 881 voci |

**SHA-256 e dimensione dell'archivio sono nella nota di consegna, non qui**, per la ragione
ovvia: questo file sta *dentro* l'archivio, e non può contenere l'impronta di qualcosa che lo
contiene. Una copia del report è consegnata anche accanto allo ZIP, e lì la riga c'è.

`versionCode 53` / `versionName "4.22"` invariati.

**Etichetta della consegna: documentazione, non una release.** Pubblicazione, tag e release restano
al maintainer e non sono state fatte.
