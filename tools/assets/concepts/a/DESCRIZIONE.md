# Concept A — «Strati»

**L'idea in una riga.** La ricetta esatta della Quercia larga portata in cielo: ogni corpo è una
pila di carte incollate leggermente fuori centro, la luce arriva da in alto a sinistra, e la
profondità sta tutta nello scarto fra gli strati. Nessun raggio: la nota del maintainer presa alla
lettera.

## Le scelte, voce per voce

- **Livelli di carta.** Quattro per il sole (base ambra scura → colmo giallo pallido) e quattro per
  la luna piena (base `#DCDCDC` → colmo `#FFFFFF`), tre fasce per falce e mezzaluna, due per la
  scintilla. Ogni strato è incollato spostato verso l'alto-sinistra, la stessa direzione della luce
  del colmo della Quercia; gli strati inferiori spuntano in basso a destra e fanno da ombra senza
  che esista un'ombra.
- **Contorni.** Nessuno, come le chiome degli alberi.
- **Ombra portata.** Assente per costruzione: la funzione dell'ombra la svolge lo strato di sotto
  che spunta. È la convenzione della chioma, non quella degli oggetti a terra (l'ellisse di
  ancoraggio non ha senso in cielo).
- **Tavolozza.** Sole: 4 toni caldi della famiglia dei due attuali (#DC8428, #F0A03C, #F7CE64,
  #FBE289). Lune: 4 grigi neutri fra #DCDCDC e #FFFFFF — gli stessi rapporti della chioma
  (pavimento al 13,7 % di mottling, media dei pixel opachi ≥ 220), così il MULTIPLY del colore
  luna del tema attraversa la maschera come attraversa gli alberi. Il passaggio giorno/notte non
  è nel PNG: lo fa il tint runtime, come oggi.
- **Dettaglio in funzione della dimensione.** Il disco è visto a ~158 px su questo schermo: quattro
  strati a scarti di 4–15 unità restano leggibili lì e spariscono con grazia nella preview dei
  temi (~53 px). I crateri sono a 20–27 livelli dal tono su cui poggiano — nello sprite attuale
  erano a 4 livelli, cioè invisibili. La scintilla (10–20 px) affida alla carta di sotto ruotata
  di 45° solo un alone morbido, non un disegno.
- **Scarto dagli sprite attuali, voce per voce.** `sun_body`: da 2 cerchi concentrici piatti a 4
  strati decentrati. `sun_glow`: **i raggi non ci sono più**, resta il solo alone radiale (già
  sanzionato da DESIGN_NOTES per il sole). `moon_full`: da crateri invisibili a strati + crateri
  leggibili. `moon_crescent`/`moon_half`: da silhouette piatta a fasce che condividono punte e
  lembo e variano solo il terminatore. `star_sparkle`: da rombo singolo a doppia carta con cuore
  chiaro.

## Geometria e contratti

Tele, convenzioni di scala, ancore e classi di tinta **identiche** agli sprite spediti: 240/396/180,
`CANVAS_PIXELS`/`SCENE_UNITS`, `SPRITE_CENTRE`, lune TINTABLE e resto FIXED_ART. Nessun sito di
chiamata cambia; `SkySpriteAnchoringTest`, `SpriteTintClassTest`, `SpriteGeometryTest`,
`SpriteCanvasConventionTest`, `ThemePreviewSceneTest`, `BackgroundScrollGeometryTest` passano con
questi PNG al posto di quelli spediti (verificato).
