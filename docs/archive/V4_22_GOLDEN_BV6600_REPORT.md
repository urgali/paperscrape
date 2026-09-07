# PaperScrape — voce 35 e rigenerazione dei golden Canvas sul BV6600

**Data:** 2026-09-05 (secondo pass sul dispositivo nuovo, stessa sessione)
**Etichetta della consegna: infrastruttura di test su v4.22 pubblicata, non una release.**
`versionCode 53` / `versionName "4.22"` invariati. Non si prepara né si attende alcuna
pubblicazione: l'albero è quello della 4.22 già pubblicata, e ciò che cambia sono i file di prova.

Ogni affermazione è etichettata **OSSERVATO**, **MISURATO**, **DEDOTTO**, **DICHIARATO** o
**NON ATTRIBUIBILE**.

**Punto di partenza:** `PaperScrape_v4_22.zip`
`876faa353a4ca6bb06c46d6c02fc6a6bce83b4dcaeba21d716a0509ffda27028`, 5 764 294 byte, 883 voci —
verificato prima di toccare qualsiasi cosa. Il build type `perf` e `local.properties` sono rimasti
locali e fuori dall'archivio.

---

## 0. Esito, in tre righe

La voce **35 si chiude con una diagnosi confermata**: l'estrazione del calcolo del riquadro porta
le locali da `0.0` a `−16.0 / 9.0` e il test torna verde per la ragione giusta. La condizione **J
non è scattata**: il difetto è confinato a quel metodo. I 24 golden Canvas sono stati
**classificati uno per uno come differenze di renderer** e poi rigenerati; la voce **36 si chiude**
con il pavimento efficace di tutti e quattro i cancelli a **0,0000%** e **nessun limite spostato**.

---

## 1. Voce 35 — l'esperimento, per primo e per una ragione

### 1.1 Perché prima

Se questo dispositivo sbagliasse aritmetica nei metodi grandi, rigenerare 24 golden qui sarebbe
sbagliato: `SceneObjectRenderer` è pieno di metodi enormi. Quanto è circoscritto il difetto andava
stabilito **prima** di benedire qualunque immagine.

### 1.2 Che cosa è stato cambiato, e che cosa no

Il calcolo del riquadro del vetro è stato estratto dal corpo di `noOccupantPixelLeavesTheGlass` in
un helper piccolo, `glassPane(type, shell)`, nel companion della stessa classe di test.

**Non è cambiato niente di ciò che il test asserisce:** non la tolleranza, non i bordi, non la
geometria, non il criterio di colore, non i nomi delle variabili al sito di chiamata. Stesse
costanti, stessi rami, stesso ordine. È cambiato **dove** l'aritmetica viene valutata, e nient'altro.

Gli altri **tre siti** che calcolano lo stesso riquadro nello stesso file sono stati **lasciati
inline apposta**, come controllo.

`VehicleOccupantScaleTest` sta in `androidTest` e non entra nell'APK: `app/src/main` non è stato
toccato.

### 1.3 L'esito — MISURATO, il valore che le locali assumono prima e dopo

| | `paneT` | `paneB` |
|---|---|---|
| **prima**, calcolo inline nel metodo grande | **0.0** | **0.0** |
| **dopo**, stesso calcolo in `glassPane` | **−16.0** | **9.0** |
| **dopo**, ramo del camion (`FIRE_TRUCK`) | **−13.0** | **6.0** |

Il log completo copre tutte e dodici le combinazioni (tre carrozzerie × PLAIN, più TAXI, POLICE e
FIRE_TRUCK, × due corsie): i valori sono corretti su tutte, **compreso il ramo del camion, che
prima non veniva nemmeno preso**. Nel pass precedente le costanti leggevano già `−16.0` e `9.0`
*nella stessa riga di log* in cui le locali valevano `0.0`.

