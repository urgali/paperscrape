# PaperScrape v4.22 — Pass di sola misura: CPU

**Consegna: DOCUMENTAZIONE, NON UNA RELEASE.** `versionCode 53`, `versionName "4.22"` invariati.
Nessuna riga di codice spedito toccata, nessun golden toccato, nessun tag, nessuna release,
nessun push. Le uniche differenze rispetto allo ZIP di partenza sono **tre file di documentazione**.

Etichette usate ovunque: **OSSERVATO / MISURATO / DEDOTTO / DICHIARATO**.

---

## 0. Baseline — verificata, condizione D non scattata

MISURATO, tutto ricontrollato in questa sessione:

| voce | atteso dal prompt | osservato |
|---|---|---|
| ZIP di partenza SHA-256 | `8bd2a63884eb641567f5cd884a7d2fde758eda05fb98cb1ab7c3c82216f2cf03` | identico |
| byte / voci | 5 723 522 / 881 | 5 723 522 / 881 |
| `versionCode` / `versionName` | 53 / "4.22" | 53 / "4.22" |
| JVM | 1331 | **1331 eseguiti, 0 falliti, 0 errori** (126 XML) |
| strumentati | 148 | 148 (conteggio statico di `@Test` in `app/src/androidTest`) |
| Python asset | 108 | **108 eseguiti, OK** (venv `paperscrape-assets`, Python 3.14.7) |
| PNG golden | 27 | 27 (di cui 3 `gl-*.png`) |

Due precisazioni sul conteggio, per onestà del metodo:

