# PaperScrape v4.23 — Fase 2, Parte B: la promozione degli sprite celesti

**Consegna: v4.23 preparata, NON pubblicata, NON approvata, e NON verificata da me.** `versionCode 54`,
`versionName "4.23"` — già impostati nella fase 1, **non toccati** qui. Nessun tag, nessun push,
nessuna release, nessun upload. La verifica finale è del maintainer, su un'estrazione pulita, con
`:app:testDebugUnitTest` e `:app:connectedDebugAndroidTest`.

Questo pass **non disegna**. Promuove ciò che è stato scelto, applica la configurazione 1 al
renderer, e rigenera i golden che si sono mossi. Tocca codice spedito, quindi la contabilità torna
intera (§9).

Etichette: **OSSERVATO / MISURATO / DEDOTTO / DICHIARATO**, **NON ATTRIBUIBILE** dove serve.

---

## 0. Baseline — verificata, condizione D non scattata

MISURATO su estrazione pulita dello ZIP della Parte A in `/home/bober/claude-shit/work_v4_23f/`:

| voce | atteso dal §0 del mandato | osservato |
|---|---|---|
| ZIP di partenza SHA-256 | `236f8256…cdb86f21` | **identico** |
| byte / voci | 6 180 834 / 963 | **6 180 834 / 963** |
| `versionCode` / `versionName` | 54 / "4.23" | 54 / "4.23" — **non toccati** |
| JVM | 1331 | **1331, 0 falliti, 0 errori, 0 saltati** (dall'XML) |
| strumentati | 148 | **148** — eseguiti per intero a fine pass, §6 |
| Python | 108 | **108, OK** (venv `paperscrape-assets`); probe `matches_expected: True`, zlib-ng 1.3.1 |
| PNG golden | 27 | **27** = 24 Canvas + 3 `gl-*` |
| `@Ignore` | 0 | **0** — i tre `grep` che colpiscono sono commenti che raccontano un `@Ignore` passato |

**Gate `PIXEL_IDENTICAL` — eseguito una volta, dopo la promozione: `PIXEL_IDENTICAL: 134` su 134.**
La condizione A non scatta. L'ho eseguito *dopo* e non *prima* perché è lì che ha valore di prova
per questo pass: dimostra che gli otto PNG spediti sono esattamente ciò che i loro SVG committati
producono, e che gli altri 126 non si sono mossi. `compare` riscrive
`tools/assets/reports/{fidelity.json,fidelity.md,comparison-sheet.png}` e stavolta **quei file
dovevano muoversi**, perché l'artwork è cambiato: sono aggiornati, non ripristinati.

`sprites-examples.zip` in `/home/bober/claude-shit` **non è nell'archivio consegnato** — verificato
sull'elenco delle voci dello ZIP finale, §9. Non l'ho aperto in questo pass: qui non si disegna.

---

## 1. B1 — gli otto sprite promossi

**Attraverso la pipeline, non copiando i PNG di lavoro.** Gli otto SVG del concept sono stati
installati sotto `tools/assets/sources/svg/`, poi `render` ha prodotto lo staging, e **i PNG spediti
sono l'output di `render`**, non i file di lavoro del concept.

| sprite | sorgente promossa |
|---|---|
| `sun_body` | `concepts/b/svg/sun_body.svg` |
| `sun_glow` | `concepts/b/alone/v2_rifinita/c/sun_glow.svg` |
| `moon_full`, `moon_gibbous`, `moon_half`, `moon_crescent` | `concepts/b/svg/` |
| `moon_jack_o_lantern` | `concepts/b/zucca/minima/` |
| `star_sparkle` | `concepts/b/svg/` (la versione della Parte A) |

**MISURATO: lo staging è byte-identico ai PNG del concept, 8 su 8.** Cioè quello che si spedisce è
esattamente l'artwork che il maintainer ha guardato nelle fotografie, non una ri-rasterizzazione che
gli somiglia.

### 1.1 Le tele sono identiche — verificato, non assunto

MISURATO leggendo entrambi i PNG:

| sprite | tela prima | tela dopo | contentBox prima | contentBox dopo |
|---|---|---|---|---|
| `sun_body` | 240×240 | **240×240** | 18,18..222,222 | 21,21..224,227 |
| `sun_glow` | 396×396 | **396×396** | 1,1..395,395 | 1,1..395,395 |
| `moon_full` | 240×240 | **240×240** | 18,18..222,222 | 19,19..221,220 |
| `moon_gibbous` | 240×240 | **240×240** | 66,18..222,222 | 65,21..220,219 |
| `moon_half` | 240×240 | **240×240** | 120,18..222,222 | 118,21..218,219 |
| `moon_crescent` | 240×240 | **240×240** | 122,18..222,222 | 120,21..220,219 |
| `moon_jack_o_lantern` | 240×240 | **240×240** | 18,18..222,222 | 20,21..218,218 |
| `star_sparkle` | 180×180 | **180×180** | 3,3..177,177 | 14,8..168,164 |

Ne segue che **nessun sito di chiamata cambia**: `CELESTIAL_DISC_ORIGIN_UNITS`,
`SUN_GLOW_ORIGIN_UNITS`, `STAR_SPRITE_ORIGIN_UNITS`, le due convenzioni di scala e le ancore
`SPRITE_CENTRE` sono i numeri che erano. E ne segue il **primo tetto**, §7.

### 1.2 Il registro

`tools/assets/sources/sprites.json` aggiornato per le otto voci: `contentBox` rimisurato, `notes`
riscritte. Dimensione, ancoraggio, convenzione di scala e classe di tinta **non cambiano**, perché
non è cambiato niente che li riguardi. `validate` passa: **registro OK, 266 voci, 134 con sorgente
SVG, 132 registrate come lacune**; nessuna coppia byte-identica non dichiarata.

Due `notes` erano diventate false e sono state riscritte, non ritoccate: quelle di `moon_crescent` e
`moon_gibbous` descrivevano la correzione v76.1 di **archi SVG** (`A52 34 0 0 0` → `A20 34 0 0 0`;
`A20 34 0 1 0` → `A18 34 0 0 1`) che nel sorgente nuovo **non esistono più** — i terminatori del
concept B sono tagliati, non spazzati. La storia resta scritta, ma dichiarata come storia.

I test JVM che leggono i PNG restano verdi (§9).

---

## 2. B2 — la configurazione 1, e i tre difetti

`PaperRenderer` blitta la scintilla a `star.radius / STAR_SPRITE_RADIUS_DIVISOR`, e il divisore
passa da **32 a 16**. Il valore non è più un letterale al sito di chiamata: è una costante nominata,
perché **tre altri punti del codice ne dipendono** e devono poterla leggere.

### 2.1 (5a) Le estensioni dello sprite — la derivazione

La derivazione, scritta:

```
reach = STAR_SPRITE_HALF_UNITS / STAR_SPRITE_RADIUS_DIVISOR × MAX_STAR_RADIUS_PX
      = 30 / 16 × 5,6 = 10,5 px di tela
```

- `STAR_SPRITE_HALF_UNITS = 30` è la **semi-estensione del bitmap** in unità locali:
  `180 px / SPRITE_PIXELS_PER_UNIT = 60` unità, mezze. Non è un numero scelto: è lo stesso 30 che
  `STAR_SPRITE_ORIGIN_UNITS` portava come `-30`, e `SkySpriteAnchoringTest` lo verifica già contro
  l'header del PNG. Perciò `STAR_SPRITE_ORIGIN_UNITS` è ora **derivato** come la sua negazione:
  origine ed estensioni non possono più litigare su quanto è grande il bitmap.
- `MAX_STAR_RADIUS_PX` **non si tocca**: `regenerateStars` legge quella costante, e il raggio delle
  stelle non è cambiato.

**Perché la riparazione ovvia era sbagliata, e perché è pericolosa.** Raddoppiare
`MAX_STAR_RADIUS_PX` fa sparire lo stesso sintomo — le tile tornano abbastanza larghe, ed è quello
che aveva fatto la build usa-e-getta della Parte A — ma cambia il campo stellare per riparare un bug
di tassellatura. È il difetto più costoso di questo progetto nella sua forma più insidiosa: la
metrica torna verde e l'artwork si deforma.

**Prima**, con divisore 32, lo sprite arrivava a `0,9375 × radius` e riservare l'intero raggio era
una **sovra-riserva deliberata**. **Dopo**, a `1,875 × radius`, riservare il raggio sarebbe una
**sotto-riserva di due volte**, e una sotto-riserva fa cadere una copia di tile alla giuntura: una
scintilla tagliata dove il campo si ripete.

**Riservato sul bitmap, non sull'artwork che contiene.** La scintilla ridisegnata ha di nuovo un
margine trasparente (contentBox `14,8..168,164`: il disegno arriva a `82/3` unità, **9,57 px**), e le
due letture, che coincidevano finché lo sprite riempiva la tela, tornano a differire. Sovra-riservare
quei 0,93 px costa un confronto; sotto-riservare costa un taglio visibile. Il commento che diceva
«the sprite has no transparent margin left» era anch'esso diventato falso ed è corretto.

**MISURATO sul dispositivo, condizione T non scattata:** con l'artwork nuovo e la configurazione 1
installati, nessuna scintilla è tagliata ai bordi. Le 24 scene golden catturate su questo device non
mostrano un solo troncamento alla giuntura, e le catture del wallpaper vivo (§8) nemmeno.

### 2.2 (5b) I commenti che sarebbero diventati falsi

Sei punti, tutti corretti, e due dei sei con una **misura** dietro invece di un'aritmetica:

| dove | diceva | ora |
|---|---|---|
| `STAR_SPRITE_{LEFT,RIGHT}_EXTENT_PX` docblock | «reaches `0.9375 * radius`»; «no transparent margin left» | la derivazione sopra, e il margine che è tornato |
| `STAR_SPRITE_ORIGIN_UNITS` docblock | «reaches 0.9375 of the star's own radius» | 1,875, e perché una stella è una posizione, non un contorno |
| `drawStars` commento in linea | «reaches 0.9375 of the star's radius» | 1,875, **con scritto che 0,9375 è ora sbagliato ovunque sopravviva** |
| `STAR_POINT_RADIUS_SCALE` docblock | «reaches 0.94 of the radius» | 1,875, e perché 0,55 **non** è stato riscalato con lo sprite |
| `SUN_GLOW_ORIGIN_UNITS` docblock | «ray ring at 150..198 units» | **MISURATO: 154..166**, con l'alone morbido fino a 197 |
| `drawCelestialBody` commento in linea | «rays sit in a ring 150..198px»; «would put the ring at 50..66 units» | **154..166**, e la lettura sbagliata ricalcolata a **51..55** |

La misura dell'anello, per esteso (MISURATO sul profilo radiale dei due PNG, pixel con alpha ≥ 150):
l'alone spedito aveva raggi in una banda **111..190** che scavalcava il bordo del disco; il `c` del
concept ha un anello pieno a **154..166** su un alone che arriva a 197. **Il numero portante era
solo quello esterno** — l'anello deve cominciare oltre i 120 unità del disco — e lo è ancora, con 34
unità di margine invece di 30.

### 2.3 (5c) `STAR_POINT_COLOR` — deciso, con il perché

Quattro stelle su cinque sono cerchi pieni disegnati dal codice, e il commento della costante dice
che è «the cream the sparkle art is drawn in, so a point and a sparkle are the same star». Valeva
`#FFF6DC`; `star_sparkle.png` è, **e non è mai stato altro che**, `#FBF4E6`. Scarto 4/2/10 livelli.

**Decisione: si corregge il colore, non la frase.** Le due riparazioni erano entrambe disponibili, e
la frase dice la proprietà che si vuole davvero: che un punto e una scintilla siano la stessa
stella. Correggere la frase avrebbe conservato una costante scollegata dall'artwork, cioè lo stesso
difetto scritto meglio.

E soprattutto: **è stata pinnata**. `StarFieldColourTest` (nuovo) legge da `star_sparkle.png` l'unico
colore completamente opaco — MISURATO: **uno solo, 4 689 px, `#FBF4E6`** — e asserisce che la
costante gli è uguale, fallendo anche se l'artwork dovesse arrivare a contenere due creme. È l'unico
modo di catturare questa classe di difetto: **una falsità invisibile su un punto di due pixel non si
vede guardando il dispositivo**, e per quattro release non si è vista.

Il valore `STAR_POINT_RADIUS_SCALE = 0,55` **non** è stato toccato, e il commento ora dice perché:
`drawStars` disegna un campo fatto in maggioranza di punti fiochi con poche stelle brillanti, e far
crescere i punti insieme alle scintille restituirebbe il campo uniforme che quella separazione
esisteva per evitare. Cambiarlo sarebbe una decisione visiva che nessuno ha preso.

### 2.4 «E cerca il resto» — l'elenco cercato

Cercato con `grep` su `0.9375`, `radius / 32`, `32f`, `FFF6DC`, `150..198`, `star_sparkle`,
`EXTENT_PX`, e leggendo ogni sito di chiamata degli otto sprite. **Trovato e corretto:**

1. `STAR_SPRITE_{LEFT,RIGHT}_EXTENT_PX` — la portata delle tile (§2.1).
2. `STAR_SPRITE_ORIGIN_UNITS` — era un `-30` indipendente, ora derivato.
3. `SkySpriteAnchoringTest`, fixture di `star_sparkle`: `nominalRadiusUnits` era il letterale **32**.
4. `SkySpriteAnchoringTest`, `the star extents cover what the sprite actually reaches`: `/32f`
   scritto nella formula della reach. Con un letterale lì, il test sarebbe rimasto **verde mentre la
   tassellatura sotto-riservava di due volte**.
5. `SkySpriteAnchoringTest`, `a star sparkle fills the star's radius without exceeding it`: **la
   proprietà stessa è diventata falsa**, non il numero. La configurazione 1 fa deliberatamente
   *superare* il raggio. Riscritta come rapporto — `1 < reach/radius < 3` — perché il lavoro vero di
   quel test è discriminare le due convenzioni di scala, che distano un fattore 3: la lettura giusta
   dà 1,875, quella sbagliata 5,625, e uno sprite di un terzo 0,625. Rinominata di conseguenza.
6. `BackgroundScrollGeometryTest`, `LEFT_EXTENT`/`RIGHT_EXTENT`: **estensioni duplicate nel test**,
   di proposito. È la trappola che `DESIGN_NOTES.md` già documenta per l'albero — *un duplicato
   protegge da un refuso, mai da una premessa vecchia* — e si è comportata esattamente così.
7. `sources/sprites.json`, nota di `star_sparkle`: «against a star's own 32».
8. `ARCHITECTURE.md`, la sezione sulla tassellatura del campo stellare: la portata, e il «~1 % del
   ciclo» in cui si disegnano tre copie, che con estensioni doppie diventa **~2 %**.
9. `ARCHITECTURE.md`, la nota sul manifesto V2: «against a star's own 32».
10. `reports/runtime-inventory.{json,md}` — **descrivevano un set da 260 sprite mentre ne spediamo
    266.** Prova committata che descriveva un insieme che non esiste da due release; rigenerata.
    Voce 43 del backlog, ed è una delle voci che la 30 raccoglie.

**Cercato e verificato *non* toccato, con la ragione:**

- `regenerateStars` — banda di spawn (`y < 0,55 × altezza`), conteggio (70 × densità) e intervallo di
  raggio (`2,4 + rnd × 3,2`) invariati. La configurazione 1 non tocca dove sono le stelle né quante.
- `STAR_SPARKLE_EVERY` — una stella su cinque resta una scintilla.
- `firstStarTileOffset` / `starTileOffsetLimit` — la formula è giusta, cambiano solo i suoi ingressi.
- Le origini di blit del preview (`sun_glow -66`, `sun_body -40`, luna `-40`, tre palline a
  `(-26,-110) (2,-84) (-22,-58)`) — dipendono dalla **tela**, che non è cambiata.
- `SpriteGeometryTest` (griglia e tetto di memoria), `GlTextureAtlas` — nessuna dimensione è cambiata.
- `HALLOWEEN_MOON_COLOUR`, le classi di tinta, `MULTIPLY` — la zucca nuova ha media 244,1, sopra il
  minimo 220 che `SpriteTintClassTest` richiede.

**Trovato, non toccato, registrato nel backlog** (perché toccarlo sarebbe disegnare, e questo pass
non disegna): le tre palline dell'abete del preview sono blit di `star_sparkle` e quindi **hanno
cambiato forma** (voce 44); il disegno di `sun_body` non è centrato nella propria tela, ~2,5 unità
(voce 45); **nessun golden esercita `moon_jack_o_lantern`** (voce 41).

### 2.5 Le tre mutazioni, viste rosse prima di essere credute

Il progetto dice che una suite verde al primo colpo è un allarme. MISURATO:

| mutazione | chi l'ha presa |
|---|---|
| estensioni rimesse a `MAX_STAR_RADIUS_PX` | `SkySpriteAnchoringTest.the star extents cover what the sprite actually reaches` **e** `BackgroundScrollGeometryTest.star sprite extents match the values the tile range is derived from` |
| divisore rimesso a `32f` | `SkySpriteAnchoringTest.a star sparkle reaches past the star's radius…` **e** `BackgroundScrollGeometryTest…` |
| `STAR_POINT_COLOR` rimesso a `#FFF6DC` | `StarFieldColourTest.the point stars are drawn in the sparkle artwork's own cream` |

Dopo ogni mutazione `PaperRenderer.kt` è stato ripristinato dalla copia e riverificato.

---

## 3. B3 — i golden: l'attribuzione, fatta prima

### 3.1 I verdetti, prima di toccare qualsiasi cosa

MISURATO: esecuzione strumentata delle sette classi di golden sul BV6600, con l'artwork nuovo e la
configurazione 1 installati e i golden **ancora quelli committati**. `am instrument`, non Gradle con
un filtro (che disinstalla il pacchetto e si porta via i file).

**45 test, 2 falliti:**

- `dusk` — 1 961 px oltre la tolleranza, **0,6809 %** contro il limite di 0,2000 %
- `people-skin` — 1 236 px, **0,4292 %**

Tutto il resto verde, **compresi i tre GL** e `GlDriverGapGuardTest`, che ha loggato
`day 0,00 %, lake-busy 0,01 %, thunderstorm 0,24 %` contro il cancello del 3 % — **le stesse tre
cifre di v4.22**.

Le due immagini di differenza che il harness ha scritto da sé (`dusk-diff.png`,
`people-skin-diff.png`, in `attribuzione/`) mostrano **soltanto l'anello del sole**, in un caso basso
sull'orizzonte a destra, nell'altro alto e mezzo coperto dalle nuvole. Niente altro nel fotogramma.

### 3.2 L'attribuzione per regione, scena per scena

Poi i 24 frame sono stati **catturati** con `-e updateGoldens true` (che scrive senza mai
confrontare) e confrontati **fuori dal dispositivo** contro i golden committati, con la metrica del
progetto riprodotta: due pixel differiscono se un canale ARGB differisce di più di 8.

Il quadro completo è in `attribuzione/ATTRIBUZIONE.txt` e le 24 immagini di differenza sono in
`attribuzione/*-diff.png` (più il foglio `foglio_attribuzione.png`). Riassunto MISURATO:

| scena | oltre tolleranza | % del frame | componente max | SKY (0,0)-(360,440) | FACCIATE (0,376)-(360,636) | PAVEMENT (0,546)-(360,655) | STRADA (0,626)-(360,703) |
|---|---|---|---|---|---|---|---|
| `dusk` | 1 961 | **0,6809 %** | 1 331 | 1 004 | 1 961 | **0** | **0** |
| `people-skin` | 1 236 | **0,4292 %** | 818 | 1 236 | 0 | **0** | **0** |
| `lake-boats` | 437 | 0,1517 % | 427 | 437 | 0 | **0** | **0** |
| `traffic-night`, `traffic-night-quiet` | 336 | 0,1167 % | 40 | 336 | 78 | **0** | **0** |
| `theme-city` | 303 | 0,1052 % | 24 | 303 | 78 | **0** | **0** |
| `people-skyscraper` | 269 | 0,0934 % | 269 | 269 | 0 | **0** | **0** |
| `shops-closed-night` | 267 | 0,0927 % | 21 | 267 | 78 | **0** | **0** |
| `night` | 250 | 0,0868 % | 21 | 250 | 78 | **0** | **0** |
| `traffic-day`, `traffic-day-sparse` | 108 | 0,0375 % | 107 | 108 | 0 | **0** | **0** |
| `people-mixed` | 83 | 0,0288 % | 83 | 83 | 0 | **0** | **0** |
| `people-overlap` | 25 | 0,0087 % | 14 | 25 | 0 | **0** | **0** |
| `people-commercial` | 10 | 0,0035 % | 10 | 10 | 0 | **0** | **0** |
| `lake-dolphin-leap` | 4 | 0,0014 % | 2 | 4 | 0 | **0** | **0** |
| `people-window` | **0** | 0,0000 % | 0 | 0 | 0 | **0** | **0** |
| `day`, `lake-busy`, `lake-empty`, `overcast`, `people-single`, `rain`, `snow`, `thunderstorm` | **0** | 0,0000 % | 0 | 0 | 0 | **0** | **0** |

**Attribuzione, e regge in due modi indipendenti.** Per riga: ogni pixel differente sta a
**y ≤ 495**, cioè nel cielo o nell'arco del sole basso; sotto quella riga non ne differisce nemmeno
uno. Per forma: le immagini di differenza mostrano **puntini** dove ci sono scintille e **archi**
dove c'è il sole, e nient'altro — nessun oggetto manca, si sposta o cambia colore.

**Le 78 px nella banda delle facciate delle scene notturne sono scintille, non edifici**, e la
ragione è geometrica e va detta: la banda delle facciate comincia a **y 376** e il campo stellare
arriva a **y 440**, quindi i due rettangoli si sovrappongono per 64 righe. Le 78 px stanno a
y 376..432 (MISURATO), cioè tutte dentro quella sovrapposizione. Su `shops-closed-night`, l'unica
scena dove quella banda è un cancello, valgono **0,0833 %** contro un cancello di **1,0038 %**:
l'asserzione passava anche prima della rigenerazione, e dopo torna a zero.

**Le 1 961 px di `dusk` stanno a y 397..495, x 244..359**: il sole al tramonto, a destra, basso.
`dusk` non ha un focus sulle facciate; il suo rosso era il limite di frame intero.

### 3.3 Condizione P — i quattro cancelli sono fermi

**Nessun rettangolo di cancello si è mosso.** Sono costanti derivate nel codice
(`SettingsGateScenesTest.roadBand` 626..703 dalla geometria delle corsie, `facadesBand` 376..636
dalla linea di gronda, `PeopleGoldenTest.PAVEMENT` 546..655 dalle due linee di terra) e **non ho
toccato né quei file né `SettingsGates`** — verificato nel diff dei due archivi, §10.

**MISURATO, PAVEMENT e STRADA misurano 0 px differenti su tutte e 24 le scene**, prima ancora della
rigenerazione. E i quattro segnali, rimisurati a ogni esecuzione dai test di derivazione:

```
GATEDERIVE: car count ignored (35% drawn as 100%): floor=0.0000% signal=7.6263% gate=3.8041%
GATEDERIVE: night car density ignored:             floor=0.0000% signal=14.0079% gate=7.0057%
GATEDERIVE: business hours ignored:                floor=0.0000% signal=2.0053% gate=1.0038%
GATEDERIVE: people density: hidden=0.2829% ignored=0.7110% gate=0.1415%
```

Identici a v4.22 alla quarta cifra. **Nessuna tolleranza toccata, nessun cancello spostato.**

### 3.4 La rigenerazione: 16 sì, 8 no

**MISURATO: 16 dei 24 golden Canvas non sono byte-identici, 8 lo sono.** Gli otto invariati non sono
stati toccati:

`day`, `lake-busy`, `lake-empty`, `overcast`, `people-single`, `rain`, `snow`, `thunderstorm`.

**Perché così pochi, e non è una scusa ma una misura.** In quelle otto scene il sole **sta dietro la
banda di nuvole** e non se ne disegna un pixel visibile — si vede aprendo `day.png` — e non c'è
notte, quindi nessuna scintilla. Il mandato si aspettava che si muovessero *molti* golden; il
dispositivo dice 16 su 24, e le altre 8 non si sono mosse perché **non esercitano il cielo**. È la
voce 40 del backlog: «i golden sono verdi» non va letto come «il cielo è coperto».

Ho rigenerato **tutti e 16** quelli mossi, anche i quattordici che passavano già, per la ragione di
v4.22: su questo dispositivo il pavimento di rumore è **zero**, e un golden lasciato vecchio
lascerebbe uno scarto costante dentro i rettangoli dei cancelli che si somma al loro budget. Dopo la
rigenerazione ogni frame e ogni focus asserito tornano a **zero pixel differenti**.

Gli SHA-256 prima/dopo dei 16, con i byte, sono in `attribuzione/SHA_PRIMA_DOPO.txt` e qui:

| PNG | SHA-256 prima → dopo (primi 16) | byte prima → dopo |
|---|---|---|
| `dusk.png` | `18d3de80c87ac8789…` → `74dd6453524ec8925…` | 52 443 → 52 397 |
| `lake-boats.png` | `85d66cc6bdc973c31…` → `3a262c931a50c47aa…` | 49 318 → 48 959 |
| `lake-dolphin-leap.png` | `15cc955323b7e95ee…` → `8319bd8136c6c12b9…` | 44 052 → 44 069 |
| `night.png` | `c06009c520a21d277…` → `3784c4d82fb768701…` | 38 400 → 38 781 |
| `people-commercial.png` | `40f7cc58fd5ef7bdc…` → `a0d443bd6a45d6014…` | 45 561 → 45 441 |
| `people-mixed.png` | `b2097e84f86d5c52a…` → `1d989f18aa96b9adf…` | 41 722 → 41 623 |
| `people-overlap.png` | `d094ba3500b708eb9…` → `3342f3550a32a79cd…` | 41 251 → 41 283 |
| `people-skin.png` | `948bfd08b708782ba…` → `e4ed60b9bccc31fa6…` | 46 496 → 46 101 |
| `people-skyscraper.png` | `b4003b419bab6d354…` → `8ad9b35c2e0dfaea3…` | 37 494 → 37 328 |
| `people-window.png` | `44aeaf705f9478735…` → `b689ae93b8b457464…` | 42 623 → 42 641 |
| `shops-closed-night.png` | `eb13fca4bc13340c4…` → `b86a59960f84e4d3b…` | 42 572 → 43 011 |
| `theme-city.png` | `e905c1da438634274…` → `1c10f10d9e502e305…` | 37 667 → 38 182 |
| `traffic-day.png` | `ad5f2030835a09995…` → `afef444cd4eb5f550…` | 46 110 → 46 229 |
| `traffic-day-sparse.png` | `05a58d15fe94b6242…` → `c3930f992dfb190ce…` | 43 174 → 43 306 |
| `traffic-night.png` | `774f482468084798f…` → `d8c9f0678a34b71fb…` | 45 567 → 45 928 |
| `traffic-night-quiet.png` | `2c997a9a9e550a76b…` → `7d2017d3b8c2ce805…` | 39 546 → 39 896 |

### 3.5 I tre golden GL — **non** rigenerati, e questo contraddice l'attesa del mandato

Il mandato si aspettava che si muovessero tutti e tre, perché asseriscono `SUN_GLOW`. **La misura
dice di no, e la dico invece di consegnarla in silenzio.**

L'argomento è diretto e non richiede di fidarsi della metrica GL: **i frame *Canvas* delle tre scene
GL — `day`, `lake-busy`, `thunderstorm` — sono byte-identici** (§3.2). In quelle tre scene non è
cambiato **niente**, perché in tutte e tre il sole sta dietro le nuvole e nessuna è notturna. Se la
scena non cambia, il golden GL di quella scena non ha ragione di cambiare.

E infatti: `GlSceneGoldenTest` verde su tutte e tre, e `GlDriverGapGuardTest` legge **day 0,00 % /
lake-busy 0,01 % / thunderstorm 0,24 %** contro il cancello del 3 % — le stesse tre cifre di v4.22.

**Una trappola da scrivere, perché sembra una contraddizione.** Catturando i tre frame GL e
confrontandoli **byte a byte** con i committati, differiscono: 0,49 % / 0,57 % / 0,94 % dei pixel
oltre la tolleranza 8, a y 278..638 e 203..799 — cioè **negli edifici e nel terreno, non nel cielo**.
Quello **non** è questo pass: i tre `gl-*.png` sono ancora i file autorati sull'**Adreno 630**, e
quello è lo scarto di driver già caratterizzato. Il confronto byte a byte è la metrica sbagliata per
i golden GL; quella giusta è lo spostamento dei bordi, che è ciò che il cancello misura, e che è
fermo. Se li avessi rigenerati avrei silenziosamente sostituito il riferimento Adreno con uno
PowerVR **senza che nessuna scena fosse cambiata**, cioè avrei speso il debito della voce 20 per
niente.

**Sarebbe stato legittimo rigenerarli su PowerVR** — è l'unico dispositivo che esiste, e la voce 20 è
stata chiusa da una misura in questo ambiente. Semplicemente non c'era niente da rigenerare.

### 3.6 Condizione Q — `GoldenUniquenessTest`

Verificato **prima** di installare i 16 file: sui 27 PNG che sarebbero risultati, **zero gruppi
byte-identici**. E poi eseguito per davvero: `GoldenUniquenessTest` è **verde** nella suite JVM
finale. Nessuna scena ha smesso di esercitare ciò che esercitava.

---

## 4. B4 — i due tetti

### 4.1 Il tetto degli sprite: **0 byte consumati**

Le tele sono identiche (§1.1), quindi il footprint decodificato non cambia. **Verificato, non
assunto** (MISURATO da `runtime-inventory`, rigenerato):

| | valore |
|---|---|
| sprite spediti | **266** |
| decodificati (ARGB_8888) | **30 254 580 B** |
| tetto di `SpriteGeometryTest` | 29 MiB = **30 408 704 B** |
| **margine** | **154 124 B** — identico a prima, a byte |

`SpriteGeometryTest` è verde. Il valore committato in `runtime-inventory.json` diceva **260** sprite
per 29 629 044 B: era **evidenza vecchia** (voce 43), rigenerata qui; il margine reale è, ed era,
154 124 B.

### 4.2 Il tetto del disegno: rimisurato, non stimato

**Protocollo della voce 32/34 riusato invariato**: delta di `utime+stime` da `/proc/<pid>/stat`
(somma su tutti i thread), `CLK_TCK` = 100 **verificato sul device**, orologio da `/proc/uptime`,
`comm` tagliato prima dell'`awk`. Build **simil-release** — `initWith(release)`, R8 acceso,
`isShrinkResources`, `debuggable = false`, firmata col `debug.keystore` committato,
`applicationIdSuffix = ".debug"`. **Il build type è locale, non è nell'archivio** (§10).

Condizioni fissate: tema **Autumn** (impostato dalla galleria e verificato: «Autumn - selected»),
wallpaper **visibile** sul launcher Blackview, alimentato, luminosità **manuale a 33**, pacchetto di
test **disinstallato**, riscaldamento **180 s dichiarato**, finestre da **60 s**, n = 3.

**L'APK installato è stato verificato byte per byte contro il `pull`** (`05398124f81f117db…`), e gli
otto sprite dentro l'APK sono stati confrontati **pixel a pixel** con i PNG spediti: 8 su 8 a
**delta massimo di canale 0**. (Il confronto va fatto sui pixel e non sui byte: la build di release
ri-codifica i PNG e rinomina le risorse.)

| | v4.23 (MISURATO oggi) | v4.22 (voce 34) |
|---|---|---|
| processo, visibile | **43,72 %** di un core (sd 0,24, n=3) | 43,56 % (sd 0,50, n=3) |
| `PaperScrapeGlTh` | **42,11 %** | 42,71 % |
| `ged-swd` / `HeapTaskDaemon` | 0,57 % / 0,32 % | 0,59 % / 0,25 % |
| `Jit thread pool` | **assente** | assente |
| processo, **nascosto** (schermo spento) | **0,138 %** (sd 0,039, n=3) | 0,137 % (sd 0,034, n=3) |
| frame rate (SurfaceFlinger, layer del wallpaper) | **29,63 fps** (sd 0,07; 378 intervalli) | 29,60 fps (sd 0,07; 198 campioni) |
| **ms di CPU per frame** | **14,76 ms** | 14,72 ms |
| quota dell'intervallo | **43,7 %** di 33,75 ms | 44,2 % di 33,33 ms |

**Condizione R non scattata.** La differenza sul costo per frame è **+0,04 ms**, cioè +0,3 %, mentre
la sola deviazione standard della CPU vale ±0,08 ms: il riempimento nuovo nel cielo — alone caldo a
piena opacità, scintille al doppio della scala — **non costa niente di misurabile**. La cadenza è
tenuta (29,63 di 30) e il margine è più della metà dell'intervallo, come a v4.22.

Le tre finestre, per chi rifà il conto: 43,51 % / 43,66 % / 43,98 %. Le tre nascoste: 0,183 % /
0,116 % / 0,116 %. La somma per thread riconcilia col totale di processo (43,00 % contro 43,72 % su
finestre diverse di 63 e 60 s).

**GPU busy: non misurabile qui e non stimata** — invariato rispetto alla voce 34.

---

## 5. B5 — documenti e backlog

**Aggiornati:** `RELEASE_HISTORY.md` (voce v4.23 nuova, in testa), `ROADMAP.md` (stato corrente
riscritto su v4.23, blocco della release), `release-notes/v4.23.md` (**nuovo**), `DESIGN_NOTES.md`
(la regola del bordo tagliato in §2, e un paragrafo lungo in «A redraw invalidates every number
measured off the old drawing» che è la lezione di questo pass accanto a quella dell'albero),
`ARCHITECTURE.md` (la tassellatura del campo stellare e la nota sul manifesto), `CLAUDE.md` (la nota
sui golden, la procedura di cattura su questo device, e la trappola dell'OOM di R8),
**`BACKLOG_v4_23.md` (nuovo)**.

**`CHANGELOG.md` non è stato toccato, di proposito, e lo segnalo perché il mandato lo elencava.** La
sua intestazione dice che è il log tecnico dello sviluppo *pre-release*, che la sequenza è finita a
v76.12, e che «nothing here describes a current version»; nessuna delle v4.20, v4.21, v4.22 ci ha
scritto. Aggiungerci la 4.23 romperebbe l'ambito che il file dichiara di sé. Se il maintainer vuole
comunque una voce lì, è una riga da aggiungere e lo dico invece di deciderlo.

**Backlog.** `BACKLOG_v4_23.md` sostituisce `BACKLOG_v4_22.md` e continua la numerazione.

- **Portate avanti:** 18, 25, 30.
- **La palma (voce 25) — segnalata, non fatta.** La voce resta OPEN e invariata nel merito, con una
  nota: col cielo tagliato, **la palma è il pezzo più vistoso del linguaggio vecchio ancora
  spedito**, perché le due famiglie che uno guarda più a lungo sono ora disegnate con regole
  diverse. È un'osservazione, non un'autorizzazione: nessun artwork della palma è stato toccato,
  nessun concept disegnato, e **quale famiglia rivedere dopo lo decide il maintainer**.
- **Aperte nuove:** 37 (la famiglia celeste promossa), 38 (le estensioni), 39 (`STAR_POINT_COLOR`),
  40 (i golden coprono poco il cielo), 41 (nessun frame disegna la zucca), **42 (la grana di
  carta)**, 43 (l'inventario vecchio), 44 (le palline dell'abete), 45 (`sun_body` non centrato),
  46 (`getExternalFilesDir` funziona qui).
- **La grana di carta è registrata, non risolta** (voce 42), con le tre ragioni per cui è una
  decisione del maintainer e va misurata prima di essere presa: tocca tutte e 266 gli sprite e non
  otto; ha un costo che è o entropia in ogni PNG contro un tetto già alzato quattro volte, o una
  passata per frame su un budget di 14,76 ms di 33,75; e interagisce con `MULTIPLY`, dove
  `SpriteTintClassTest` chiede media ≥ 220.

**Conteggi a mano nei documenti di stato corrente.** La regola in vigore — *il conteggio Canvas è il
numero di asserzioni, non di file nella cartella* — è stata riverificata e i documenti la portano
ancora giusta: **25 asserzioni Canvas, 3 GL, 27 PNG committati**, invariati (`day.png` è asserito due
volte, con focus diversi). L'unico conteggio trovato sbagliato è quello di `runtime-inventory`
(260 contro 266): chiuso con una rigenerazione, voce 43.

---

## 6. Verifica

| | risultato |
|---|---|
| **JVM** | **1332 test, 0 falliti, 0 errori, 0 saltati** sull'albero finale pulito |
| **strumentati** | **148 test, 0 falliti** — `OK (148 tests)`, suite intera, dopo la rigenerazione |
| **Python (asset)** | **108, OK**, venv `paperscrape-assets`; probe conforme |
| **gate `PIXEL_IDENTICAL`** | **134 / 134** |
| **`validate`** | registro OK, 266 voci |
| **`lintDebug`** | eseguito insieme alla suite JVM finale |
| **`assembleDebug`** | eseguito (serviva per la suite strumentata e le catture) |
| **`GoldenUniquenessTest`** | **verde** |
| **mutazioni** | tre applicate, tre viste rosse, tre ripristinate (§2.5) |

`assembleRelease` **non** è stato tentato: la firma di release non esiste qui e non si genera.

---

## 7. Le condizioni d'arresto

| | esito |
|---|---|
| **A** — gate `PIXEL_IDENTICAL` fallisce a livello di pixel | **non scattata** — 134/134 |
| **D** — la baseline non combacia | **non scattata** — §0 |
| **P** — un rettangolo di cancello si muove | **non scattata** — i rettangoli sono costanti derivate che non ho toccato; PAVEMENT e STRADA misurano 0 px su tutte e 24 le scene. Riporto comunque, perché è il caso limite che la condizione ha in mente: le facciate (376..636) **si sovrappongono per 64 righe** al campo stellare (y < 440), e lì 78 px di scintille sono cambiati sulle scene notturne — 0,0833 % contro un cancello di 1,0038 %, e zero dopo la rigenerazione |
| **Q** — due golden rigenerati escono byte-identici | **non scattata** — zero gruppi duplicati sui 27 |
| **R** — margine sprite o ms/frame peggiorano in modo misurabile | **non scattata** — margine identico a byte (154 124 B); ms/frame +0,04 su 14,72, dentro una deviazione standard |
| **T** — una scintilla tagliata ai bordi anche dopo la correzione | **non scattata** — OSSERVATO sul dispositivo |

---

## 8. Stato del dispositivo

**OSSERVATO alla riconsegna (2026-09-06, ~17:15 CEST).**

- **Wallpaper attivo: la release del maintainer** — `com.paperscrape.livewallpaper`, `versionName
  4.22`, `versionCode 53`, firma non toccata, **mai disinstallata**. Rimessa dal picker
  («Set wallpaper») e **osservata mentre rende**: `mEngine` legato, due `screencap` a 4 s di distanza
  **diversi** (SHA differenti, 147 519 e 143 714 byte), e nella cattura si vede il **vecchio**
  sunburst a raggi — cioè è davvero la sua build, non la mia.
- **Build sperimentali disinstallate:** `pm list packages` mostra **solo** il pacchetto release; il
  pacchetto `.debug` (debug e poi simil-release) e `.debug.test` sono stati disinstallati, e la loro
  cartella su `/sdcard/Android/data/` è sparita con loro. I file temporanei che ho scritto su
  `/sdcard` sono stati cancellati.
- **Il wallpaper v4.23 è stato osservato mentre rende**, prima del ripristino, sul tema Autumn:
  `anteprime/live_autumn_v4_23_a.png` — il disco sfaccettato con l'anello, a scena piena.
- **Stato originale annotato e ripristinato:**

  | impostazione | all'inizio | durante | alla fine |
  |---|---|---|---|
  | `system.screen_brightness_mode` | 1 (auto) | 0 (manuale, per la misura) | **1** |
  | `system.screen_off_timeout` | 60 000 | invariato | **60 000** |
  | `system.screen_brightness` | 102 | 33 | 12 — **non l'ho scritto io**: con la modalità automatica ripristinata è il sensore a muoverlo (DICHIARATO) |
  | `global.stay_on_while_plugged_in` | 0 | 3 | **0** |
  | `global.auto_time` | 1 | **mai toccato** | 1 |

- **Le preferenze della release del maintainer non sono state modificate.** Per rimettere il suo
  wallpaper ho aperto la sua app e premuto il suo «Set as wallpaper»: la schermata si è aperta su una
  sotto-pagina («Weather & time») da cui sono uscito con il tasto indietro senza toccare nulla. La
  sua configurazione è quella che era: tema automatico per data attivo, oggi Autumn.
- La data/ora non è mai stata cambiata (non serviva una fase lunare).

---

## 9. Contabilità, con l'aritmetica scritta

I conteggi d'archivio sono **derivati dal diff dei due archivi**, fatto **prima** di scrivere questi
numeri.

| | prima | dopo | aritmetica |
|---|---|---|---|
| test JVM | 1331 | **1332** | 1331 + 1 − 0 = 1332. Il +1 è `StarFieldColourTest`, una classe con un test. `SkySpriteAnchoringTest` ne ha uno **rinominato**, non aggiunto |
| test strumentati | 148 | **148** | 148 + 0 − 0. Nessuna classe strumentata toccata |
| test Python | 108 | **108** | 108 + 0 |
| PNG golden committati | 27 | **27** | 24 Canvas (16 rigenerati + 8 intatti) + 3 GL intatti = 27; **25 asserzioni Canvas** su 24 PNG |
| `@Ignore` | 0 | **0** | |
| sprite spediti | 266 | **266** | 8 sostituiti, 0 aggiunti, 0 rimossi |
| voci dell'archivio | 963 | **967** | 963 + 4 − 0 = 967 |

Le **4 voci nuove**: `V4_23_FASE2B_REPORT.md`, `BACKLOG_v4_23.md`, `release-notes/v4.23.md`,
`app/src/test/kotlin/com/paperscrape/livewallpaper/engine/StarFieldColourTest.kt`. Nessuna rimossa.
Le altre **46** differenze sono file modificati allo stesso percorso (§10).

---

## 10. File toccati — dal diff dei due archivi

**46 modificati, 4 aggiunti, 0 rimossi**, più questo report. Derivato dal confronto albero-contro-ZIP
di partenza, **non** da `git status` (`CLAUDE.md` non è versionato e git non lo mostra mai).

*Aggiunti:* `V4_23_FASE2B_REPORT.md`, `BACKLOG_v4_23.md`, `release-notes/v4.23.md`,
`app/src/test/kotlin/…/StarFieldColourTest.kt`.

*Modificati — codice spedito (1):* `app/src/main/kotlin/…/engine/PaperRenderer.kt`.

*Modificati — sprite spediti (8):* `app/src/main/res/drawable-nodpi/{sun_body,sun_glow,moon_full,
moon_gibbous,moon_half,moon_crescent,moon_jack_o_lantern,star_sparkle}.png`.

*Modificati — test JVM (2):* `…/SkySpriteAnchoringTest.kt`, `…/BackgroundScrollGeometryTest.kt`.

*Modificati — golden (16):* i sedici PNG di `app/src/androidTest/assets/golden/` elencati in §3.4.

*Modificati — pipeline degli asset (14):* `tools/assets/sources/sprites.json`; gli **8** SVG sotto
`tools/assets/sources/svg/`; `tools/assets/reports/{fidelity.json,fidelity.md,comparison-sheet.png,
runtime-inventory.json,runtime-inventory.md}`.

*Modificati — documenti (5):* `RELEASE_HISTORY.md`, `ROADMAP.md`, `DESIGN_NOTES.md`,
`ARCHITECTURE.md`, `CLAUDE.md`.

**Non toccati, e verificati per hash:** `app/build.gradle.kts` (il build type simil-release è stato
aggiunto in locale e **rimosso**, il file è identico a quello dello ZIP di partenza),
`app/src/androidTest/kotlin/…/SceneGolden.kt` (una modifica locale della cartella di output è stata
applicata per prudenza e **ripristinata**, byte-identica), `SettingsGates.kt`,
`SettingsGateScenesTest.kt`, `PeopleGoldenTest.kt`, i tre `gl-*.png`, `app/build.gradle.kts`,
`gradle.properties`, il manifesto.

`sprites-examples.zip` **non è nell'archivio**: non sta dentro l'albero del progetto, sta accanto, e
l'elenco delle voci dello ZIP lo conferma.

---

## 11. Che cosa NON è stato fatto, di proposito

- **Non ho disegnato niente.** Le tre cose che, promuovendo, mi sono sembrate meritevoli di un
  ritocco — il disegno di `sun_body` fuori centro nella sua tela, le tre palline dell'abete che ora
  hanno un'altra forma, la palma — sono **scritte nel backlog e non toccate**.
- **Non ho rimesso in discussione** il concept B, l'alone `c`, la zucca minima, la configurazione 1,
  le lune.
- **Non ho rigenerato i tre golden GL**, e §3.5 dice perché con la misura in mano.
- **Non ho spostato cancelli né tolleranze**, non ho aggiunto scene golden (la zucca resta scoperta:
  voce 41), non ho incrementato la versione.
- **Non ho pubblicato niente**: nessun tag, nessuna release, nessun push, nessun `gh`. Nessun commit
  è stato fatto neppure in locale in questo pass.
- **Non ho dichiarato la release verificata.** La verifica finale è del maintainer, su un'estrazione
  pulita.

## 12. Lo stato in cui lascio il progetto

**v4.23, `versionCode 54`, `versionName "4.23"` — preparata, non pubblicata, non approvata, e non
verificata da me.** L'archivio è il meccanismo di consegna; la pubblicazione è del maintainer e non
è stata presa.
