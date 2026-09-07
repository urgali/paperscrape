# PaperScrape v4.23 — Giro 1b: l'alone di B, le due lune mancanti, il costo dell'ombra

**Consegna: CONCEPT IN ATTESA DI GIUDIZIO, NON UNA RELEASE.** Il maintainer ha scelto il concept
**B «Forbici»**; questo giro non lo rimette in discussione e **non esegue**: produce tre varianti
d'alone da guardare, completa la famiglia B con le due lune mancanti, rifà le catture di sole **alla
pari** (nuvole spente per tutti i termini di paragone), e consegna **la tabella dei due costi**
dell'ombra portata — senza raccomandazione, come la curva del traffico.

`versionCode 54` / `versionName "4.23"` **non toccati** (già a posto dal giro precedente).
Nessuno sprite spedito, golden, cancello o tolleranza toccati; `app/src/main` intatto.

Etichette: **OSSERVATO / MISURATO / DEDOTTO / DICHIARATO**, **NON ATTRIBUIBILE** dove serve.

---

## 0. Baseline — verificata, condizione D non scattata

MISURATO su estrazione pulita dello ZIP consegnato (`/home/bober/claude-shit/work_v4_23b/`):

| voce | atteso | osservato |
|---|---|---|
| ZIP di partenza SHA-256 | `2c9ddc74…d241b843` | identico |
| byte / voci | 5 950 746 / 928 | 5 950 746 / 928 |
| `versionCode` / `versionName` | 54 / "4.23" | 54 / "4.23" (non toccati) |
| JVM | 1331 | **1331, 0 falliti, 0 errori** |
| strumentati | 148 | **148, 0 falliti** (`am instrument`, suite intera, `OK (148 tests)`) |
| Python | 108 | **108, OK** |
| PNG golden | 27 (24 Canvas + 3 GL) | 27, di cui 3 `gl-*` |
| `@Ignore` | 0 | 0 |
| probe rasterizzatore | `ec77e95d…` | `matches_expected: True` |

Il gate `PIXEL_IDENTICAL` **non è stato rifatto**, come da mandato (passato nel pass precedente);
il probe è stato comunque rieseguito perché costa niente e pinna il rasterizzatore di questa
sessione.

---

## 1. L'alone: tre varianti ad anello (`tools/assets/concepts/b/alone/{v1,v2,v3}/`)

`sun_body.svg` di B **non è stato toccato**. Ogni variante sostituisce i 12 petali con **un unico
anello**, e le tre domande del mandato hanno una risposta *disegnata* per ciascuna:

| | taglio | gradiente | ombra |
|---|---|---|---|
| **V1 «Anello di carta»** | a mano (±2,4, 30 vertici, su entrambi i bordi) | **no** | no |
| **V2 «Anello pulito sopra l'alone»** | **a compasso** | **sì** (0,40/0,16, quello dei petali) | no |
| **V3 «Anello spesso con l'ombra»** | a mano | no | **sì** (+6,+8, nero 13 %) |

Le scelte, scritte (il dettaglio è nel commento d'autore di ciascun sorgente):

- **V1** è il linguaggio di B senza compromessi: corona piena 150–176 (26 unità ≈ 17 px a schermo),
  un tono `#F7CE64` a 0,62, niente gradiente — la variante che chiede se l'alone morbido serva
  ancora quando c'è l'anello. Niente ombra: su una corona sottile l'ombra a +6,+8 sarebbe un filo
  ambiguo, e il disco ce l'ha già.
- **V2** ammette che alla dimensione di scena il tremolio su una linea sottile legge come «cerchio
  un po' storto» (il destino già osservato sul bordo della luna di B) e sceglie il compasso:
  corona 154–166 (≈ 8 px a schermo) a 0,75 **sopra il gradiente**. È la variante che misura a
  occhio il rischio «impastato».
- **V3** estende all'anello la firma di B: corona più spessa 148–178 (≈ 20 px, così l'ombra ha dove
  leggersi), ombra della stessa sagoma a +6,+8 al 13 %, niente gradiente.