- I **148 strumentati** sono un **conteggio statico**, non un'esecuzione. Non sono stati eseguiti di
  proposito: raffiche di `am instrument` sono una trappola nota del progetto (fanno ripiegare
  Android sul wallpaper statico, e l'effetto persiste), e questo pass doveva lasciare il device in
  uno stato di misura stabile. Nessuna affermazione qui dipende da loro.
- I **108 Python** passano solo con il venv dedicato; l'interprete di sistema ne esegue 58 con 4
  errori, perché mancano le dipendenze pinnate. Chi rifà la verifica usi
  `/home/bober/.venvs/paperscrape-assets/bin/python`.

**La voce 27 non sta in `BACKLOG_v4_22.md`.** Il prompt la indicava lì; sta in
`BACKLOG_v4_21.md`, dove è chiusa come **DOCUMENTED**. `BACKLOG_v4_22.md` porta avanti solo 18,
20, 25, 30. Non cambia nulla nel merito — è solo dove cercarla.

---

## 1. Il protocollo, per esteso, prima dei numeri

Questa sezione è la parte riutilizzabile: senza di essa un numero di CPU non vale niente.

**Che cosa viene misurato.** Il tempo di CPU del processo del wallpaper, letto come **delta di
`utime + stime` da `/proc/<pid>/stat`** fra i due estremi di una finestra, diviso per il tempo di
parete effettivamente trascorso nella stessa finestra. `utime + stime` del leader del gruppo di
thread è il totale **di processo**, cioè **la somma su tutti i thread**: è esattamente la
grandezza che la voce 27 prescrive, e non «il solo pid» nel senso del thread principale.
`CLK_TCK` = 100 sul device, verificato. Il risultato è espresso in **percentuale di un core**; il
device ne ha **8**, quindi 100% = un core saturo, 800% = tutta la CPU.

**Perché non `top`.** `top` campiona su un intervallo proprio e arrotonda; il delta su `/proc` è
esatto sull'intervallo che dichiaro io. `top -H` è stato usato solo come riscontro qualitativo.

**Condizioni fissate, tutte dichiarate:**

| condizione | valore |
|---|---|
| build | **debug**, costruita dallo ZIP consegnato, installata e verificata **byte per byte contro il `pull`** |
| tema | **Autumn** (già quello del maintainer sul device; non è stato cambiato nulla) |
| scena | ora reale, **notte** (finestre fra le 22:2x e le 23:1x del 2026-09-04) |
| visibilità | wallpaper **visibile**, launcher Nova in foreground, nessun'altra app |
| modalità aereo | attiva |
| luminosità | fissata a manuale, valore 33 |
| pacchetto di test | **disinstallato** prima di iniziare |
| alimentazione | collegato, schermo tenuto acceso (`stay_on_while_plugged_in = 2`) |
| riscaldamento | **180 s dichiarati** di rendering visibile prima della prima lettura di ogni blocco |
| finestra | **60 s**, ripetizioni consecutive |

**Sul «stesso tempo trascorso dall'avvio del wallpaper».** Interpretato come: stesso riscaldamento
dichiarato prima di ogni blocco, e finestre tutte nel regime assestato, con l'uptime del device
registrato per ogni finestra così che chi legge veda che stanno tutte nello stesso regime. Non ho
riavviato il motore prima di ogni singola ripetizione: farlo avrebbe richiesto o un force-stop
(trappola nota che fa ripiegare Android sul wallpaper statico, e persiste) o una reinstallazione a
ogni ripetizione, cioè avrebbe introdotto più rumore di quanto ne toglieva. La riproducibilità
attraverso i cicli di visibilità è invece misurata: ogni versione ha **due blocchi separati da uno
spegnimento e riaccensione dello schermo**.

**Le due build sono comparabili per costruzione.** v4.22 e v4.21 sono state entrambe costruite
dai rispettivi ZIP consegnati (`8bd2a638…` e `dcd1cf70…`), installate con `adb install -r -d`
(downgrade consentito perché il pacchetto è debuggabile — così la DataStore, e quindi il tema
Autumn, sopravvive allo scambio) e ogni installazione è stata **verificata byte per byte contro il
`pull`** prima di misurare.

---

## 2. Fase 1 — i numeri, con la dispersione

### 2.1 Il costo del processo

MISURATO. Tutte le ripetizioni, non solo le medie:

| blocco | ripetizioni (% di un core) |
|---|---|
| v4.22 blocco 1 | 108,58 · 107,82 · 109,15 · 107,96 · 105,54 |
| v4.22 blocco 2 (dopo ciclo di visibilità) | 108,95 · 109,14 · 108,92 · 108,95 · 108,90 |
| v4.22 blocco 3 (dopo reinstallazione) | 109,18 · 108,91 · 108,76 · 108,91 · 107,42 · 106,13 · 108,94 · 109,99 |
| v4.21 blocco 1 | 108,10 · 109,28 · 109,10 · 109,09 · 108,51 |
| v4.21 blocco 2 (dopo ciclo di visibilità) | 106,57 · 109,02 · 110,35 · 110,57 · 109,43 |

Aggregati:

| condizione | n | media | min | max | escursione | sd |
|---|---|---|---|---|---|---|
| **v4.22 visibile** | 18 | **108,45%** di un core | 105,54 | 109,99 | 4,45 pt | 1,11 |
| **v4.21 visibile** | 10 | **109,00%** | 106,57 | 110,57 | 4,00 pt | 1,13 |
| **v4.22 nascosto** (schermo spento) | 3 | **0,12%** | 0,08 | 0,17 | 0,09 pt | 0,05 |

**Condizione B non è scattata.** La dispersione (sd ≈ 1,1 pt) è **quasi due ordini di grandezza
sotto** lo scarto che il pass doveva risolvere: 108,45% contro il 67–68% della voce 27 sono **41
punti** di differenza. Il protocollo regge largamente per questa domanda. È invece **troppo
grossolano** per la differenza fra le due versioni, ed è detto sotto.

### 2.2 Il confronto con il 67–68% della voce 27: **non combacia**

108,45% contro 67–68%: **+41 punti**. Come prescritto, non ho aggiustato la misura — ho costruito
anche la **v4.21** dal suo ZIP e l'ho misurata con lo stesso protocollo, per **attribuire** la
differenza invece di spiegarla.

Risultato: **v4.21 misura 109,00%**, cioè la stessa cosa della v4.22. **La differenza fra 67–68% e
~108% non è la versione.** Se lo fosse, la v4.21 — che è la versione a cui il 67–68% si riferisce —
avrebbe dovuto darmi 67–68%.

### 2.3 Dove sta davvero il 67–68%: è il thread di rendering

MISURATO, e questa è l'attribuzione. Ripartizione per thread sulla v4.22, tre finestre da 60 s,
letta da `/proc/<pid>/task/<tid>/stat`:

| thread | finestra 1 | finestra 2 | finestra 3 | media |
|---|---|---|---|---|
| `PaperScrapeGlTh` (il loop di disegno) | 67,56 | 67,48 | 67,47 | **67,50%** |
| `Jit thread pool` | 37,19 | 37,23 | 37,23 | **37,22%** |
| `Profile Saver` | 1,28 | 1,28 | 1,30 | 1,29% |
| binder + resto | ~1,9 | ~1,8 | ~1,9 | ~1,85% |
| **somma sui thread vivi** | 107,92 | 107,78 | 107,83 | 107,84% |
| **processo** (`/proc/<pid>/stat`) | 107,90 | 107,78 | 107,91 | **107,86%** |

**La somma torna al totale entro 0,08 pt**, che è il controllo che rende credibile la
ripartizione.

**`PaperScrapeGlTh` sta a 67,50%.** Il 67–68% della voce 27 è **riprodotto con precisione**, ma è
il costo del **solo thread di disegno**, non del processo. La stessa struttura si vede sulla v4.21
(`top -H`: thread GL 60–68%, JIT ~37%), quindi non è un artefatto di una versione.

Una nota di metodo che vale la pena lasciare scritta, perché mi ha prodotto un numero sbagliato
prima che il controllo di somma lo smascherasse: in `/proc/<pid>/task/<tid>/stat` il campo `comm`
sta fra parentesi e **`Jit thread pool` contiene spazi**, quindi `awk '{print $14, $15}'` legge due
colonne sbagliate e riporta il thread JIT a **0**. Va tagliata prima la parentesi
(`sed 's/.*) //' | awk '{print $12+$13}'`), e va verificato che la somma per thread torni al totale
di processo. Il totale di processo non è mai stato affetto (il `comm` del processo non ha spazi).

### 2.4 v4.22 contro v4.21: nessun costo misurabile

MISURATO. Differenza delle medie: **−0,55 pt**, cioè **0,49 sd**. Le **due valutazioni per frame
aggiunte dalla v4.22** — l'orario commerciale (`BusinessHours.opennessAt`, un float per frame in
`SceneObjectRenderer`) e il target del conteggio auto (`CarSelection.densityAt` → `countFor`) —
**non hanno un costo rilevabile con questo protocollo**.