```
PANEDIAG: type=PLAIN      shell=COMPACT lane=0.834 paneL=-28.0 paneR=34.0 paneT=-16.0 paneB=9.0
PANEDIAG: type=PLAIN      shell=SALOON  lane=0.834 paneL=-27.0 paneR=32.0 paneT=-16.0 paneB=9.0
PANEDIAG: type=PLAIN      shell=ESTATE  lane=0.834 paneL=-30.0 paneR=30.0 paneT=-16.0 paneB=9.0
PANEDIAG: type=TAXI       shell=COMPACT lane=0.834 paneL=-28.0 paneR=34.0 paneT=-16.0 paneB=9.0
PANEDIAG: type=POLICE     shell=SALOON  lane=0.834 paneL=-27.0 paneR=32.0 paneT=-16.0 paneB=9.0
PANEDIAG: type=FIRE_TRUCK shell=null    lane=0.834 paneL=-33.5 paneR=-4.0 paneT=-13.0 paneB=6.0
```

**Il test è verde per la ragione giusta:** il riquadro non è più degenere, quindi i pixel degli
occupanti — che il pass precedente aveva già misurato *dentro* il vetro — non sono più contati come
fuori. **Non è stata provata nessuna variante**: un solo esperimento, un solo esito.

### 1.4 Condizione J — NON scattata, con quattro prove

| prova | esito |
|---|---|
| gli altri tre siti dello stesso calcolo, lasciati inline | i loro test **passano** |
| la stessa espressione in un metodo piccolo (pass precedente) | dava già **−16.0 / 9.0** |
| l'intera suite strumentata dopo l'estrazione | **148 / 148**, nessun altro test mostra il sintomo |
| i 24 golden Canvas | **solo** differenze di renderer, **nessuna** differenza di contenuto |

L'ultima riga è la prova che riguarda il renderer, ed era già in mano prima di rigenerare: se
`SceneObjectRenderer` — che è pieno di metodi grandi — sbagliasse aritmetica, si vedrebbe come
contenuto spostato o assente, non come dithering di un gradiente. **Dopo la rigenerazione quella
rassicurazione è più forte, non più debole**, perché il confronto passa da cross-device a
same-device: ogni frame combacia col suo golden a **zero pixel differenti**.

### 1.5 Che cosa resta non dimostrato

Il **meccanismo** — perché su ART di Android 10 due locali di un metodo con oltre 39 registri dex
valutino `0.0` — **non è dimostrato, solo circoscritto**. Il dex contiene le costanti giuste, quindi
non è il compilatore. La voce si chiude con una diagnosi verificata sperimentalmente e un rimedio
minimo, **non** con una spiegazione del runtime, e il report lo dice invece di lasciarlo intendere.

---

## 2. L'attribuzione delle 24 scene, prima di rigenerare qualsiasi cosa

### 2.1 Il metodo

Per ciascuna delle 24 scene: l'immagine di differenza contro il golden committato, il conteggio dei
pixel sopra tolleranza (`CHANNEL_TOLERANCE = 8`) **sul frame intero e su ogni rettangolo di focus
che quella scena asserisce**, e la classificazione.

I conteggi vengono dall'**aritmetica del progetto**, non da una mia reimplementazione: una sonda
locale in `SceneGolden.assertMatches` stampa `countDiffering` sul frame e su ogni `GoldenFocus`
della lista che la scena dichiara, con le sue coordinate e il suo limite. Le verifiche del test
sono rimaste intatte; è stato aggiunto solo il log.

**Il numero che decide la classificazione è la dimensione della componente connessa** dei pixel
sopra tolleranza. Un oggetto intero spostato, assente o ricolorato produce una componente di
centinaia o migliaia di pixel; l'antialiasing produce granelli.

### 2.2 La tabella, tutte e ventiquattro

