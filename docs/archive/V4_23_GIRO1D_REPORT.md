# PaperScrape v4.23 — Giro 1d: V2 rifinita, la zucca da capo, `moon_full` a passo fine, le stelle

**Consegna: CONCEPT IN ATTESA DI GIUDIZIO, NON UNA RELEASE.** Finisce con le fotografie e con una
diagnosi. Decisioni del maintainer, non in discussione: **B «Forbici»**, alone **V2**. Questo giro
mette a punto V2 su due parametri, rifà la zucca da capo, chiude il residuo di `moon_full`, e sulle
stelle consegna una diagnosi con tre configurazioni del renderer fotografate da build locali
usa-e-getta — **nessuna modifica a `app/src/main` è entrata nello ZIP** (verificato: il file
`PaperRenderer.kt` nell'archivio è byte-identico a quello dello ZIP di partenza).

`sun_body` di B, sprite spediti, golden, cancelli, tolleranze, versione (54/"4.23"): non toccati.
Etichette: **OSSERVATO / MISURATO / DEDOTTO / DICHIARATO**, **NON ATTRIBUIBILE** dove serve.

---

## 0. Baseline — verificata, condizione D non scattata

MISURATO su estrazione pulita dello ZIP del giro 1c (`/home/bober/claude-shit/work_v4_23d/`):

| voce | atteso | osservato |
|---|---|---|
| ZIP di partenza SHA-256 | `57de2bc1…c0cd3a1` | identico |
| byte / voci | 6 095 779 / 952 | 6 095 779 / 952 |
| `versionCode` / `versionName` | 54 / "4.23" | 54 / "4.23" (non toccati) |
| JVM | 1331 | **1331, 0 falliti, 0 errori** |
| strumentati | 148 | **148, 0 falliti** (`am instrument`, suite intera, `OK (148 tests)`) |
| Python | 108 | **108, OK**; probe `matches_expected: True` |
| PNG golden / `@Ignore` | 27 / 0 | 27 (3 `gl-*`) / 0 |

Gate `PIXEL_IDENTICAL` non rifatto, come da mandato.

---

## 1. V2 rifinita — due parametri, non un ridisegno (`tools/assets/concepts/b/alone/v2_rifinita/`)

V2 oggi (OSSERVATO nel file): anello `#F7CE64` a compasso, corona 154–166, **opacità 0,75**;
gradiente radiale **0,40 / 0,16 → 0**. Tre varianti che cambiano **solo** i due parametri del
mandato; geometria, colore dell'anello e assenza d'ombra invariati; `sun_body` non toccato.

| variante | opacità anello | gradiente |
|---|---|---|
| **a «anello pieno, alone com'è»** | **1,0** | 0,40 / 0,16 → 0 (`#F7CE64` → `#F0A03C`), invariato |
| **b «anello pieno, senza alone»** | **1,0** | **assente** |
| **c «anello pieno, alone riscaldato»** | **1,0** | **0,55 / 0,28 → 0** (`#FBE289` → `#F0A03C` → `#E08A2E`) |

Il termine di paragone a **0,75** è la cattura `giorno_alone_v2.png` del giro 1b, presa nelle stesse
condizioni, e sta nel foglio: non è stato rifatto. Le tre a 1,0 chiudono la questione di coerenza
interna (l'anello era l'unico velo in un concept di carta opaca) e isolano il gradiente: **b** dice
se, con l'anello pieno, l'alone serva ancora; **c** se, servendo, vada riscaldato perché non
desaturi in grigio sul blu.

**In scena (OSSERVATO, `catture/giorno_v2_rifinita_{a,b,c}.png`, e nel foglio a dieci soli
accanto alla V2 a 0,75 del giro 1b):** a piena opacità l'anello è **giallo pieno e netto**, e la
«sbavatura grigia» sparisce in tutte e tre — la diagnosi del coordinatore (opacità + tinta calda su
blu freddo) è confermata dal contrario: alzata l'opacità, il grigio non c'è più. Fra le tre, a
occhio: **b** (senza alone) è la più «carta ritagliata» — anello e disco su cielo pulito; **a** e
**c** aggiungono un alone che ora, dietro un anello pieno, non desatura ma resta un velo sotto un
oggetto opaco; **c** più caldo e più presente di **a**. La scelta è del maintainer.

**Un errore di procedura, dichiarato.** Le prime tre catture sono state prese col tema **Sunset**
(il tap sulla galleria era mancato); accorto dal cielo azzurro saturo e dagli alberi verdi, ho
impostato Autumn, spento le nuvole *sotto Autumn* (le impostazioni sono per tema) e **rifatto le
tre catture**: quelle in consegna sono Autumn, alla pari con i giri 1b e 1c.

---

## 2. La zucca, da capo (`tools/assets/concepts/b/svg/moon_jack_o_lantern.svg`)

**Conservato il dispositivo**: è l'unica luna del progetto coi fori passanti, e a ~100 px deve dire
«Halloween» a colpo d'occhio. **Ridisegnato tutto il resto, in B:**

- bordo tagliato a mano (40 vertici, ±2,2 — il passo della famiglia);
- **fori tagliati a mano**: due occhi a cuneo asimmetrici inclinati verso il naso, un naso a
  triangolo storto, **una bocca larga unica** con **quattro zanne** che scendono dal labbro
  superiore — tutti poligoni con tremolio ±1,4–1,6, come ogni bordo del concept;
- i fori sono grandi apposta: sul disco da 158 px di questo schermo gli occhi sono ~30×20 px e
  la bocca ~100×38 px (DEDOTTO dalla geometria, OSSERVATO nella cattura).

**Nessuna coordinata viene dalla faccia spedita** (MISURATO per confronto dei sorgenti): la faccia
spedita aveva occhi a quadrilatero `48,78 / 99,108 / 90,123 / 54,117` (×3), una bocca a curve
cubiche `C` con **denti come sei fori separati**; la nuova ha numeri diversi, forme diverse e **un
solo foro** per la bocca con le zanne ricavate dal suo profilo. Non è una mossa minima: è un
disegno nuovo che tiene il dispositivo.

**L'anello esterno: no, e il perché.** La zucca spedita usava l'anello grigio per dare profondità a
un disco a compasso; nel ridisegno il bordo tagliato e i fori irregolari portano l'identità da soli,
e B è «un foglio, un taglio»: un secondo foglio attorno sarebbe una terza generazione nello stesso
sprite. **Una zucca sola** in consegna; `zucca_anello/` del giro 1c è stato **rimosso**
dall'albero. Maschera neutra, media pixel opachi **244,1** (MISURATO).

**In scena (OSSERVATO, `catture/notte_zucca_nuova.png`, Halloween attivo, 1:00):** sul disco da
158 px la zucca dice Halloween a colpo d'occhio — due occhi a cuneo, naso, la bocca larga con le
zanne — e il bordo tagliato a mano e i fori irregolari sono della stessa mano: non ci sono più due
generazioni nello stesso sprite.

---

## 3. `moon_full` a passo fine (`tools/assets/concepts/b/svg/moon_full.svg`)

Il residuo dichiarato nel giro 1c: `moon_full` a 28 vertici (12,9°) mentre il resto della famiglia
sta a 8,6–9,0°. Chiuso: **40 vertici, 9,0°/faccia, ±2,2**, crateri invariati, media **242,9**
(MISURATO). Ora tutta la famiglia B ha lo stesso passo di taglio.

**In scena (OSSERVATO, `catture/notte_luna_piena_40v.png` contro
`consegna_v4_23_giro1b/catture/b_autumn_notte_0100_luna_piena.png`, stesse condizioni):** le facce
più fini non peggiorano la lettura — il disco resta un cerchio tagliato a mano, il tremolio si vede
meno «a scalini» e più «a carta»; i crateri identici.

---

## 4. Le stelle — diagnosi e opzioni

### La diagnosi (i numeri del coordinatore, riusati; la lettura è mia)

- Solo **1 stella su 5** è lo sprite (`STAR_SPARKLE_EVERY = 5`); le altre quattro sono **cerchi
  disegnati dal codice** (`radius × 0,55`, `#FFF6DC`). L'artwork governa **il 20 % delle stelle**.
- La scintilla più grande è **~10 px** (raggio max 5,6 × 0,9375 × 2) da uno sprite 180×180:
  riduzione ~16×. Il ritaglio 3× del giro 1b: 5–7 px reali, legge come una pallina.
- La scintilla **ruota**, pulsa ed è **senza tinta** (`theme.starColor` non la raggiunge).

**Conclusione (DEDOTTO, poi verificato in scena — sotto): l'artwork non è la leva.** A 10 px
qualunque scintilla, per quanto ben tagliata, si riduce a una macchia chiara di 5–7 px; e l'80 %
delle stelle non passa nemmeno dallo sprite. Perciò **§4a: nessun ridisegno di `star_sparkle`**
in consegna — lo sprite B del giro 1a resta com'è — invece di un disegno più bello che nessuno
vedrebbe. La leva sta nelle quattro costanti di `PaperRenderer`, fuori perimetro, e va mostrata.

### §4b — tre configurazioni, build locali usa-e-getta, fotografate

Ciascuna è **un solo cambiamento** rispetto al codice spedito, applicato con `sed` su una copia
di `PaperRenderer.kt`, costruito, e **il file ripristinato byte-identico** dopo (verificato con
`cmp`; i diff sono in `consegna_v4_23_giro1d/patch_stelle*.diff`, fuori dallo ZIP). Sprite: B con
V2 rifinita «b». Tema Autumn, 1:00, nuvole spente.

| config | costante | spedito → provato | che cosa cambia in scena |
|---|---|---|---|
| **1 «scintille più grandi»** | scala del blit della scintilla `s = radius / 32f` (+ `MAX_STAR_RADIUS_PX` ×2 per l'estensione delle tile) | `/32` → **`/16`** | la scintilla passa da ~10 a **~21 px** massimi; i punti non cambiano |
| **2 «scintille più frequenti»** | `STAR_SPARKLE_EVERY` | **5 → 2** | da ~14 a ~35 scintille su 70 |
| **3 «punti più brillanti»** | `STAR_POINT_RADIUS_SCALE` e `STAR_POINT_COLOR` | **0,55 → 0,85** e **`#FFF6DC` → `#FFFFFF`** | i 56 punti crescono del 55 % e diventano bianco pieno |

**La derivazione scritta nel codice, rispettata o contestata.** Il commento di `STAR_SPARKLE_EVERY`
dice che cinque «keeps roughly a dozen sparkles… without the field looking like a repeated motif».
La config 2 la contraddice di proposito, e l'argomento per superarla sarebbe: **lo sprite B non è
più identico a se stesso** — le quattro punte sono diseguali (82/78/74/76) e ogni scintilla ruota
con la propria fase, quindi trentacinque copie non sono trentacinque copie dello stesso motivo. Se
in scena il campo legge comunque come motivo, la derivazione regge e la config 2 va scartata: la
risposta è nella cattura, non nell'argomento. Le config 1 e 3 non contraddicono nessuna derivazione
scritta: nessun commento fissa il raggio o il peso del punto come scelta motivata.

**In scena (MISURATO sulla stessa regione 540×370 px di cielo, luna esclusa, in tutte e quattro le
catture — `anteprime/confronto_stelle.png`, ingrandimento 3× NEAREST):**

| config | stelle nel ritaglio | le 5 più grandi (px reali) | lettura (OSSERVATO) |
|---|---|---|---|
| **0 spedito** | 14 | 6, 6, 6, 6, 6 | tutto pallina; scintille indistinguibili dai punti |
| **1 scintille ×2** (`/16`) | 14 | **13, 13, 10, 7, 7** | **le scintille tornano forme**: a 10–13 px le quattro punte si vedono, e si vede che sono diseguali (B) — la sola config in cui l'artwork conta |
| **2 EVERY 5→2** | 13 | 6, 6, 6, 6, 6 | più segni piccoli, nessuno leggibile: a 6 px una scintilla in più è un punto in più; **la derivazione del codice regge per una ragione diversa da quella scritta** — non «motivo ripetuto» ma «nessun guadagno»: non vale il costo di sei blit in più |
| **3 punti 0,85, bianco** | 13 | **10, 10, 9, 9, 8** | i punti crescono e sbiancano: il cielo è più «stellato», ma è un cielo di dischi; nessuna scintilla in più |

**Condizione N — la risposta.** Una configurazione migliora la lettura in modo visibile: la **1**,
e migliora proprio la cosa che l'artwork governa — la scintilla diventa una scintilla. È anche la
sola che renderebbe sensato un ridisegno dello sprite in fase 2, perché a 13 px il taglio a mano di
B si vede. La 3 cambia il carattere del cielo, non la leggibilità dello sprite. La 2 non cambia
niente di visibile. **Nessuna delle tre è scelta qui**: sono decisioni su `app/src/main`, del
maintainer, per la fase 2 — e si prendono guardando il foglio.

---

## 5. Le catture

Nuvole spente, Autumn, ora fissa 12:00 / 1:00, `screencap` a schermo intero dal wallpaper vivo sul
BV6600 — le condizioni dei giri 1b e 1c. I sette soli precedenti **non sono stati rifatti**.

| file | condizione |
|---|---|
| `giorno_v2_rifinita_{a,b,c}.png` | le tre V2 rifinite (alberi v4.21 nello stesso fotogramma) |
| `notte_luna_piena_40v.png` | `moon_full` a 40 vertici, fasi realistiche spente — anche **config 0** delle stelle |
| `notte_zucca_nuova.png` | la zucca da capo, Halloween attivo (poi disattivato) |
| `notte_stelle1-grandi.png`, `notte_stelle2-frequenti.png`, `notte_stelle3-punti.png` | le tre configurazioni del renderer, stessa scena, stessi sprite (B + V2 rifinita b) |

`anteprime/confronto_dieci_soli.png`: i sette soli dei giri 1b/1c + le tre V2 rifinite.
`anteprime/confronto_stelle.png`: la stessa regione di cielo (540×370 px, luna esclusa) per le
quattro configurazioni, ingrandita 3× NEAREST, con conteggio e dimensioni reali misurate.
Montaggi di catture reali, dichiarati come tali; il giudizio si prende sui file di `catture/`.

---

## 6. Contabilità

MISURATO su estrazione pulita dello ZIP di partenza; i conteggi d'archivio **derivati dal diff
albero-vs-ZIP fatto prima di scriverli** e riverificati sull'archivio finale:

- **JVM 1331/0/0**, **strumentati 148/0**, **Python 108/0**, probe conforme, **27 golden**, `@Ignore` 0.
- **Voci archivio: 957** = 952 (baseline) **+ 8 nuove − 3 rimosse**. Nuove: `V4_23_GIRO1D_REPORT.md`;
  `tools/assets/concepts/b/alone/v2_rifinita/{a,b,c}/sun_glow.{svg,png}` (6); `…/v2_rifinita/varianti.json`.
  Rimosse: `tools/assets/concepts/b/zucca_anello/{moon_jack_o_lantern.svg,moon_jack_o_lantern.png,variante.json}`
  (la questione dell'anello è sciolta: una zucca sola).
- **6 modificati** (stesso path): `concepts/b/svg/moon_full.{svg,png}` (40 vertici),
  `concepts/b/svg/moon_jack_o_lantern.{svg,png}` (da capo), `concepts/b/sprites.concept.json`,
  `concepts/b/DESCRIZIONE.md`.
- `versionCode`/`versionName`: **54/"4.23", non toccati**. `app/src/main`: **identico** allo ZIP
  di partenza (`PaperRenderer.kt` verificato per hash nel diff).

---

## 7. Stato del dispositivo

**OSSERVATO alla riconsegna (2026-09-06, ~13:28 CEST).**

- **Wallpaper attivo:** `com.paperscrape.livewallpaper` — la **release del maintainer**, mai toccata
  né disinstallata — rimessa come wallpaper vivo e **osservata mentre rende** (`mEngine` legato,
  scena a ora reale sul launcher).
- **Build sperimentali disinstallate:** `pm list packages` mostra solo il pacchetto release. Le sei
  build del giro (`app-v2-{a,b,c}.apk`, `app-stelle{1,2,3}-*.apk`, tutte `.debug`) erano costruite da
  questo albero con gli otto sprite B verificati byte-per-byte contro lo staging della pipeline
  (**48/48**) prima dell'installazione; le tre `stelle*` portavano una patch locale a
  `PaperRenderer.kt` che **non è nell'archivio** (file ripristinato e verificato con `cmp`, e
  riverificato nel diff degli archivi: identico allo ZIP di partenza).
- **Stato originale annotato e ripristinato:** `screen_brightness_mode` 1 (auto),
  `screen_brightness` 112, `screen_off_timeout` 60 000; `auto_time` 1 — **mai cambiato** in questo
  giro (nessuna fase da forzare: luna piena con le fasi realistiche spente, zucca sempre piena).
- Halloween attivato solo per la cattura della zucca e disattivato subito dopo (verificato a
  schermo); il DataStore della build `.debug` è comunque sparito con la disinstallazione.

---

## 8. File toccati (dal diff dei due archivi, fatto PRIMA di scrivere questi numeri)

**8 file aggiunti, 3 rimossi, 6 modificati** (diff dei due archivi, `57de2bc1…` → questa consegna).

*Aggiunti (8):* `V4_23_GIRO1D_REPORT.md`; `tools/assets/concepts/b/alone/v2_rifinita/a/sun_glow.{svg,png}`,
`…/b/sun_glow.{svg,png}`, `…/c/sun_glow.{svg,png}`, `…/v2_rifinita/varianti.json`.

*Rimossi (3):* `tools/assets/concepts/b/zucca_anello/moon_jack_o_lantern.svg`, `….png`, `…/variante.json`.

*Modificati (6):* `tools/assets/concepts/b/svg/moon_full.svg`, `.png`;
`tools/assets/concepts/b/svg/moon_jack_o_lantern.svg`, `.png`; `tools/assets/concepts/b/sprites.concept.json`;
`tools/assets/concepts/b/DESCRIZIONE.md`.

**Nulla fuori da `tools/assets/concepts/b/` è cambiato** oltre al report in radice; in particolare
**nessun file sotto `app/src/main`** — le patch delle build sperimentali non sono nell'archivio
(sono i tre `patch_stelle*.diff` accanto allo ZIP).

---

## 9. Che cosa NON è stato fatto, di proposito

- Nessuna scelta fra le varianti di V2 né fra le configurazioni delle stelle: sceglie il maintainer.
- Nessun ridisegno di `star_sparkle` (§4a, con la ragione scritta).
- `app/src/main` intatto nell'archivio; le tre build sperimentali sono strumenti di misura e non
  sono in consegna (solo i loro diff, accanto allo ZIP).
- `sun_body` di B, sprite spediti, golden, cancelli, versione: intatti. Gate non rifatto.
