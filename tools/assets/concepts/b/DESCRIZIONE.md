# Concept B — «Forbici»

**L'idea in una riga.** La carta ritagliata *a mano*: ogni sagoma è un poligono con un tremolio
deterministico di ±2–2,6 unità (un taglio di forbici, non un compasso), il sole galleggia sul cielo
con la sua ombra portata, e i raggi non sono tolti ma **domati**: dodici petali smussati al posto
degli otto triangoli acuminati.

## Le scelte, voce per voce

- **Livelli di carta.** Due per corpo (sagoma + cuore per il sole; sagoma + crateri per le lune),
  più l'ombra dove esiste. Il lavoro non lo fanno gli strati: lo fa il bordo.
- **Contorni.** Nessuno. Il bordo è definito dal taglio stesso: 20–28 vertici con jitter fisso,
  scritto nei punti del sorgente (mai generato a runtime).
- **Ombra portata.** **Sì, ed è la firma del concept — ma solo dove c'è luce dietro.** Il sole
  porta la propria sagoma spostata (+6,+8) in nero al 13 %: luce da alto-sinistra, coerente con la
  banda d'ombra del tronco della Quercia. Le lune e la scintilla **non** la portano, per scelta
  scritta nei sorgenti: di notte il cielo dietro la carta è già scuro, un'ombra non ha niente da
  scurire (e nella maschera lunare trascinerebbe la media dei grigi sotto il pavimento del
  MULTIPLY). L'ombra è un fatto del giorno.