Tutte e tre stanno nell'anello 150..198 documentato al sito di chiamata; tela 396, convenzione,
origine e classe di tinta invariate → nessun file Kotlin cambia, `SkySpriteAnchoringTest` e
compagnia verdi (MISURATO: quattro esecuzioni della batteria sprite — set petali, V1, V2, V3 al posto degli spediti — tutte PASS; set spediti poi ripristinato e riverificato byte-per-byte).

**La convergenza su C, dichiarata come chiede il mandato.** Il concept C porta già un anello (24
trattini, r 150–158, senza gradiente). **V1 è, in silhouette, «C senza il tratteggio»**: un anello
pieno al posto di uno perforato, tagliato a mano invece che a compasso — a scena, dove il tremolio
vale ~1,6 px, la parentela si vede. Va detto anche il rovescio: il tratteggio di C faceva rima con
la linea di mezzeria della strada, l'anello pieno quella rima non ce l'ha. V2 se ne distanzia col
gradiente e con la corona più sottile e netta; V3 con lo spessore e con l'ombra. La scelta resta al
maintainer, ma V1 non viene consegnata come una cosa mai vista.

---

## 2. Le due lune mancanti (`tools/assets/concepts/b/svg/`)

La famiglia B è ora **8 su 8**. Registrate in `sprites.concept.json` con riquadri misurati dai PNG.

- **`moon_gibbous`** — la geometria di fase è quella dello sprite spedito (lembo esterno r=99 a
  destra, terminatore sull'ellisse rx=54 che sporge a sinistra fino a x≈66: più di metà disco
  illuminato, l'A18×3 della correzione v76.1), eseguita col taglio di B: lembo ±2,2, terminatore
  ±1,8, **due crateri pieni** `#E1E1E1`. Maschera neutra, media pixel opachi **243,1** (MISURATO).
  In scena poggia sul disco scuro «earthshine» che il renderer disegna sotto, che ricompone il
  cerchio.
- **`moon_jack_o_lantern`** — **la proprietà preservata: resta l'unica luna del progetto coi
  fori.** Occhi, naso, bocca e denti sono ritagli passanti (evenodd), e la **geometria della
  faccia è quella spedita, ×3, invariata** — è ciò che la rende riconoscibile a ottobre. Il
  contributo di B è il bordo (taglio a mano ±2,4 al posto del compasso) e il tono unico `#F4F4F4`
  al posto dei tre anelli concentrici dello sprite spedito. Media **244,1** (MISURATO).

**La verifica di distinguibilità chiesta dal mandato.** La distinzione non si assottiglia, per
costruzione: la luna ordinaria di B ha **crateri pieni e zero fori** (0 pixel trasparenti dentro il
disco), quella di Halloween ha **11 fori e zero crateri** — il cielo passa attraverso la faccia con
qualunque tinta. Il dispositivo «traforo» resta speso su una sola luna. Vista nelle catture, la
faccia legge inconfondibile (`catture/notte_b_halloween.png`).

---

## 3. Le catture — alla pari (`consegna_v4_23_giro1b/catture/`)

Il difetto del giro scorso, riconosciuto: 42 minuti fra la prima e l'ultima cattura di giorno, con
le nuvole a ~40 px/min — B col sole coperto, C in cielo libero. **Questo giro: nuvole SPENTE per
tutte le catture di sole**, stessa ora fissa (12:00), stesso tema (Autumn), stessa scena.

**Elenco (10 file in `catture/`):**

| file | condizione |
|---|---|
| `giorno_rif_v4_22.png` | riferimento v4.22 (sprite spediti), Autumn 12:00, nuvole spente — **il termine di paragone che mancava** |
| `giorno_b_petali.png` | B coi petali del giro 1a, **per la prima volta in cielo libero** |
| `giorno_alone_v1.png` / `_v2` / `_v3` | le tre varianti d'anello, stesse identiche condizioni |
| `notte_b_gibbosa.png` | la gibbosa B in scena (fasi reali; data del device portata al 31 ago 2026 per ottenere la fase, poi **ripristinata via rete** — l'algoritmo del progetto legge il clock reale, non l'ora fissa della scena) |
| `notte_b_halloween.png` | la luna di Halloween B (decorazione attivata e poi disattivata) |
| `notte_b_stelle_ritaglio_3x.png` | **il ritaglio ingrandito delle stelle**: 540×370 px della cattura vera, NEAREST 3×, didascalia con la dimensione reale — in questo fotogramma le stelle misurano **5–7 px** (twinkle in corso), col massimo teorico della scintilla a **~11 px** (raggio 5,6 × 0,9375 × 2) da uno sprite 180×180: riduzione ~16× |
| `notte_b_luna_piena_dal_giro_precedente.png` | copia della cattura del giro 1a, per l'affiancamento chiesto dal mandato |

Tutte a schermo intero dal wallpaper vivo (le `giorno_*` contengono gli alberi v4.21 in basso nello
stesso fotogramma); `anteprime/confronto_soli.png` affianca i cinque soli per comodità — è un
montaggio di catture reali, dichiarato come tale, e serve alla comodità, non al giudizio.

**Una cosa che il ritaglio delle stelle dice e che va scritta (OSSERVATO):** a 5–7 px reali,
l'asimmetria «tagliata a mano» della scintilla B (punte 82/78/74/76) è al limite del percettibile —
la stella legge come un twinkle leggermente irregolare, non come carta tagliata. Se il maintainer
vuole che il taglio si veda anche nelle stelle, il posto dove agire è la costante dei raggi in
`regenerateStars` (2,4–5,6 px), che però è fuori dal perimetro artwork: da decidere in fase 2, non
qui.

---

## 4. I due costi dell'ombra portata — la tabella, senza raccomandazione

B introduce l'ombra portata; oggi su 134 sorgenti SVG solo 3 usano un'ombra (tutte neve). Se
l'ombra diventa stile di casa, tocca 266 sprite. I numeri del coordinatore (266 sprite,
30 254 580 B decodificati, margine 154 124 B = 0,51 %, 166 `person_*` tutti a tela stretta) sono
stati **riusati e non rifatti**; quelli nuovi sono qui sotto.

