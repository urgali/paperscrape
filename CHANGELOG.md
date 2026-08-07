# Changelog

Ogni versione qui corrisponde a uno zip consegnato in chat e a un commit sul
repository GitHub dell'utente. Da qui in avanti ogni output viene versionato:
il file consegnato si chiama `PaperScrape_vN.zip` e questa voce di changelog
ne riassume il contenuto, così è sempre chiaro cosa contiene ogni commit
(`v1`, `v2`, `v3`, ...) senza dover confrontare i diff a mano.

## v3 — in corso

- **Rename completo**: il progetto non si chiama più PaperScape ma
  **PaperScrape** — package Kotlin (`com.paperscrape.livewallpaper`),
  `applicationId`, nome app, tema Compose (`PaperScrapeTheme`), stile
  Android (`Theme.PaperScrape`), tutti i riferimenti in README/CONTRIBUTING.
- **Gatti rimossi** da tutta l'app (oggetti di scena, suoni, generatore
  casuale, testi UI).
- **Babbo Natale in slitta**: nuovo evento periodico (tema Natale) — a
  intervalli casuali attraversa il cielo trainato da due renne lanciando
  regali.
- **Sezione Wiki** aggiunta al README (temi, oggetti/interazioni,
  impostazioni spiegate in tabelle).
- **Roadmap aggiornata**: rimosso l'obiettivo "suoni reali"; aggiunto un
  nuovo obiettivo prioritario, "tema automatico per data/periodo"; annotata
  la pianificazione per collegare l'editor temi personalizzato
  all'automatismo data-based (vedi sezione Roadmap nel README).
- Introdotto questo changelog e la convenzione di versioning.

## v2

- Corretti 2 errori di compilazione emersi dalla CI (`companion object` non
  ammesso in una `inner class`; opt-in mancante per l'API sperimentale
  `TopAppBar` di Material3).
- Silenziato il warning AGP su `compileSdk 36` non ancora certificato.
- Aggiornate le action della CI (`checkout`, `setup-java`, `setup-gradle`,
  `upload-artifact`) alle versioni più recenti compatibili con Node 24,
  risolvendo gli avvisi di deprecazione.
- Rimossi tutti i riferimenti testuali a prodotti di terzi da
  README/CONTRIBUTING/commenti nel codice.

## v1

- Prima release funzionante: motore di rendering a strati di carta con
  parallasse, ciclo giorno/notte, 4 temi colore base (Tramonto, Autunno,
  Inverno, Deserto).
- Oggetti animati e interattivi (auto, cani, gatti, case, alberi) con
  reazione al tocco e suono sintetico.
- 5 temi stagionali/festivi aggiuntivi come scene distinte (Natale,
  Capodanno con fuochi d'artificio, Spiaggia, Grande città, Tundra).
- Funzione Randomize: generazione procedurale di temi/oggetti.
- Struttura repo pronta per GitHub: licenza MIT, `.gitignore`, CI GitHub
  Actions, README, CONTRIBUTING.