- **Tavolozza.** La più stretta dei tre insieme ad A sul sole: i due colori attuali (#F0A03C,
  #F7CE64) + ombra. Lune: un tono (#F4F4F4) + crateri (#E1E1E1), media ≥ 220. Scintilla: il crema
  dei punti-stella, con quattro punte volutamente diseguali.
- **Dettaglio in funzione della dimensione.** Il tremolio di ±2,4 su un raggio di 99 è ~2,4 %: a
  158 px si legge come mano, a 53 px (preview) sparisce senza sporcare. I petali del glow stanno
  nell'anello 150–198 documentato al sito di chiamata; a distanza di braccio leggono come corolla,
  non come dentatura.
- **Scarto dagli sprite attuali, voce per voce.** `sun_body`: da compasso a mano libera + ombra.
  `sun_glow`: da 8 triangoli staccati a 12 petali a punta arrotondata; alone radiale ridotto
  (0,40/0,16) perché la corolla porta già presenza. `moon_full`: da crateri invisibili a crateri
  irregolari leggibili su disco tagliato a mano. `moon_crescent`/`moon_half`: stessa fase, bordo
  vivo. `star_sparkle`: da rombo simmetrico a scintilla asimmetrica (N 82, E 78, S 74, O 76 unità).

## Geometria e contratti

Tele, convenzioni, ancore e classi di tinta identiche agli sprite spediti; il raggio nominale del
disco è 99 (invece di 102) perché l'ombra del sole deve restare dentro la tela 240 — a schermo è
il 3 % di diametro in meno, sotto la soglia del percettibile alla distanza d'uso. Suite JVM degli
sprite verde con questi PNG al posto di quelli spediti (verificato).

---

## Aggiornamento giro 1b (2026-09-05, dopo la scelta del maintainer)

La famiglia è ora **completa: 8 sprite su 8** — aggiunte `moon_gibbous` (stessa geometria di fase
dello sprite spedito, lembo e terminatore tagliati a mano, crateri pieni) e `moon_jack_o_lantern`
(la **proprietà preservata**: resta l'unica luna coi **fori** — faccia intagliata passante,
geometria della faccia identica alla spedita ×3 — e prende da B solo il bordo tagliato a mano e il
tono unico; la distinzione dalla luna ordinaria regge per costruzione: crateri pieni e zero fori
contro undici fori e zero crateri).

**L'alone è in revisione su richiesta del maintainer** («un unico cerchio intorno al sole»): tre
varianti in `alone/{v1,v2,v3}/`, registrate in `alone/varianti.json`, ciascuna col suo compromesso
su gradiente / taglio / ombra. `sun_glow.svg` in `svg/` resta la versione a petali finché il
maintainer non sceglie. `sun_body` non si tocca.

## Aggiornamento giro 1c

Le fotografie del giro 1b hanno chiuso la domanda dell'anello: **nessun anello legge come raggi**
(i raggi irradiano, un anello circonda) e i petali leggevano come margherita per il **distacco**
dal disco. Il giro 1c prova **la corona attaccata** — smerli che escono dal bordo del disco, senza
stacco — in due varianti che differiscono per la sola profondità (`corona/smerli_bassi`, picchi
r=132; `corona/smerli_alti`, r=156), entrambe senza gradiente e con l'ombra di B. Raggio interno
derivato dal `sun_glow` spedito e dal disco di B (base 94 < 96,6; valli 104 > 101,4).

Lune: la faccettatura della zucca è armonizzata al passo del lembo delle altre fasi (40 vertici,
9°/faccia); la versione **con anello esterno** (`zucca_anello/`) affianca quella a tono unico:
sceglie il maintainer. La stella non si tocca (5–7 px reali: nessuna scelta è visibile).

## Aggiornamento giro 1d

**Decisioni del maintainer**: B confermato; **alone = V2** («anello pulito sopra l'alone»); corone
smerlate e V1/V3 chiuse (la corona a smerli alti resta agli atti come il punto in cui il sole
diventa un girasole). V2 è messa a punto su due soli parametri in `alone/v2_rifinita/{a,b,c}/`:
anello a **opacità 1.0** (carta ritagliata, come tutto il resto di B) e gradiente com'è / assente /
riscaldato. Geometria e colore dell'anello invariati; `sun_body` non toccato.

**Zucca rifatta da capo** (`svg/moon_jack_o_lantern.svg`): conservato il dispositivo (unica luna coi
fori; deve dire Halloween a ~100 px), ridisegnato tutto il resto nel linguaggio di B — bordo e fori
tagliati a mano, nessuna coordinata dalla faccia spedita, **niente anello esterno** (il perché è nel
sorgente). `zucca_anello/` del giro 1c rimosso: la domanda è sciolta.

**`moon_full` a 40 vertici**: chiuso il residuo del giro 1c; tutta la famiglia è a 9°/faccia.

**Stelle**: nessun ridisegno dello sprite — la diagnosi è nel report del giro 1d (la leva è nel
renderer, fotografata con build locali usa-e-getta mai entrate nell'archivio).

## Aggiornamento fase 2 / Parte A

**Decisioni del maintainer, chiuse**: alone = `v2_rifinita/c` (anello a compasso, opacità 1,0,
gradiente riscaldato); stelle = configurazione 1 (`s = radius / 16f`); lune B con `moon_full` e
zucca a 40 vertici; `sun_body` invariato.

**La zucca, due varianti** (`zucca/{minima,spinta}/`, registro `zucca/varianti.json`), entrambe
dentro il vincolo — silhouette tonda, un foglio, fori passanti come unico dispositivo. Il giro 1d
aveva portato gli occhi da cunei angolati a rettangoli arrotondati, che leggono come lastre: la
**minima** tiene bordo, naso e bocca a fascia con zanne del 1d e riporta gli occhi a un **cuneo
tagliato a mano**; la **spinta** cerca il «davvero Halloween» dentro il disco — cuneo con
sopracciglio più ripido, **ghigno a sega** con zanne da entrambi i labbri (sempre un foro solo),
e **quattro costolature** affusolate ritagliate nella corona alta, l'unica cosa che il vincolo
lascia aperta. Sceglie il maintainer guardando le catture; `svg/moon_jack_o_lantern.svg` resta
il 1d finché non sceglie.

**La scintilla ridisegnata** (`svg/star_sparkle.svg`, una versione sola, tela 180×180): con la
configurazione 1 la scintilla sta a 10–13 px reali e la forma torna a contare. Vita piena (24–28
unità, 27–31% del raggio: la carta non si taglia più sottile dei ~12 del giro 1a, che a quella
scala diventavano una croce di un pixel), punte diseguali invariate, lati concavi a due segmenti
con tremolio; sedici vertici scritti. Fotografata **solo** con la configurazione 1 in una build
usa-e-getta: la patch alla costante non è in questo archivio (entra nella Parte B).