Il limite va detto con la stessa chiarezza: con sd ≈ 1,1 pt questo protocollo **non può risolvere
differenze di pochi punti**. Non dice che il costo è zero; dice che è **sotto la risoluzione della
misura**. Per lo stesso motivo **i 3–4 punti dichiarati fra v4.20 e v4.21 restano non testati**:
sarebbero al limite della risoluzione, e comunque avrei dovuto costruire anche la v4.20, cosa che
questo pass non ha fatto.

### 2.5 Nascosto: **0,12%**

MISURATO. A schermo spento il processo scende a **0,12% di un core** (0,08–0,17 su 3 finestre da
60 s). Il motore smette davvero di disegnare: `GlRenderThread` non entra nel loop quando
`visible` è falso. **Il costo esiste solo mentre il wallpaper è effettivamente visibile.**

### 2.6 Il numero è di una build di debug, e la release non è misurabile qui

**Dichiarato esplicitamente, come richiesto.** Tutto sopra è una **build di debug**, la stessa
natura di build della voce 27, quindi la comparabile. Il `Jit thread pool` a 37,22% — più di un
terzo del totale — **è un artefatto della build di debug**, non del disegno.

**Una v4.22 di release non è costruibile su questa macchina, e non l'ho aggirata.**
`app/build.gradle.kts` prende la firma di release **solo** da variabili d'ambiente
(`PAPERSCRAPE_RELEASE_STORE_FILE` e compagne) che il progetto non fornisce di proposito, e senza
firma l'APK non si installa. Il commento nel file dice esplicitamente che una chiave di release non
va generata per conto del maintainer, e non l'ho generata. **Limite segnalato, non aggirato.**

Conseguenza: **ogni conclusione tratta da questi numeri è conservativa**. `CLAUDE.md` §7 registra
un rapporto debug/release di ~120% contro ~28% su questo stesso telefono — DICHIARATO, non
rimisurato qui — e la release del maintainer installata sul device è la **4.15**, non la 4.22,
quindi non è un termine di paragone della versione in esame.