| scena | frame: px >tol / frac (lim 0,2000%) | componenti connesse (max blob) | amp. max | sotto-tolleranza 1–8 | focus asseriti: px / frac / limite | classificazione |
|---|---|---|---|---|---|---|
| `day` | 58 / **0.0201%** | 48 (max **4 px**) | 37 | 31.54% | **sun glow** (53,33)-(307,287): 0/64516 = 0.0000% (lim 2.0000%); **pavement** (0,546)-(360,655): 48/39240 = 0.1223% (lim 2.0000%) | **RENDERER** |
| `dusk` | 49 / **0.0170%** | 40 (max **3 px**) | 31 | 36.21% | — | **RENDERER** |
| `lake-boats` | 59 / **0.0205%** | 45 (max **4 px**) | 39 | 25.71% | — | **RENDERER** |
| `lake-busy` | 59 / **0.0205%** | 48 (max **4 px**) | 37 | 24.07% | — | **RENDERER** |
| `lake-dolphin-leap` | 59 / **0.0205%** | 45 (max **4 px**) | 39 | 22.35% | **dolphin 0 at its apex, inside sailboat 0's sail** (4,424)-(40,446): 2/792 = 0.2525% (lim 2.0000%); **dolphin 2 mid-climb, inside sailboat 2's sail** (300,454)-(336,476): 0/792 = 0.0000% (lim 2.0000%) | **RENDERER** |
| `lake-empty` | 58 / **0.0201%** | 48 (max **4 px**) | 37 | 29.37% | — | **RENDERER** |
| `night` | 187 / **0.0649%** | 121 (max **5 px**) | 52 | 37.92% | — | **RENDERER** |
| `overcast` | 57 / **0.0198%** | 47 (max **4 px**) | 37 | 34.50% | — | **RENDERER** |
| `people-commercial` | 104 / **0.0361%** | 68 (max **11 px**) | 40 | 40.44% | **restaurant frontage** (288,562)-(306,582): 0/360 = 0.0000% (lim 2.0000%) | **RENDERER** |
| `people-mixed` | 94 / **0.0326%** | 72 (max **4 px**) | 28 | 38.25% | **pavement** (0,546)-(360,655): 34/39240 = 0.0866% (lim 2.0000%) | **RENDERER** |
| `people-overlap` | 26 / **0.0090%** | 18 (max **4 px**) | 31 | 37.18% | **pavement** (0,546)-(360,655): 8/39240 = 0.0204% (lim 2.0000%) | **RENDERER** |
| `people-single` | 58 / **0.0201%** | 48 (max **4 px**) | 37 | 31.54% | **pavement** (0,546)-(360,655): 48/39240 = 0.1223% (lim 2.0000%); **pavement density gate** (0,546)-(360,655): 48/39240 = 0.1223% (lim 0.1415%) | **RENDERER** |
| `people-skin` | 73 / **0.0253%** | 44 (max **12 px**) | 37 | 33.41% | **pavement** (0,546)-(360,655): 53/39240 = 0.1351% (lim 2.0000%) | **RENDERER** |
| `people-skyscraper` | 135 / **0.0469%** | 73 (max **11 px**) | 36 | 42.42% | **left tower windows** (154,506)-(184,532): 0/780 = 0.0000% (lim 2.0000%); **right tower windows** (218,536)-(234,562): 0/416 = 0.0000% (lim 2.0000%) | **RENDERER** |
| `people-window` | 21 / **0.0073%** | 17 (max **2 px**) | 29 | 36.05% | **facades** (0,376)-(360,636): 21/93600 = 0.0224% (lim 2.0000%) | **RENDERER** |
| `rain` | 56 / **0.0194%** | 47 (max **4 px**) | 37 | 36.34% | — | **RENDERER** |
| `shops-closed-night` | 213 / **0.0740%** | 131 (max **5 px**) | 48 | 39.56% | **facades band** (0,376)-(360,636): 100/93600 = 0.1068% (lim 1.0038%) | **RENDERER** |
| `snow` | 106 / **0.0368%** | 84 (max **4 px**) | 28 | 36.62% | — | **RENDERER** |
| `theme-city` | 248 / **0.0861%** | 151 (max **9 px**) | 49 | 42.47% | — | **RENDERER** |
| `thunderstorm` | 56 / **0.0194%** | 47 (max **4 px**) | 37 | 34.45% | — | **RENDERER** |
| `traffic-day` | 165 / **0.0573%** | 111 (max **8 px**) | 39 | 32.23% | — | **RENDERER** |
| `traffic-day-sparse` | 120 / **0.0417%** | 80 (max **8 px**) | 39 | 32.13% | **road band** (0,626)-(360,703): 63/27720 = 0.2273% (lim 3.8041%) | **RENDERER** |
| `traffic-night` | 285 / **0.0990%** | 182 (max **7 px**) | 60 | 38.45% | — | **RENDERER** |
| `traffic-night-quiet` | 189 / **0.0656%** | 117 (max **7 px**) | 60 | 38.24% | **road band** (0,626)-(360,703): 0/27720 = 0.0000% (lim 7.0057%) | **RENDERER** |

