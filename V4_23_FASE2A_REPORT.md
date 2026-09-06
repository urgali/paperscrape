# PaperScrape v4.23 — Fase 2, Parte A: il fermo artwork (due zucche, la scintilla per la configurazione 1)

**Consegna: FERMO OBBLIGATORIO DI METÀ PASS, IN ATTESA DEL GIUDIZIO DEL MAINTAINER. Non una release.**
La Parte B (promozione degli otto sprite, configurazione 1 in `PaperRenderer`, golden, documenti)
**non è stata iniziata**: riparte da questo ZIP e da questo report, con la zucca scelta.

Decisioni già prese e non in discussione: **B «Forbici»**, `sun_body` di B invariato, alone
**`v2_rifinita/c`**, stelle **configurazione 1** (`s = radius / 16f`), le cinque lune di B a 40
vertici. In questo archivio **`app/src/main` è identico allo ZIP di partenza** (verificato nel diff
degli archivi, §7): la patch della configurazione 1 è servita solo a una build usa-e-getta e sta
accanto allo ZIP, fuori, come `patch_stelle1-grandi.diff`.

Etichette: **OSSERVATO / MISURATO / DEDOTTO / DICHIARATO**, **NON ATTRIBUIBILE** dove serve.

---

## 0. Baseline — verificata, condizione D non scattata

MISURATO su estrazione pulita dello ZIP del giro 1d (`/home/bober/claude-shit/work_v4_23e/`):

| voce | atteso | osservato |
|---|---|---|
| ZIP di partenza SHA-256 | `429d270a…9d786f` | identico |
| byte / voci | 6 174 885 / 957 | 6 174 885 / 957 |
| `versionCode` / `versionName` | 54 / "4.23" | 54 / "4.23" (non toccati) |
| JVM | 1331 | **1331, 0 falliti, 0 errori, 0 saltati** (dall'XML) |
| strumentati | 148 | **NON eseguita per intero, per istruzione del coordinatore** (sotto) — avviata su APK puliti di questo albero e fermata a 74/148 con 0 fallimenti fino a lì; **non conta** come verifica |
| Python | 108 | **108, OK**; probe `matches_expected: True` (zlib-ng 1.3.1) |
| PNG golden / `@Ignore` | 27 / 0 | 27 (24 Canvas + 3 `gl-*`) / 0 (i tre `grep` hit sono commenti che raccontano un `@Ignore` passato) |

**La verifica di questa metà è ridotta, per istruzione del coordinatore arrivata a suite avviata:** SHA
dell'archivio di partenza, i soli test JVM che leggono i PNG degli sprite (§3: 54/54 con ciascuna
zucca), i test Python, e build più install degli APK concept — perché il giudizio si prende dal
wallpaper vivo, e i file di questa metà vivono sotto `tools/assets/concepts/`, che nessun percorso
testato legge. **La giustificazione non è una promessa ma il diff dei due archivi (§7): nulla fuori
da `tools/assets/concepts/b/` si è mosso, oltre a questo report.** Se il diff avesse mostrato
qualcos'altro, la verifica ridotta non sarebbe stata giustificata e mi sarei fermato. La suite JVM
completa (1331/0) era già stata eseguita prima dell'istruzione ed è riportata come fatto, non come
requisito; la suite strumentata è stata **interrotta** (74/148 eseguiti, 0 fallimenti, pacchetto di
test disinstallato) e non viene contata. **Nella Parte B la contabilità completa torna intera.**

**Gate `PIXEL_IDENTICAL` — rifatto una volta, come da mandato: PASSATO.** `render` in una cartella
di staging fuori dall'albero + `compare --staging`: **`PIXEL_IDENTICAL: 134` su 134**; **79 PNG**
differiscono nei soli byte (involucro zlib-ng), i pixel no — la condizione A non scatta. `compare`
riscrive `tools/assets/reports/{fidelity.json,fidelity.md,comparison-sheet.png}`: i tre file sono
stati **ripristinati dallo ZIP** (verificato per hash), così l'evidenza committata non si muove per
un controllo.

---

## 1. La zucca — due varianti, dentro il vincolo (`tools/assets/concepts/b/zucca/`)

### 1.1 Che cosa è andato storto nel giro 1d, riletto sui sorgenti