---

## 3. Fase 2 — tolta dal perimetro dal coordinatore

**Non è un impedimento tecnico, ed è importante che il report non lo faccia sembrare tale.** La
Fase 2 è stata **rimossa dal perimetro dal coordinatore**, a misura non iniziata, per questa
ragione, che è sua:

> Il progetto ha **un solo dispositivo, con cella invecchiata**. Un delta in mA fra due bracci
> resterebbe difendibile — l'invecchiamento colpisce capacità e resistenza interna, non i mA
> assorbiti dal SoC — ma le due traduzioni che lo rendevano giudicabile, **percentuale di una
> carica** e **minuti di schermo persi**, non lo sono più, perché la capacità nominale non è
> quella. Senza quelle due resterebbe un numero in mA non più interpretabile del 67% da cui si era
> partiti. **La misura non è impossibile: è inutile allo scopo.**

Non è stato prodotto **nessun numero di batteria**, e non ne va citato nessuno. Un pilota
esplorativo a telefono collegato era stato avviato per caratterizzare lo strumento prima della
decisione: **è stato scartato e non è riportato**, perché a telefono collegato il contatore misura
il saldo netto rispetto al caricabatterie e non un consumo, e comunque la Fase 2 non è più nel
perimetro.

Per completezza di stato, e non come risultato: il device è rimasto **collegato via USB** per tutta
la sessione, e non è raggiungibile via adb WiFi (ping fallito). Una finestra scollegata avrebbe
comunque richiesto un intervento fisico. Nulla di ciò cambia la ragione sopra, che è di merito e
non logistica.

**Fase 3 non aperta**, su indicazione del coordinatore: l'indagine prosegue con un perimetro
diverso, in una sessione nuova. Nessuna profilazione per frame è stata eseguita.

---

## 4. Candidate di ottimizzazione incontrate, elencate e non applicate

Come richiesto, scritte e lasciate stare. **Nessuna è stata misurata e nessuna è raccomandata qui.**

1. **La cadenza.** `FRAME_INTERVAL_MS = 33L` in `GlRenderThread.kt:471` e
   `PaperWallpaperService.kt:40`. Entrambi i loop sottraggono il costo del frame prima di dormire,
   quindi portarla a 50 ms porterebbe la cadenza da ~30 a ~20 fps e taglierebbe il lavoro per frame
   di circa un terzo; è una riga. **Ma è un giudizio dell'occhio** — auto e nuvole si muovono, e a
   20 fps il moto potrebbe leggersi a scatti. La scelta si prende guardando il telefono, e la
   prende il maintainer.
2. **I livelli statici.** Cielo, colline, edifici e alberi sono ridisegnati a ogni frame benché
   cambino solo con `dayBlend`, che si muove nell'arco di minuti. Esiste un atlante di texture GL
   ma nessuna cache dei livelli composti. **Rischio dichiarato:** è una modifica al draw path,
   tocca ogni golden, ed è esattamente la forma di «un refactor grande per un difetto piccolo» che
   il progetto tiene fra i segnali d'allarme.
3. **Il `Jit thread pool` a 37,22%** non è un bersaglio di ottimizzazione del codice: sparisce con
   una build di release. È elencato solo perché è più di un terzo del numero, e chi legge il
   numero deve saperlo.

---

## 5. Documentazione aggiornata

Tre file, tutti e tre solo documentazione:

- **`BACKLOG_v4_22.md`** — nuova **voce 32**, «Il 67–68% della voce 27 è il thread di rendering,
  non il processo», **DOCUMENTED**: protocollo, numeri con dispersione, attribuzione, il limite
  della build di release, e la ragione per cui la Fase 2 è fuori perimetro. Riga aggiunta alla
  tabella di sintesi; l'intestazione ora dice «items 31 and 32 are new to this release».
- **`BACKLOG_v4_21.md`** — **voce 27** aggiornata con una nota datata, **senza riscrivere il testo
  originale**: il 67–68% è ora **MISURATO** ed è il **thread di rendering di una build di debug**;
  i **3–4 punti fra v4.20 e v4.21 restano DICHIARATO**.