### Via delle tele (ombra cotta negli sprite) — MISURATO

Ombra nel linguaggio di B — silhouette dello sprite spostata di **+6,+8 unità locali** (la
convenzione del sole), nero al 13 % — **disegnata davvero** su tre sprite rappresentativi
(i PNG dimostrativi sono in `tools/assets/concepts/b/ombra_misura/`, prodotti dal canale alfa dello
sprite spedito, offset in pixel sprite = unità × 3 per gli SCENE_UNITS):

| sprite | tela oggi | tela con l'ombra | crescita |
|---|---|---|---|
| `person_boy_summer_head_window` (persona, 0 px di margine) | 159×171 | **177×195** | **+29 304 B (+26,9 %)** |
| `house_large_wall` (edificio, TINTABLE) | 420×285 | **438×309** | **+62 568 B (+13,1 %)** |
| `car_body_saloon` (veicolo, TINTABLE) | 327×150 | **345×174** | **+43 920 B (+22,4 %)** |

**Proiezione sull'intero insieme, calcolata sprite per sprite** (offset per convenzione: +18,+24 px
sprite per i 258 SCENE_UNITS, +6,+8 per gli 8 CANVAS_PIXELS; margini reali letti dal canale alfa di
ogni PNG; tele arrotondate alla griglia 3×):

| | valore |
|---|---|
| footprint decodificato oggi | 30 254 580 B |
| footprint con l'ombra su tutti i 266 | **36 625 756 B** |
| crescita | **+6 371 176 B = +6,08 MiB (+21,06 %)** |
| margine attuale | 154 124 B |
| **sfondamento** | **41,3 volte il margine; il tetto dei 29 MiB è superato di 6 217 052 B** |

Tre complicazioni **osservate** durante la misura, che il numero da solo non dice:

1. **36 sprite su 266 sono TINTABLE** (maschere): un'ombra semitrasparente dentro una maschera è
   moltiplicata dal colore dell'utente e trascina la media dei grigi verso il pavimento del
   MULTIPLY — è esattamente il vincolo scoperto sulle lune di B nella fase 1, dove l'ombra è stata
   tolta per questo.
2. **Gli oggetti composti** (una casa è 4–7 sprite) non hanno un pezzo che possa portare l'ombra
   dell'insieme: l'ombra della sagoma-unione o si spezza per parte (cuciture e doppie ombre dove le
   parti si sovrappongono) o richiede un pezzo nuovo.