OSSERVATO sui due SVG. Il giro 1c aveva occhi a **cuneo** — `48,78 / 99,108 / 90,123 / 54,117`:
alto 39 unità all'esterno, 15 verso il naso, sopracciglio che scende verso il naso — ed erano le
coordinate spedite ×3. Il giro 1d, rifacendo tutto da capo, li ha sostituiti con quadrilateri
`49.7,85 / 94,69.9 / 103.2,98.5 / 63.2,107.5`: **una lastra di ~18 unità di spessore costante,
ruotata di ~19°, con l'estremo interno più alto dell'esterno** — cioè sopracciglia sollevate, non
occhi. Il resto del 1d (bordo a 40 vertici, naso storto, **bocca a fascia con quattro zanne**) ha
funzionato e resta.

### 1.2 Il vincolo, e che cosa lascia aperto

Resta una luna: **silhouette tonda** (il bordo del 1d, intatto), **un foglio**, **fori passanti come
unico dispositivo**. Niente picciolo, niente secondo foglio, niente sagoma da zucca. Dentro questi
limiti le leve sono due sole: **l'espressione della faccia** e **costolature ritagliate nel disco**.

### 1.3 Variante «minima» (`zucca/minima/moon_jack_o_lantern.svg`)

Bordo, naso e bocca del giro 1d **riusati alla lettera** (stesse stringhe di path). Cambiano **solo
gli occhi**: un cuneo tagliato a mano — sei vertici, tremolio ±1,5, jitter diverso per i due occhi
— alto all'esterno (y 76→113 a x≈50), stretto verso il naso (y 106→124 a x≈100), sopracciglio che
scende verso il naso. È **il cuneo del 1c letto come forma, non copiato**: le coordinate sono nuove
(`46,76 / 75,88 / 101,106 / 94,124 / 70,118 / 54,113` + jitter) e il lato lungo ha un vertice in
più, perché un taglio a mano di 55 unità non è una retta.

DEDOTTO dalla geometria sul disco da 158 px di questo schermo: ogni occhio **~36×32 px**; a 100 px
di disco **~23×20 px**. MISURATO sul PNG: contentBox `[20,21,218,218]`, 22 467 pixel opachi, media
**244,1** (la stessa del 1d: cambia la forma dei fori, non la carta).

### 1.4 Variante «spinta» (`zucca/spinta/moon_jack_o_lantern.svg`) — che cosa spinge, e perché

Stesso bordo, stesso naso, stesso vincolo. Tre spinte, **tutte dentro il disco**:

1. **Occhi**: il cuneo, con il sopracciglio **più ripido** (il vertice interno scende a y 111
   invece di 106) e la coda esterna più alta (y 72): lo sguardo si chiude di più. È la spinta
   sull'espressione — «cattivo» si legge dall'angolo del sopracciglio, ed è l'angolo che a 100 px
   sopravvive per primo.
2. **Bocca**: il **ghigno a sega** della zucca intagliata — **un foro solo**, più largo del 1d
   (x 38→202 contro 42→198) e più alto al centro, con **tre zanne dal labbro superiore e due dal
   labbro inferiore**, sfalsate, e gli angoli che **salgono** (sorriso). Il 1d aveva zanne solo in
   alto; la sega interlacciata è il segno più riconoscibile del genere, e a 100 px un profilo a
   zig-zag su entrambi i labbri legge come «denti» dove quattro zanne da un lato leggono come
   «frange».
3. **Costolature**: **quattro solchi affusolati** (larghi 6→2,5 unità, lunghi ~32) ritagliati
   nella corona alta del disco (y 34→74), dove la faccia non arriva, che seguono i meridiani di
   una zucca. Sono l'unica cosa che il vincolo lascia aperta: **trasformano il disco in una zucca
   senza toccare la silhouette**, e sono passanti come tutto il resto (il cielo passa). DEDOTTO: a
   158 px sono ~21 px lunghi e 4→1,6 px larghi; a 100 px ~13 px e 2,5→1 px — è la spinta più
   fragile alla scala, e la fotografia deve dire se leggono come solchi o come graffi.

MISURATO sul PNG: contentBox `[20,21,218,218]`, 21 677 pixel opachi (−790 rispetto alla minima: i
solchi e la bocca più aperta), media **244,1**.

Il registro `zucca/varianti.json` porta le due voci con lo schema di `sources/sprites.json`;
`svg/moon_jack_o_lantern.svg` **resta il 1d** finché il maintainer non sceglie.

**Una nota su un file trovato in `/home/bober/claude-shit`, non nel mandato.** `sprites-examples.zip`
(datato oggi, 14:35) contiene fogli di sprite di carta con feltro, fra cui una zucca-luna
classica. L'ho guardato come contesto ambientale e **non ne ho preso niente**: nessuna coordinata,
nessuna texture (B è carta piatta), nessuna decisione. Lo dico perché sta nella cartella di lavoro.

---

## 2. La scintilla, ridisegnata per la configurazione 1 (`tools/assets/concepts/b/svg/star_sparkle.svg`)

