# PaperScrape v4.23 — Giro 1c: la corona attaccata

**Consegna: CONCEPT IN ATTESA DI GIUDIZIO, NON UNA RELEASE.** Giro corto, una domanda: *una corona
smerlata che esce dal bordo del disco legge come sole?* Due varianti che differiscono per **una**
cosa sola, fotografate nelle **stesse identiche condizioni** del giro 1b e messe in fila con i cinque
soli già in mano al maintainer. Più le due piccole cose sulle lune. Finisce con le fotografie.

Concept scelto: **B «Forbici»**, non in discussione. `sun_body` di B, la stella, gli sprite
spediti, i golden, i cancelli, la versione (54/"4.23"): **non toccati**.

Etichette: **OSSERVATO / MISURATO / DEDOTTO / DICHIARATO**, **NON ATTRIBUIBILE** dove serve.

---

## 0. Baseline — verificata, condizione D non scattata

MISURATO su estrazione pulita dello ZIP del giro 1b (`/home/bober/claude-shit/work_v4_23c/`):

| voce | atteso | osservato |
|---|---|---|
| ZIP di partenza SHA-256 | `52b654c1…58f400f` | identico |
| byte / voci | 6 042 081 / 943 | 6 042 081 / 943 |
| `versionCode` / `versionName` | 54 / "4.23" | 54 / "4.23" (non toccati) |
| JVM | 1331 | **1331, 0 falliti, 0 errori** |
| strumentati | 148 | **148, 0 falliti** (`am instrument`, suite intera, `OK (148 tests)`) |
| Python | 108 | **108, OK**; probe `matches_expected: True` |
| PNG golden / `@Ignore` | 27 / 0 | 27 (3 `gl-*`) / 0 |

Gate `PIXEL_IDENTICAL` non rifatto, come da mandato.

---

## 1. La corona attaccata — due varianti (`tools/assets/concepts/b/corona/`)

**La derivazione del raggio interno, scritta (MISURATO sui sorgenti).**

- Nel `sun_glow` **spedito** gli otto raggi hanno la base a **r = 110,6–115,0** (spazio 396) e le
  punte a 192–195; il disco spedito ha r = 34×3 = **102**. Cioè i raggi *non* partono dal bordo:
  partono 9–13 unità fuori, e quel vuoto lo copre la zona più densa del gradiente radiale (0,55 di
  giallo). La geometria «risolta» è risolta dal gradiente.
- Il disco di **B** è un taglio a mano: il suo poligono sta fra **r = 96,6 e 101,4** (28 vertici,
  ±2,4). B non ha il gradiente a coprire vuoti, quindi la corona deve essere attaccata **per
  costruzione**:
  - **base della corona a r = 94** — sotto il *minimo* del disco (96,6): nessun pixel di cielo può
    passare fra disco e corona in nessun punto del bordo; il disco, disegnato sopra, copre la
    cucitura;
  - **valli degli smerli a r = 104** — sopra il *massimo* del disco (101,4): il fondo di ogni
    smerlo resta visibile tutto attorno, così la corona *esce* dal bordo invece di sparirci sotto.
- Vive in `sun_glow` (tela 396), come i raggi hanno sempre fatto; `sun_body` (contenuto 203×206 in
  240, 13 px di margine) non potrebbe ospitarla e non è stato toccato.

**Le due varianti — un solo parametro cambia:**

| | lobi | base / valli | **picchi** | gradiente | ombra |
|---|---|---|---|---|---|
| **«Smerli bassi»** | 16, arrotondati (seno), ±2,0 a mano | 94 / 104 | **r = 132** (profondità 28) | no | sì (+6,+8, 13 %) |
| **«Smerli alti»** | 16, identici | 94 / 104 | **r = 156** (profondità 52) | no | sì |

Le scelte comuni, scritte:

- **Colore e carta.** La corona è il giallo del cuore del sole (`#F7CE64`) a carta **opaca**: un
  secondo foglio *sotto* il disco. Non lo stesso arancio del disco, perché un foglio unico
  cancellerebbe il bordo tagliato a mano che il maintainer ha scelto; con due fogli l'ombra del
  disco cade sulla corona come deve.
- **Gradiente: no, in entrambe.** Nel giro 1b, su cielo pulito e dietro una forma netta, leggeva
  come sbavatura grigia; qui la corona *è* la presenza attorno al disco. Il mandato chiedeva che
  almeno una provasse senza; siccome le due devono differire per una cosa sola, senza tutte e due.