### 2.3 Che cosa dicono questi numeri

- **Sotto tolleranza:** il 22–42% di ogni frame differisce di **1–8 livelli su 255**. È il
  dithering del gradiente del cielo di un'altra Skia, e nelle immagini di differenza si vede come
  bande orizzontali. Per la metrica del progetto questi pixel **non differiscono**: non sono «aree
  ampie di colore diverso», sono lo stesso colore arrotondato diversamente.
- **Sopra tolleranza:** lo 0,0073–0,0990% del frame, in **17–182 componenti separate**, e **la
  componente più grande su tutte e 24 le scene è di 12 pixel**. Ampiezza massima 60/255.
- **Nessun oggetto** manca, si sposta o cambia colore: le sagome combaciano tutte.

**Classificazione: RENDERER per tutte e 24. La condizione K non è scattata su nessuna scena.**
Tutti i frame stavano già sotto il limite di 0,2000% e tutti i 16 rettangoli di focus sotto il
proprio: le asserzioni passavano già, e la rigenerazione non copre un rosso.

Immagini di differenza per tutte e 24 in `attribuzione/` (blu = sotto tolleranza, rosso = sopra).

---

## 3. La rigenerazione

### 3.1 Come sono stati prodotti i frame

Con `-e updateGoldens true`, che scrive il frame reso senza mai confrontarlo, guidato da
`am instrument` e **non** da Gradle con un filtro di classe — quello disinstalla il pacchetto a
fine esecuzione e si porta via i file scritti dai test (trappola già incontrata in questa sessione).

**I frame catturati per la rigenerazione sono byte-identici a quelli delle tre esecuzioni del pass
precedente** — processo riavviato e telefono riavviato compresi. È la **quarta** esecuzione con la
stessa impronta: il pavimento di rumore resta 0,0000% (voce 31) e i golden nuovi non sono un
campione fortunato.

### 3.2 I 24 PNG, SHA-256 prima e dopo