### 2.1 Perché adesso, e perché così

I giri 1b e 1d avevano MISURATO che a 6 px l'artwork non conta. La configurazione 1 (`/16`) porta
le scintille più grandi a **10–13 px reali** (giro 1d, MISURATO in scena): lì la forma torna a
leggersi, e il B del giro 1a aveva un limite che a 6 px era invisibile — **otto vertici a lati
dritti con una vita di ~12 unità su 90 (13 % del raggio)**, che a 13 px si riduce a una croce di
un pixel. L'unico «taglio» era l'asimmetria delle punte.

### 2.2 La versione (una sola, tela 180×180 invariata)

- **Vita piena**: raggio di vita 24–28 unità (27–31 % del raggio, diseguale per quadrante). La
  carta non si taglia più sottile; a 13 px la vita è ~3,5 px invece di ~1,5.
- **Punte diseguali invariate**: N 82, E 78, S 74, O 76 unità (le stesse di B), con l'asse di
  ciascuna ruotato di +1,5 / −2 / +2,5 / −1 gradi.
- **Lati concavi a due segmenti**: fra punta e vita un vertice di mezzo rientra di ~6 unità verso
  il centro, con tremolio ±1,5. **Sedici vertici**, scritti nei punti del sorgente.
- Niente ombra (di notte il cielo dietro è già scuro — scelta di B, invariata). Colore: il crema
  **`#FBF4E6` della scintilla spedita** (che è anche quello del B 1a).

MISURATO sul PNG: contentBox `[14,8,168,164]` — **identico** a quello del B 1a, per costruzione
(stesse punte); 5 151 pixel opachi contro i 3 794 del 1a (+36 %); media 241,8. Scala `SCENE_UNITS`,
ancora `SPRITE_CENTRE (90,90)`, il sito di chiamata non cambia.

**Una cosa vista e non toccata, per la Parte B.** `STAR_POINT_COLOR` in `PaperRenderer` vale
`#FFF6DC` e il suo commento dice «the cream the sparkle art is drawn in»; la scintilla spedita (e
quella di B) è `#FBF4E6`. Differiscono di 4/2/10 livelli: invisibile, ma il commento non è esatto
già oggi. Va nell'elenco delle dipendenze della Parte B, non qui.

### 2.3 Con la configurazione 1, in una build usa-e-getta

La scintilla è stata fotografata **solo** con la patch del giro 1d applicata (`/32 → /16`,
`MAX_STAR_RADIUS_PX ×2`) a una copia di `PaperRenderer.kt`; il file è stato **ripristinato e
verificato byte-identico allo ZIP** dopo la build (§3). Senza patch la fotografia non direbbe
niente, e non è stata fatta.

---

## 3. Come sono state costruite le tre build

Ogni build: gli **otto sprite B** (`sun_body`, `sun_glow` = `v2_rifinita/c`, `moon_full`,
`moon_gibbous`, `moon_half`, `moon_crescent`, la zucca della variante, la scintilla) copiati in
`res/drawable-nodpi` dell'albero di lavoro → `assembleDebug` → **PNG nell'APK verificati byte per
byte contro lo staging (8/8 per ciascuna)** → `res/drawable-nodpi` e `PaperRenderer.kt` ripristinati
e **verificati byte per byte contro lo ZIP di partenza (0 file diversi)**.