- **`CLAUDE.md`** §7 — il gotcha sulla CPU delle build di debug ora porta i numeri misurati, il
  fatto che la release non è costruibile qui, e la trappola di parsing di `comm` con spazi.

---

## 6. Verifica

Il cambiamento è di **Livello 1** per la policy di `CLAUDE.md` §5.1 (solo documentazione non
eseguibile), che non richiede Gradle. È stato comunque eseguito di più, perché serviva alla misura:

- `testDebugUnitTest` — **1331 eseguiti, 0 falliti, 0 errori**.
- `assembleDebug` — **BUILD SUCCESSFUL**, e l'APK risultante è quello installato e misurato.
- Test Python asset — **108 eseguiti, OK**.
- Strumentati — **non eseguiti di proposito** (vedi §0).
- Diff dei due archivi — **0 aggiunte, 0 rimozioni, 3 modifiche**, elencate in §5.

---

## 7. Stato del device alla consegna

OSSERVATO, verificato prima della chiusura:

| voce | stato |
|---|---|
| APK del wallpaper | debug **v4.22 / versionCode 53**, SHA-256 `9c684dd4e36c31a8de432388d1418de20812f768e100055ce00037900774c868`, **`pull` byte-identico** |
| wallpaper attivo | `com.paperscrape.livewallpaper.debug/…PaperWallpaperService` sull'**home**, **osservato mentre rende** (scena di giorno, 09:02, sole, traffico, persone) |
| lock screen | `com.android.systemui/…ImageWallpaper` — **come all'inizio** |
| tema | **Autumn**, mai cambiato |
| release del maintainer | `com.paperscrape.livewallpaper` **4.15 / 46**, intatta, non toccata |
| pacchetto di test | **disinstallato** (era installato a inizio sessione, come previsto dal pass precedente) |
| `screen_off_timeout` | **120000**, ripristinato |
| `screen_brightness_mode` | **1 (automatico)**, ripristinato |
| `stay_on_while_plugged_in` | **2**, ripristinato |
| `airplane_mode_on` | **1**, mai toccato |
| script e file spinti sul device | **rimossi** (`/data/local/tmp/*.sh`, `/sdcard/Pictures/paperscrape_still.png` e la sua riga MediaStore) |

I valori originali erano stati **annotati prima** di essere cambiati:
`screen_off_timeout=120000`, `screen_brightness_mode=1`, `screen_brightness=33`,
`stay_on_while_plugged_in=2`, `airplane_mode_on=1`.

Due cose da dire con precisione, perché non tornerebbero da sole:

- **`screen_brightness` legge 54, non 33.** La luminosità automatica è stata riattivata (era il suo
  stato originale) e ha subito riportato il valore a quello che decide lei. Il **regime** è
  ripristinato; il numero è gestito dal sistema, e valeva 33 al momento dell'annotazione solo
  perché era quello che l'automatico aveva scelto allora.
- **Il device non è più raggiungibile via adb e serve un ricollegamento fisico del cavo.** Durante
  la sessione avevo abilitato `adb tcpip 5555` per valutare una finestra scollegata; per rimetterlo
  in modalità USB ho eseguito `adb usb`, e la ri-enumerazione del gadget USB ha fatto sparire il
  telefono dal bus (`lsusb` non lo vede più). **Tutte le verifiche di stato qui sopra sono state
  fatte prima di quel comando**, wallpaper vivo osservato compreso; nulla è rimasto da verificare
  dopo. Si risolve **staccando e riattaccando il cavo**. `adb tcpip` non sopravvive a un riavvio.

---

## 8. Artefatto

| | |
|---|---|
| file | `PaperScrape_v4_22.zip` |
| SHA-256 | `37ec53cf13542e4b2fd0ae2d116dae4e9433fac8199ee8a0064c1b8da862acc9` |
| byte | 5 708 828 |
| voci | **881** |
| sostituisce | `8bd2a63884eb641567f5cd884a7d2fde758eda05fb98cb1ab7c3c82216f2cf03`, 5 723 522 byte, 881 voci |

Stesso numero di voci: nessun file aggiunto, nessuno rimosso, tre modificati. `versionCode 53` /
`versionName "4.22"` invariati.

**Etichetta della consegna: documentazione, non una release.** Pubblicazione, tag e release
restano al maintainer e non sono state fatte.