| PNG | SHA-256 **prima** | SHA-256 **dopo** | byte |
|---|---|---|---|
| `day.png` | `67d983517124d8758a02e107bb83d7a110c4173ce462a56142b7a490a2ea4a8f` | `ba5290990322b3f4b6f7c7765a93657a7f8f58971116a495392aaef27c654365` | 41366 |
| `dusk.png` | `089078deefd10bf6aadb794452a13eb17e960d7b99154c7518a0209e25e4fe87` | `18d3de80c87ac878927dd6fe0803b283050277725df5dd9ec5ab9ebada94d48d` | 52443 |
| `lake-boats.png` | `61dd5e800b363d0e8e33e3b556dccddb9fbd14747f76482e7418f6fb396733ff` | `85d66cc6bdc973c3184e195e636c7f992942f9c25d6a3ee63fa8f86d0e7f1e5d` | 49318 |
| `lake-busy.png` | `04ff3df882cccd7b8cb62730f106bd10c3c22384a0b13692655c99b85d1f011d` | `c2d4c336becfcd65c663c012c901388b1fcce4f4ea9c7c5f96c4e666975b4e4d` | 46193 |
| `lake-dolphin-leap.png` | `6b4a2c5048737dbd2fe786f09ba7271f87bdec2056051eae1d334dcea4197086` | `15cc955323b7e95eeaa87d2991c109c28470733f91c99a8b5145abaa8869b457` | 44052 |
| `lake-empty.png` | `b12673162126f84fb1a5d638032183889f8237676f550cec1ec5c30d8a1ebe67` | `1197d24b324680d85b3050b6dde11a8f6bb5482444073398a9fe3564027a6281` | 42322 |
| `night.png` | `ef62ee545a7ddfeef5dbd3bfe7ad3fb0206344d5de2b9f48fcde1272ea7f5356` | `c06009c520a21d277a51c5c382233b75f20455e4ad2278f9f1b4b64822c18d43` | 38400 |
| `overcast.png` | `b2b1c7977d6adef7d670e7687b2f62ef724db2bd7286a7e8727704072a8ec944` | `64aa61b35777a0a4a5193212c4779bb1a25b96b66eb95a7f911c8108770a8c09` | 41550 |
| `people-commercial.png` | `e246be431a3d2104ae9dc3e07ccecee9e8fc01d3d680210d4d3612b54c0dfb22` | `40f7cc58fd5ef7bdc518bbb44aaea623b3ff4501d4a15b24e14d16f031ae6420` | 45561 |
| `people-mixed.png` | `8e6b7a1ed7bdac4bfe9b0c6095de7e5a1ad5176a28b86f1d35082f80bf319884` | `b2097e84f86d5c52a258e4f5d38dc6b2bb39d74f9d77f30d108ebfa3c4a05f77` | 41722 |
| `people-overlap.png` | `5b0d1c83ed0d03c5d98a7b69b2472b0c934b8015f304bf9bc1ec78529ba7c9ed` | `d094ba3500b708eb98673616abeb6f3c028cf57be7ee95060e9a2960e4feb33e` | 41251 |
| `people-single.png` | `99ad55f6a636be565dc11d52ebd51c8c997503c10784dfaa46b1a9b029df2a35` | `08dbd1f13136eef5328c32e6672766cd2f7ffddfc938c0bf4a7f3dd1da9c8f3f` | 40400 |
| `people-skin.png` | `8804b29d6ce8ae11d0cdb6cb9b156f2f32b9d68916b93c872d4e64dea6892972` | `948bfd08b708782ba6a6fbe6a7b4281da1a68c4984903b9f98877ba5c8591ef8` | 46496 |
| `people-skyscraper.png` | `0203768511d7f7b88ada6f71281e63f9d7005fa5629af86f0f2273b5ffafb2da` | `b4003b419bab6d354035835c53d85651c2b7b65859d75cc81d8b4017b3bdd7c5` | 37494 |
| `people-window.png` | `809e20f9238558e93bc63e984a33f5abab91329c6284f350c380f96626e2366a` | `44aeaf705f9478735dbeb4baefeee68ca3669c711f5497aaa0b20dd3be859e37` | 42623 |
| `rain.png` | `d5ee667810aad7c1cd62b2e40532688db59ccc093804638de4cbd1a3ac9e6b11` | `6774d7868beaa22a1ddb566673c0f7ccdc912f025925b00a07d25cdfe40e3f95` | 48133 |
| `shops-closed-night.png` | `88f9194bed4cbf4b65f8785c108c7e7fa19da9a803249ec0cfeccc1460ce32f5` | `eb13fca4bc13340c4405958e367d82f4edb2fbb1fb8bad746dc7903526bd77c5` | 42572 |
| `snow.png` | `5f2c20b8fb6b506e3c060638f3ecdeb411e51bb33856213c2bc9a28bd70f7d38` | `c49c2a45f6ff75484d2d7bca7ecdb796c44d86486c7b6b863bb2b46806fbc3b8` | 43159 |
| `theme-city.png` | `cd80496da7704d9608f9d2859db28053c811bb19e0293096c79d450236ba1502` | `e905c1da438634274347861723366deed9dad1e8bf91e5cc790c3cd9ded6ad57` | 37667 |
| `thunderstorm.png` | `704baa3eeb7916222b8b495f922effa121f97937e4cc78e13703010c70b0e351` | `ad5f9f9ee3c64a22953bfed710ccddf89a92eb31816f31e43e1119df3b762864` | 51608 |
| `traffic-day-sparse.png` | `69838964a4ecf714c63172dc0d558c292d0d4c7ced01fbbd51c52b9783766af4` | `05a58d15fe94b624215e99962f64d4a6c181b2b50d820cfcd4f67032024b0cd4` | 43174 |
| `traffic-day.png` | `e977dff4ff3e49a64553b9b46f1cf696e6c7e66584ee640a6109fe5e91881924` | `ad5f2030835a09995ffa3e17eb9f34016974dd18667c51003b8ae6bc7e6ebb81` | 46110 |
| `traffic-night-quiet.png` | `6ea1a3cfe0973108d5fc3a1504bbeb1c575fb8f6389cefd6b891a631895fe879` | `2c997a9a9e550a76b95cd2c4ef785d060d44a1f8ff19b03d6eea0f272fc1f357` | 39546 |
| `traffic-night.png` | `94ce3b70643fd6ebf76450a61c886132f5c18e91085da9d1a86842992f93aac6` | `774f482468084798fada79d7b654b9604945b2e9c9e664e1b37f0e2630e97cc0` | 45567 |