| build | zucca | scintilla | `PaperRenderer` |
|---|---|---|---|
| `app-zmin.apk` | minima | B 1a | spedito |
| `app-zsp.apk` | spinta | B 1a | spedito |
| `app-star1.apk` | minima | **nuova** | **configurazione 1** (patch, fuori dall'archivio) |

Le due zucche sono fotografate con la scintilla B 1a e il renderer spedito — **le condizioni dei
giri 1c/1d** — così il confronto è sulla zucca sola.

**Validazione JVM con gli sprite al posto dei spediti** (copia separata dell'albero, sei classi
di test degli sprite: `SkySpriteAnchoringTest`, `SpriteTintClassTest`, `SpriteGeometryTest`,
`SpriteCanvasConventionTest`, `ThemePreviewSceneTest`, `BackgroundScrollGeometryTest`):
**minima: 54 test, 0 falliti, 0 errori; spinta: 54 test, 0 falliti, 0 errori** (MISURATO dall'XML; con la scintilla nuova in entrambi i casi). In particolare `every tinted sprite is light enough for MULTIPLY` (media ≥ 220: 244,1) e `a star sparkle fills the star's radius without exceeding it` restano verdi — quest'ultimo legge la tela, non il disegno, e la tela non cambia.

---

## 4. Le catture

Tutte dal wallpaper vivo sul BV6600 (`screencap` a schermo intero), build `.debug` di §3 installata
sopra la build di debug pulita, **tema Autumn** (verificato dalla galleria: «Autumn - selected», e
dal colore del cielo), **ora fissa 1:00**, **nuvole spente sotto Autumn**, pagina home con l'icona
in alto a sinistra — le condizioni dei giri 1c/1d. In `consegna_v4_23_fase2A/catture/`.

| file | build | condizione |
|---|---|---|
| `notte_zucca_minima.png` | `app-zmin` | Halloween **attivo**, scintilla B 1a, renderer spedito |
| `notte_zucca_spinta.png` | `app-zsp` | idem, zucca spinta |
| `notte_stelle1_scintilla_nuova.png`, `…_b.png` (+3 s) | `app-star1` | Halloween **spento**, fasi realistiche **spente** (luna piena), **configurazione 1**, scintilla nuova — le condizioni di `notte_stelle1-grandi.png` del giro 1d, che è il termine di paragone (stessa configurazione, scintilla B 1a) |

**Le zucche in scena (OSSERVATO, `anteprime/confronto_zucche_scena_3x.png`: 1d / minima / spinta,
stesso ritaglio 240×200 ingrandito 3×).** Sul disco da 158 px la **minima** torna a dire Halloween a
colpo d'occhio: i cunei leggono come occhi cattivi, la bocca a fascia con le zanne resta quella
del 1d, e l'espressione che il 1d aveva perso è di nuovo lì. La **spinta** legge ancora più
«zucca»: il ghigno a sega con i denti da entrambi i labbri è il segno più forte, e i quattro
solchi in alto si vedono come tagli corti nella corona del disco — a questa scala leggono come
costolature *o* come una corona di tagli, e questo è esattamente il giudizio che spetta al
maintainer guardando la cattura, non a me. Nessuna delle due esce dal vincolo: la silhouette è
il bordo del 1d, un foglio, solo fori. `anteprime/foglio_zucche_240_158_100.png` mette i tre
sprite (1d / minima / spinta) tintati `#FF8C2A` a 240, 158 e 100 px.

**La scintilla in scena (MISURATO, `anteprime/confronto_scintilla_config1.png`: stessa regione
540×370 px sotto la luna, x 90–630 / y 380–750, luna esclusa, 3× NEAREST; componenti connesse
sopra 150/255 sul canale massimo).** Le scintille più grandi misurano **11 e 10 px** sia col B 1a
(giro 1d) sia con la nuova: la dimensione la dà la configurazione 1, non l'artwork, com'è giusto.
Quello che cambia è dentro quei 10–11 px: **la nuova ha corpo** — quattro punte con una vita che
si vede, una forma a stella e non una croce di un pixel — mentre il B 1a a 10–11 px è un «+»
sottile (OSSERVATO nel foglio). Il conteggio di stelle sopra soglia (12 / 5 / 6) **non è
confrontabile**: dipende dalla fase del pulsare al momento dello scatto (le due catture a 3 s di
distanza ne danno 5 e 6), non dallo sprite — lo dico perché sta nel foglio. `anteprime/
foglio_scintilla.png` mette i due sprite a 180 px e a 13 / 10 / 7 px.

---

## 5. Contabilità

MISURATO su estrazione pulita dello ZIP di partenza; i conteggi d'archivio **derivati dal diff
albero-vs-ZIP fatto prima di scrivere questi numeri** e riverificati sull'archivio finale.

- **Verifica ridotta (Parte A, per istruzione):** SHA dell'archivio di partenza **identico**; test
  JVM degli sprite **54/54 con la zucca minima e 54/54 con la spinta** (scintilla nuova in
  entrambi); **Python 108/0**, probe conforme; **tre build `assembleDebug`** costruite, installate,
  sprite negli APK verificati 8/8 contro lo staging.
- Eseguito prima dell'istruzione, riportato come fatto: **JVM 1331/0/0** sull'albero pulito; gate
  `PIXEL_IDENTICAL` 134/134.
- **Strumentati: non eseguiti** (interrotti a 74/148, 0 fallimenti fino a lì; non contano).
- **27 golden**, `@Ignore` 0, `versionCode`/`versionName` **54/"4.23" non toccati**.
- **Voci archivio: 963** = 957 (baseline) **+ 6 nuove − 0 rimosse**; **4 modificate** (stesso path).
  Aritmetica: 957 + 6 = 963.

---

## 6. Stato del dispositivo

**OSSERVATO alla riconsegna (2026-09-06, ~15:25 CEST).**

- **Wallpaper attivo:** `com.paperscrape.livewallpaper` — la **release del maintainer** (oggi
  `versionName 4.22`, `versionCode 53`; firma non toccata), mai disinstallata — rimessa come
  wallpaper vivo dal picker (`CHANGE_LIVE_WALLPAPER` → «Set wallpaper», stavolta senza dialogo) e
  **osservata mentre rende**: `mEngine` legato, due `screencap` a 3 s di distanza **diversi**
  (135 885 vs 137 690 byte), scena a ora reale sul launcher.
- **Build sperimentali disinstallate:** `pm list packages` mostra solo il pacchetto release; il
  pacchetto di test `….debug.test` (installato per la suite strumentata) disinstallato.
- **Stato originale annotato e ripristinato:** `screen_brightness_mode` 1 (auto, invariato);
  `screen_off_timeout` 60 000 (invariato); `auto_time` 1 (**mai toccato**);
  `stay_on_while_plugged_in` 0 → 3 durante il pass → **0**. `screen_brightness` era 255
  all'inizio e legge 102 alla fine: **non l'ho scritto io** — con la modalità automatica è il
  sensore a muovere quel valore (DICHIARATO), e il device è stato trovato addormentato dopo la
  suite strumentata e risvegliato via `KEYCODE_WAKEUP`.
- Le impostazioni della build `.debug` (Autumn, 1:00, nuvole spente, Halloween, fasi) sono sparite
  con la disinstallazione; le preferenze della release del maintainer non sono state aperte.

---

## 7. File toccati (dal diff dei due archivi, fatto PRIMA di scrivere questi numeri)

**6 file aggiunti, 0 rimossi, 4 modificati** (diff dei due archivi, `429d270a…` → questa consegna;
963 voci contro 957). **Tutto sotto `tools/assets/concepts/b/`, più il report in radice: niente
sotto `app/`, `tools/assets/sources/`, `tools/assets/reports/` o nei documenti di stato.**

*Aggiunti (6):* `V4_23_FASE2A_REPORT.md`;
`tools/assets/concepts/b/zucca/minima/moon_jack_o_lantern.{svg,png}`;
`tools/assets/concepts/b/zucca/spinta/moon_jack_o_lantern.{svg,png}`;
`tools/assets/concepts/b/zucca/varianti.json`.

*Modificati (4):* `tools/assets/concepts/b/svg/star_sparkle.svg`, `.png` (la scintilla
ridisegnata; il B 1a resta agli atti nello ZIP del giro 1d);
`tools/assets/concepts/b/sprites.concept.json` (voce `star_sparkle` e nota);
`tools/assets/concepts/b/DESCRIZIONE.md` (aggiornamento fase 2 / Parte A).

`app/src/main` **identico** allo ZIP di partenza — in particolare `PaperRenderer.kt` e
`res/drawable-nodpi/` (verificati per hash nel diff, dopo le tre build e i ripristini).

---

## 8. Che cosa NON è stato fatto, di proposito

- **Nessuna scelta fra le due zucche**: è del maintainer e si prende guardando.
- **Parte B non iniziata**: nessuno sprite promosso, `sources/sprites.json` intatto,
  `PaperRenderer` intatto nell'archivio, nessun golden toccato, nessun documento di stato
  aggiornato, backlog intatto.
- `sun_body`, alone `c`, le lune, la configurazione 1: non rimessi in discussione.
- Build type simil-release non costruito (serve alla Parte B4), nessuna misura CPU.
- `lintDebug` non eseguito: `assembleDebug` è servito solo per installare le build usa-e-getta.

## 9. Come riparte la Parte B

Da questo ZIP: `tools/assets/concepts/b/` ha gli otto sprite (zucca: `zucca/<scelta>/`), la
scintilla ridisegnata in `svg/star_sparkle.svg`, l'alone in `alone/v2_rifinita/c/`. La patch della
configurazione 1 è `patch_stelle1-grandi.diff` accanto allo ZIP (due righe di `PaperRenderer.kt`:
`MAX_STAR_RADIUS_PX` e `s = star.radius / 16f`) — e la Parte B2 deve **ri-derivare** entrambe, non
riapplicarle: `MAX_STAR_RADIUS_PX ×2` era la mossa della build usa-e-getta per far stare le tile,
non una derivazione (il raggio massimo delle stelle non è cambiato: è la portata dello sprite che
raddoppia, e sono `STAR_SPRITE_{LEFT,RIGHT}_EXTENT_PX` a doverla coprire — vedi il test
`the star extents cover what the sprite actually reaches`, che con `/16` ha `reach = 30/16·5,6 =
10,5 px` contro estensioni di 5,6).
