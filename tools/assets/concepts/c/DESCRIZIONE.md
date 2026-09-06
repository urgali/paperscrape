# Concept C — «Traforo»

**L'idea in una riga.** La mossa della zucca intagliata («carved rather than painted») generalizzata
a tutta la famiglia: il disegno sta in ciò che le forbici hanno **portato via**. I crateri sono fori
passanti in cui si vede il cielo, i raggi sono parte della silhouette del disco, e l'alone è una
linea di taglio perforata.

## Le scelte, voce per voce

- **Livelli di carta.** Il minimo dei tre: uno per le lune e la scintilla, due per il sole
  (silhouette + cuore). Tutto il resto è assenza.
- **Contorni.** Nessuno.
- **Ombra portata.** Nessuna. La profondità della luna piena è suggerita da una fenditura ad arco
  ritagliata parallela al lembo in basso a destra: ombra per sola assenza di carta.
- **Tavolozza.** La più povera: sole 2 toni (quelli attuali), lune **1 tono** (bianco pieno,
  mottling zero — tutta l'informazione è nei tagli, e i fori restano cielo qualunque colore l'utente
  scelga per la luna: è l'unico dei tre concept in cui i crateri sono *garantiti* leggibili sotto
  ogni tinta), scintilla 1 tono.
- **Dettaglio in funzione della dimensione.** I fori sono dimensionati per la lettura a 158 px
  (12–19 unità di raggio ≈ 8–13 px a schermo); il foro della scintilla si legge solo sulle
  scintille grandi e scompare nelle piccole, che tornano alla sagoma classica — dichiarato nel
  sorgente. La linea perforata dell'alone (24 trattini, r 150–158) è deliberatamente discreta:
  il falloff ambientale lo dà già `drawRadialGlow` del renderer.
- **I raggi.** La terza risposta alla nota: né tolti (A) né ammorbiditi (B), ma **fusi nella
  silhouette** — dodici punte triangolari che toccano il raggio 102 con valli ad arco sul raggio
  84, un pezzo unico. Il sole è l'unico dei tre in cui la parola «raggi» descrive la sagoma e non
  un ornamento appeso.
- **Scarto dagli sprite attuali, voce per voce.** `sun_body`: da disco liscio a sole-francobollo in
  un pezzo. `sun_glow`: da gradiente+8 triangoli a **nessun gradiente** e una perforazione ad
  anello. `moon_full`: da crateri dipinti invisibili a crateri forati. `moon_crescent`/`moon_half`:
  sagome identiche alle spedite + fori presso il lembo. `star_sparkle`: cuore forato.

## Geometria e contratti

Tele, convenzioni, ancore e classi di tinta identiche agli sprite spediti (240/396/180, lune
TINTABLE a fill-rule evenodd come `moon_jack_o_lantern`, che questo concept assumerebbe come
fratello naturale in fase 2). Suite JVM degli sprite verde con questi PNG al posto di quelli
spediti (verificato).