**24 rigenerati, 0 invariati.** I tre golden GL — `gl-day.png`, `gl-lake-busy.png`,
`gl-thunderstorm.png` — **non sono stati toccati**: 24 + 3 = **27 PNG committati**, come prima.

### 3.3 Le due prove che la rigenerazione non ha rotto nulla

- **`GoldenUniquenessTest`: verde** — «no two committed goldens are byte-identical». Nessuna coppia
  di PNG rigenerati coincide, quindi nessuna scena ha smesso di esercitare la propria impostazione.
  In questo pass è una prova vera, non una formalità.
- **I tre golden GL restano verdi**: `GlDriverGapGuardTest` legge ancora **day 0,00% /
  lake-busy 0,01% / thunderstorm 0,24%** contro il cancello del 3,00%. I due percorsi non si sono
  separati.

---

## 4. I quattro cancelli: rimisurati, non spostati

MISURATO dopo la rigenerazione, con l'aritmetica del progetto:

| cancello | scena / rettangolo | pavimento efficace **prima** | **dopo** | limite | margine |
|---|---|---|---|---|---|
| **densità persone** | `people-single` / PAVEMENT (0,546)-(360,655) | 0,1223% | **0,0000%** | 0,1415% | **100%** |
| conteggio auto giorno | `traffic-day-sparse` / banda strada (0,626)-(360,703) | 0,2273% | **0,0000%** | 3,8041% | **100%** |
| densità auto notte | `traffic-night-quiet` / banda strada | 0,0000% | **0,0000%** | 7,0058% | **100%** |
| orario commerciale | `shops-closed-night` / banda facciate (0,376)-(360,636) | 0,1068% | **0,0000%** | 1,0038% | **100%** |

**Ogni** frame e **ogni** focus asserito misurano ora **zero pixel differenti**: il pavimento di
ogni cancello è tornato a essere il rumore di rendering del dispositivo che esegue il test, che su
questo dispositivo è zero.

**Nessun limite è stato spostato e `SettingsGates` non è stato toccato.** Non c'era niente da
ri-derivare: i segnali erano già stati rimisurati su questo dispositivo nel pass precedente e si
riproducono alla quarta cifra — persone 0,2829% nascosti e 0,7110% ignorati, contro 0,283% e
0,711% sul OnePlus. I test di derivazione li rimisurano a ogni esecuzione e sono verdi:

```
GATEDERIVE: people density: hidden=0.2829% ignored=0.7110% gate=0.1415%
GATEDERIVE: car count ignored:        floor=0.0000% signal=7.6263%  gate=3.8041%
GATEDERIVE: night car density ignored: floor=0.0000% signal=14.0079% gate=7.0057%
GATEDERIVE: business hours ignored:    floor=0.0000% signal=2.0053%  gate=1.0038%
```

Nessun cancello risulta stretto dopo la rigenerazione.

---

## 5. Contabilità, con l'aritmetica scritta

| | prima | dopo | aritmetica |
|---|---|---|---|
| test JVM | 1331 | **1331** | 1331 + 0; `GoldenUniquenessTest` incluso e verde |
| test strumentati | 148 (**1 fallimento**) | **148 (0 fallimenti)** | il fallimento della voce 35 **sparisce**: 1 − 1 = 0. Nessun test aggiunto o rimosso |
| test Python | 108 | **108** | 108 + 0 |
| PNG golden committati | 27 | **27** | 24 Canvas rigenerati + 3 GL intatti = 27; 25 asserzioni Canvas su 24 PNG (`day.png` è asserito due volte, con foci diversi) |
| `@Ignore` | 0 | **0** | |
| voci d'archivio | 883 | **884** | 883 + 1 (questo report) − 0 = **884** |
| `versionCode` / `versionName` | 53 / 4.22 | **53 / 4.22** | invariati |

---

## 6. Lista intera dei file toccati — dal diff dei due archivi

Derivata confrontando l'archivio consegnato con `876faa35…`, **non** da `git status`.