- **Ombra: sì, in entrambe** — la stessa del disco (+6,+8, nero 13 %). È la firma di B, e sulla
  corona spessa del giro 1b si leggeva. Con la corona attaccata l'ombra della corona più l'ombra
  del disco compongono l'ombra dell'**unione**, che è ciò che avrebbe un unico ritaglio. Artefatto
  atteso e da guardare nelle catture (OSSERVATO nei render): l'ombra *del disco* cade sui lobi in
  basso a destra — corretto per due fogli sovrapposti, e sarebbe sbagliato solo se i due fossero
  lo stesso foglio.

**Condizione M — la convergenza, dichiarata.** Guardando i render prima ancora delle catture:
**«Smerli alti» converge su un fiore** — sedici lobi lunghi e arrotondati attorno a un disco
arancione con cuore giallo sono, in silhouette, un **girasole**. Non viene consegnata come cosa
nuova: viene consegnata come *il punto della retta oltre il quale la corona smette di essere un
sole*, ed è utile proprio per questo. «Smerli bassi» resta dalla parte del sole di carta
(lobi larghi quanto profondi); se al maintainer, guardando la scena, leggesse come ingranaggio,
la retta va percorsa ancora verso il basso — ma quello è un giro dopo, con la sua fotografia.

**Vista in scena (OSSERVATO nelle catture):** la convergenza regge — a schermo, con gli alberi
sotto, «smerli alti» è un girasole in cielo; «smerli bassi» legge come un sole di carta smerlato,
e nessuna delle due come ingranaggio o come il sole spedito con le punte arrotondate. L'ombra
dell'unione disco+corona si legge in basso a destra e non impasta i lobi.

Vincoli tecnici invariati: tela 396, `CANVAS_PIXELS`, origine −198, `FIXED_ART` con colore reale
→ nessun file Kotlin cambia; batteria JVM degli sprite verde con ciascun set al posto degli
spediti (MISURATO: set A = smerli bassi + zucca senza anello, set B = smerli alti + zucca con anello, entrambi PASS; set spediti poi ripristinato e riverificato byte-per-byte).

---

## 2. Le due piccole cose sulle lune

**3a — la faccettatura, prima misurata poi armonizzata.** Il mandato dice che il poligono della
zucca ha spigoli più larghi di `moon_full`. **MISURATO sui sorgenti di B**, passo del lembo
(gradi per faccia, r = 99):

| sprite | vertici sul lembo | passo |
|---|---|---|
| `moon_full` | 28 | **12,9°** |
| `moon_jack_o_lantern` (giro 1b) | 28 | **12,9°** |
| `moon_half` | 20 per 180° | 9,0° |
| `moon_crescent` | 24 per 180° | 8,7° |
| `moon_gibbous` | 24 per 180° | 8,6° |