3. **L'ancora.** Nei `CONTENT_BOTTOM_CENTRE` l'ombra sotto i piedi entra nel contentBox e sposta
   l'ancora di +24 px sprite, oltre a sovrapporsi all'ellisse d'ancoraggio procedurale che gli
   oggetti a terra già hanno.

### Via del codice (ombra procedurale) — DEDOTTO dai numeri già misurati, NON implementata

`drawGroundShadow` dimostra che il renderer sa disegnare ombre procedurali. Un'ombra di carta come
«stessa blit, tinta nera 13 %, spostata +6,+8, disegnata prima» lascia le tele intatte e raddoppia
le blit degli sprite che la portano:

| | oggi (audit v4.22, MISURATO) | con l'ombra su ogni sprite (DEDOTTO) |
|---|---|---|
| draw call/frame, scena affollata (giorno) | **24,9** | **~49,8** |
| draw call/frame, scena quieta (notte) | **69,1** | **~138,2** |

Sul budget di frame: oggi il processo spende **14,72 ms su 33,33 (44,2 %)** sul BV6600. Il raddoppio
riguarda **solo il disegno degli sprite**, non l'update né i riempimenti di cielo/colline, quindi il
costo vero sta **fra 14,72 e ~29,4 ms** (il raddoppio pieno, che è il tetto teorico peggiore:
88,3 % dell'intervallo): dove esattamente, **lo dice solo una misura col protocollo di
`V4_22_MISURA_CPU_REPORT.md`** sulla build che la implementa — che questo giro, da mandato, non
costruisce. Da notare che la scena **notturna** è quella messa peggio (69→138 draw call proprio nel
percorso che già alterna 5 texture decine di volte per frame, l'anomalia registrata dall'audit), ed
è anche la scena dove B **non** disegna ombre per scelta («di notte non c'è niente da scurire») —
se la politica di stile adottasse la regola di B, il raddoppio colpirebbe solo il giorno (24,9→49,8)
e la notte resterebbe con le blit di oggi. Questo è un fatto del *concept*, non una raccomandazione.

**Il confronto, come lo chiede il mandato:**

| via | dove sbatte | numero |
|---|---|---|
| ombra cotta nelle tele | tetto di memoria sprite (29 MiB) | **+6,08 MiB: 41× il margine** |
| ombra dal codice | budget di frame (33,33 ms) | **draw call ×2 (24,9→49,8 giorno; 69,1→138,2 notte); CPU da 44,2 % a un valore fra 44,2 % e 88,3 %, da misurare** |

**La scelta è del maintainer.** Nessuna raccomandazione.

### Condizione L — terze vie venute in mente durante la misura, scritte e NON implementate

1. **La regola di B come perimetro:** l'ombra solo dove c'è luce dietro (il giorno; il cielo). Gli
   oggetti *a terra* hanno già l'ellisse d'ancoraggio: la politica potrebbe limitarsi a sostituire
   quell'ellisse con la silhouette spostata (stesso numero di blit di oggi, zero crescita tele) e
   riservare l'ombra piena agli oggetti di cielo, che sono pochi.
2. **Ombra clippata alla linea di base** per gli oggetti a terra (il pezzo d'ombra che «cade» oltre
   la base non esiste su carta appoggiata): riduce la crescita delle tele da +24 px verticali a 0
   per tutti gli sprite appoggiati, lasciando solo i +18 orizzontali.
3. **Un'ombra per oggetto composto invece che per sprite** (la sagoma-unione, disegnata una volta
   per casa/edificio): meno del raddoppio pieno di draw call, niente cuciture — ma richiede al
   renderer la nozione di «sagoma dell'oggetto», che oggi non esiste.

---

## 5. Contabilità

MISURATO in questa sessione, su estrazione pulita dello ZIP di partenza:

- **JVM 1331/0/0** (`testDebugUnitTest`, contati dagli XML).
- **Strumentati 148/0** (`am instrument`, suite intera, non filtrata).
- **Python 108/0** (`unittest discover`), probe del rasterizzatore conforme.
- **PNG golden 27** (24 Canvas + 3 GL), `@Ignore` 0.
- **Voci archivio: 943** = 928 (baseline) + 15 nuove − 0 rimosse. Le nuove:
  `V4_23_GIRO1B_REPORT.md`; `tools/assets/concepts/b/svg/{moon_gibbous,moon_jack_o_lantern}.{svg,png}` (4);
  `tools/assets/concepts/b/alone/{v1,v2,v3}/sun_glow.{svg,png}` (6); `tools/assets/concepts/b/alone/varianti.json`;
  `tools/assets/concepts/b/ombra_misura/*.png` (3, i dimostrativi della misura §4).
- File modificati (stesso path): `tools/assets/concepts/b/sprites.concept.json` (ora 8 voci),
  `tools/assets/concepts/b/DESCRIZIONE.md` (appendice giro 1b).
- `versionCode`/`versionName`: **54/"4.23", non toccati**.

---

## 6. Stato del dispositivo

**OSSERVATO alla riconsegna (2026-09-06, ~00:50 CEST).**

- **Wallpaper attivo:** `com.paperscrape.livewallpaper` — la **release del maintainer**, firma
  ≠ `debug.keystore`, **mai toccata né disinstallata** — rimessa come wallpaper vivo e **osservata
  mentre rende** (scena notturna Autumn a ora reale, `mEngine` legato).
- **Build concept disinstallata:** `pm list packages` mostra solo il pacchetto release. Gli APK
  installati durante il giro erano `applicationIdSuffix=".debug"`, costruiti da questo albero e con
  gli sprite verificati byte-per-byte contro lo staging della pipeline (**32/32**) prima
  dell'installazione.
- **Data e ora del dispositivo:** per fotografare la gibbosa (l'algoritmo della fase legge il clock
  reale) `auto_time` è stato portato a 0 e la data al **31 ago 2026** via UI di sistema; a fine
  catture `auto_time` è tornato a **1** e la data è **rientrata via rete** (verificata: 6 set 2026).
  Valore originale di `auto_time`: 1, annotato prima del cambio.
- **Schermo ripristinato:** `screen_brightness_mode` 1 (auto), `screen_brightness` 47,
  `screen_off_timeout` 60 000 — i valori annotati a inizio giro.
- **Halloween:** attivato solo per la cattura, poi disattivato (verificato a schermo).

---

## 7. File toccati (dal diff dei due archivi)

Derivata dal **diff dei due archivi** (`2c9ddc74…` → l'archivio di questa consegna): **15 file
aggiunti, 0 rimossi, 2 modificati.**

*Aggiunti (15):*
- `V4_23_GIRO1B_REPORT.md` (questo report)
- `tools/assets/concepts/b/svg/moon_gibbous.svg` + `.png`
- `tools/assets/concepts/b/svg/moon_jack_o_lantern.svg` + `.png`
- `tools/assets/concepts/b/alone/v1/sun_glow.svg` + `.png`, `v2/…` (2), `v3/…` (2)
- `tools/assets/concepts/b/alone/varianti.json`
- `tools/assets/concepts/b/ombra_misura/person_boy_summer_head_window_ombra.png`,
  `house_large_wall_ombra.png`, `car_body_saloon_ombra.png` (i dimostrativi della misura §4)

*Modificati (contenuto, stesso path):*
- `tools/assets/concepts/b/sprites.concept.json` — da 6 a 8 voci
- `tools/assets/concepts/b/DESCRIZIONE.md` — appendice giro 1b

**Nessun file fuori da `tools/assets/concepts/b/` è cambiato**, tranne l'aggiunta del report in
radice. Sprite spediti, sorgenti in `sources/svg/`, golden, `SettingsGates`, `app/src/main`,
`build.gradle.kts`: byte-identici allo ZIP di partenza (verificato dal diff).

---

## 8. Che cosa NON è stato fatto, di proposito

- Non si è scelta la variante d'alone né la via dell'ombra: decisioni del maintainer.
- `sun_body` di B non toccato; sprite spediti, golden, cancelli, tolleranze, versione: intatti.
- L'ombra procedurale non è stata implementata (mandato §4); le terze vie sono scritte, non fatte.
- Il gate `PIXEL_IDENTICAL` non rifatto (mandato §0); probe rieseguito e conforme.
- `lintDebug` non eseguito: giro di concept e misura, `assembleDebug` è servito solo come
  strumento di fotografia.