**Aggiunti (1):**

- `V4_22_GOLDEN_BV6600_REPORT.md` — questo report.

**Modificati (27):**

- `app/src/androidTest/kotlin/com/paperscrape/livewallpaper/engine/VehicleOccupantScaleTest.kt` —
  l'estrazione di `glassPane()` (voce 35). È l'unico file di codice toccato, sta in `androidTest`
  e non entra nell'APK.
- **24 PNG** in `app/src/androidTest/assets/golden/` — i golden Canvas rigenerati (§3.2).
- `BACKLOG_v4_22.md` — voci 35 e 36 chiuse con il loro esito; tabella di riepilogo aggiornata.
- `CLAUDE.md` — il blocco del §7 aggiornato: i golden Canvas sono ora autorati su questo
  dispositivo e i cancelli poggiano su uno zero vero.

**Rimossi (0).**

**`app/src/main` non è stato toccato.** Le modifiche locali usate per misurare — il redirect di
`SceneGolden.outputDir()` su `filesDir` e la sonda `ATTRIB` — sono state rimosse e i sorgenti
verificati byte-identici al pristine.

---

## 7. Stato del dispositivo alla consegna

Annotato prima di toccarlo e invariato rispetto alla consegna precedente.

| | originale | alla consegna |
|---|---|---|
| wallpaper attivo | `com.paperscrape.livewallpaper/…PaperWallpaperService` (release del maintainer) | **identico** |
| pacchetti PaperScrape | solo quello del maintainer | **solo quello del maintainer** — `…debug` e `…debug.test` disinstallati (li aveva già rimossi Gradle a fine suite) |
| `system.screen_brightness_mode` | 1 | **1** |
| `system.screen_off_timeout` | 60000 | **60000** |
| `global.stay_on_while_plugged_in` | 0 | **0** |
| `global.adb_enabled` / `development_settings_enabled` | 1 / 1 | **1 / 1** |
| `global.auto_time` | 1 | **1** |

**Su `global.auto_time`, ripetuto perché non resti implicito:** il suo valore *originale*, prima
del primo pass di migrazione, **non era stato annotato**. È stato riportato a **1**, che è il
default di Android, e l'orologio del dispositivo è corretto — ma è un ripristino *al default*, non
*al valore osservato*, ed è l'unica voce di stato di cui questo si debba dire.

La build di questo pass è `com.paperscrape.livewallpaper.debug`, pacchetto separato: la release del
maintainer non è mai stata disinstallata né sovrascritta. Le due trappole già incontrate in questa
sessione sono state evitate: la cattura dei frame è passata per `am instrument` e non per Gradle
con un filtro di classe, e non è stato usato `am force-stop` sul pacchetto del wallpaper.

---

## 8. Condizioni d'arresto

| | esito |
|---|---|
| **J** — il difetto della voce 35 non è confinato ai metodi grandi di quel test | **non scattata** — quattro prove indipendenti, §1.4 |
| **K** — una scena non riconoscibilmente di renderer | **non scattata** — tutte e 24 classificate RENDERER, §2.2 |
| golden GL toccati | **no** — non toccati, e verdi |
| cancello spostato | **no** — `SettingsGates` non toccato |
| `app/src/main` modificato | **no** |
| versione incrementata | **no** |
| varianti dell'esperimento provate finché qualcosa passa | **no** — un solo esperimento, un solo esito |

---

## 9. Artefatto

| | |
|---|---|
| file | `PaperScrape_v4_22.zip` |
| voci | **884** = 883 + 1 aggiunto, 0 rimossi, 27 modificati |
| sostituisce | `876faa353a4ca6bb06c46d6c02fc6a6bce83b4dcaeba21d716a0509ffda27028`, 5 764 294 byte, 883 voci |

**SHA-256 e dimensione dell'archivio sono nella nota di consegna, non qui**, perché questo file sta
dentro l'archivio e non può contenere l'impronta di ciò che lo contiene. Una copia del report è
consegnata accanto allo ZIP, e lì la riga c'è.

`versionCode 53` / `versionName "4.22"` invariati.

**Etichetta della consegna: infrastruttura di test su v4.22 pubblicata, non una release.**