Zucca e luna piena avevano **lo stesso passo** (28 vertici, 22,2 unità per faccia): la differenza
vista dal coordinatore fra quelle due **NON È ATTRIBUIBILE** al passo di taglio (candidati non
verificati: la tinta arancione ad alto contrasto, i tratti dritti della bocca che «allungano»
l'occhio). **L'incoerenza reale dentro il concept è un'altra**: `moon_full` e la zucca a 12,9°
contro le tre fasi a 8,6–9,0°. Come chiesto, la zucca è stata portata al passo delle altre lune —
**40 vertici, 9,0°/faccia, ±2,2** — in entrambe le versioni. `moon_full` resta a 28 (non è nei due
file del mandato) ed è **la residua da decidere in fase 2**.

**3b — l'anello esterno, due versioni, sceglie il maintainer.**
- `svg/moon_jack_o_lantern.svg` — **senza anello**: un foglio, un taglio, tono unico `#F4F4F4`,
  media pixel opachi **244,1**.
- `zucca_anello/moon_jack_o_lantern.svg` — **con anello**: fascia esterna `#C8C8C8` da r = 99 a
  r = 87, entrambi i bordi tagliati a mano a 40 vertici, sotto il foglio chiaro; due toni invece dei
  tre dello spedito (`#b4b4b4`/`#dcdcdc`/`#ffffff`). Media **232,1** (≥ 220, MISURATO).

In entrambe la faccia è quella spedita, ×3, invariata: **i fori restano**, e restano solo qui.
**La stella non è stata toccata** (5–7 px reali: nessuna scelta sarebbe visibile).

---

## 3. Le catture — stesse condizioni del giro 1b

Nuvole spente, Autumn, ora fissa 12:00 (giorno) / 1:00 (notte), dal wallpaper vivo sul BV6600,
`screencap` a schermo intero — le stesse condizioni del giro 1b, quindi comparabili con ciò che il
maintainer ha già in mano. Le cinque catture di giorno del giro 1b **non sono state rifatte**.

| file | condizione |
|---|---|
| `giorno_corona_smerli_bassi.png` | corona attaccata, picchi r=132 (alberi v4.21 nello stesso fotogramma) |
| `giorno_corona_smerli_alti.png` | corona attaccata, picchi r=156 (idem) |
| `notte_zucca_con_anello.png` | Halloween attivo, zucca B con anello esterno |
| `notte_zucca_senza_anello.png` | Halloween attivo, zucca B a tono unico |

`anteprime/confronto_sette_soli.png` mette in fila i **sette soli** (riferimento v4.22, B a petali,
V1, V2, V3 del giro 1b, e le due corone) — montaggio di catture reali, dichiarato come tale;
`anteprime/confronto_zucche.png` affianca le due zucche. Il giudizio si prende sui file di
`catture/`, a schermo intero.

---

## 4. Contabilità

MISURATO su estrazione pulita dello ZIP di partenza:

- **JVM 1331/0/0**, **strumentati 148/0**, **Python 108/0**, probe conforme.
- **PNG golden 27** (24 Canvas + 3 GL), `@Ignore` 0.
- **Voci archivio: 952** = 943 (baseline) + 9 nuove − 0 rimosse. Le nuove:
  `V4_23_GIRO1C_REPORT.md`; `tools/assets/concepts/b/corona/{smerli_bassi,smerli_alti}/sun_glow.{svg,png}` (4);
  `tools/assets/concepts/b/corona/varianti.json`; `tools/assets/concepts/b/zucca_anello/moon_jack_o_lantern.{svg,png}` (2);
  `tools/assets/concepts/b/zucca_anello/variante.json`.
- Modificati (stesso path): `tools/assets/concepts/b/svg/moon_jack_o_lantern.{svg,png}` (faccettatura
  armonizzata), `tools/assets/concepts/b/sprites.concept.json`, `tools/assets/concepts/b/DESCRIZIONE.md`.
- `versionCode`/`versionName`: **54/"4.23", non toccati**.

---

## 5. Stato del dispositivo

**OSSERVATO alla riconsegna (2026-09-06, ~11:59 CEST).**

- **Wallpaper attivo:** `com.paperscrape.livewallpaper` — la **release del maintainer**, mai
  toccata né disinstallata — rimessa come wallpaper vivo e **osservata mentre rende** (`mEngine`
  legato, scena a ora reale sul launcher).
- **Build concept disinstallata:** `pm list packages` mostra solo il pacchetto release. Le due build
  del giro (`app-corona-A/B.apk`, `.debug`) erano costruite da questo albero con gli otto sprite B
  verificati byte-per-byte contro lo staging della pipeline (**16/16**) prima dell'installazione.
- **Stato originale annotato e ripristinato:** `screen_brightness_mode` 1 (auto),
  `screen_brightness` 12, `screen_off_timeout` 60 000; `auto_time` 1 — **mai cambiato** in questo
  giro (nessuna fase lunare da forzare: la zucca è sempre piena).
- Halloween era attivo solo nel DataStore della build `.debug`, sparito con la disinstallazione.

---

## 6. File toccati (dal diff dei due archivi)

Derivata dal **diff dei due archivi** (`52b654c1…` → questa consegna): **9 file aggiunti,
0 rimossi, 4 modificati.**

*Aggiunti (9):* `V4_23_GIRO1C_REPORT.md`; `tools/assets/concepts/b/corona/smerli_bassi/sun_glow.{svg,png}`;
`…/corona/smerli_alti/sun_glow.{svg,png}`; `…/corona/varianti.json`;
`…/zucca_anello/moon_jack_o_lantern.{svg,png}`; `…/zucca_anello/variante.json`.

*Modificati (4):* `tools/assets/concepts/b/svg/moon_jack_o_lantern.svg` e `.png` (40 vertici),
`tools/assets/concepts/b/sprites.concept.json` (riquadro e nota della zucca),
`tools/assets/concepts/b/DESCRIZIONE.md` (appendice giro 1c).

**Nulla fuori da `tools/assets/concepts/b/` è cambiato** oltre al report in radice: sprite spediti,
`sources/svg/`, golden, `SettingsGates`, `app/src/main`, `build.gradle.kts` byte-identici allo ZIP
di partenza (verificato dal diff).

---

## 7. Che cosa NON è stato fatto, di proposito

- Nessuna terza idea di corona; nessuna scelta fra le due varianti né fra le due zucche.
- `sun_body` di B, `moon_full` di B (fuori dai due file), la stella: non toccati.
- Sprite spediti, golden, cancelli, tolleranze, versione, `app/src/main`: intatti.
- Gate `PIXEL_IDENTICAL` non rifatto; `lintDebug` non eseguito (giro di fotografie).
