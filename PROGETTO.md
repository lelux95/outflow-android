# Outflow – Arrow Game · Registro di progetto

Documento di riferimento per chi riprende il lavoro (persona o nuova chat con Claude).
Contiene **tutte le decisioni prese**, lo stato attuale e le cose da fare.
Va aggiornato a ogni nuova versione.

- **App:** Outflow – Arrow Game (nome scelto il 13/11/2026, uguale in Play Console e nel gioco; marchio "Outflow" da verificare prima della pubblicazione)
- **ID app:** `com.airis.outflow` (definitivo, non si può più cambiare dopo la pubblicazione)
- **Repository:** `lelux95/outflow-android` (privato)
- **Versione attuale:** 0.6.0-alpha (versionCode 7), 13 novembre 2026
- **Ultimo aggiornamento di questo file:** 13 novembre 2026 (fase 0.6 avviata: punto 1 dell'ordine di lavoro)
- **Tester principale:** il padre di Daniele (giocatore abituale di questo genere di giochi)
- **Documento di progetto iniziale:** artifact "Outflow – Documento di progetto" su claude.ai

---

## 1. Come si lavora

- **Codice:** Kotlin nativo, nessuna libreria esterna, interfaccia costruita in codice (niente XML di layout).
  Scelto al posto di Godot perché l'APK pesa meno di 1 MB e usa la stessa pipeline di altri progetti.
- **Compilazione:** su GitHub Actions (`.github/workflows/build.yml`). Ogni push su `main` compila l'APK
  e lo pubblica nel ramo `build-output` insieme a `build.log` e `info.txt` (con l'esito).
- **Firma:** chiave `app/outflow.keystore` (password `outflow-alpha`), solo per le versioni di prova.
  Tutte le alpha si installano una sopra l'altra senza perdere dati.
  **Per lo Store si usa la firma di Google Play (Play App Signing)**: la chiave alpha non va usata in produzione.
- **Chiave di upload (Store):** creata il 13/11/2026, **fuori dal repository** nella cartella
  `chiave-upload-NON-CONDIVIDERE` sul PC di Daniele (va tenuta anche una copia di backup). Arriva al
  workflow tramite i secret GitHub `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`,
  `UPLOAD_KEY_PASSWORD`. Con i secret presenti ogni push produce anche `Outflow.aab` (firmato con
  questa chiave) nel ramo `build-output`. SHA-1 upload: `2B:65:1B:41:2E:D4:BF:63:23:CE:8E:02:94:0B:97:4C:DD:7B:8D:DB`.
- **Chiave di firma di Google (Play App Signing):** attiva dal 13/11/2026. SHA-1 firma app:
  `B5:35:9B:8F:51:14:29:2E:A5:1B:2C:0F:C9:DF:30:C4:93:FB:DD:8C` (in Play Console: Protetto con Play →
  Protezione del Play Store → Firma dell'app). Per Play Giochi servono **entrambe** le SHA-1 (upload e firma app).
- **Attenzione:** l'app installata da Google Play ha una firma diversa da quella degli APK alpha: per
  passare dall'una all'altra bisogna disinstallare (si perdono i progressi locali).
- **Versioni:** a ogni rilascio aumentare `versionCode` e `versionName` in `app/build.gradle.kts`,
  aggiornare README e questo file.
- **Modalità Tester:** visibile solo se `ALPHA = true` in `app/build.gradle.kts`. Per lo Store va messo `false`.

### Strumenti (cartella `tools/`)
- `generate_levels.py` → genera i **250 livelli** in `app/src/main/assets/levels.json` (metodo "a buccia di
  cipolla": le frecce nascono nell'ordine in cui si tolgono, griglia piena e risolvibile per costruzione).
  Ogni livello è verificato dal risolutore (tutti risolvibili, nessuna sovrapposizione).
  Il seme è fisso: rilanciarlo produce gli stessi livelli.
- `silhouettes.py` → le 25 sagome dei boss (forme geometriche semplici, disegnate da noi).
- `preview.py` → anteprime PNG di sagome e livelli, solo per controllo.
- Python 3.12 installato sul PC di Daniele (13/11/2026): `python tools/generate_levels.py`.
- `make_sounds.py` → genera i 5 suoni WAV in `app/src/main/res/raw/` (sintetizzati, senza diritti di terzi).

### File principali
| File | Contenuto |
| --- | --- |
| `MainActivity.kt` | Tutte le schermate, timer dei boss, negozio, profilo, trofei |
| `GameView.kt` | Campo di gioco: frecce, tocchi, zoom, animazioni, stili, scie |
| `Data.kt` | Livelli, regole/economia (`Rules`), oggetti (`Item`), salvataggio (`Store`) |
| `Daily.kt` | Generatore della sfida del giorno (gira sul telefono, seme = data) |
| `Theme.kt` | Temi e colori attivi (`Palette`) |
| `Cosmetics.kt` | Stili freccia e scie |
| `Trophies.kt` | Trofei |
| `Share.kt` | Immagine da condividere + provider per il menu Condividi |
| `Strings.kt` | Tutti i testi, inglese e italiano |
| `BgArrowsView.kt` | Frecce animate sullo sfondo della home |
| `Sounds.kt` | Effetti sonori |

### Salvataggio
- `SharedPreferences` nel file `outflow_v5` (dalla 0.6.0). Al cambio di file (nuovi livelli) si azzerano stelle e monete
  ma si conservano lingua, nome, suoni, vibrazione e Tester.
- Backup Android attivo solo su quel file (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`).
- **Se i livelli cambiano di nuovo**, passare a `outflow_v6` e aggiornare le regole di backup.

---

## 2. Regole di gioco

- Ogni freccia occupa da 1 a più caselle, dritta, a L o a zig-zag; la punta indica dove esce.
- Tocchi un punto qualsiasi della freccia: esce se la strada davanti alla punta è libera fino al bordo,
  e scivola via seguendo il proprio percorso ("uscita a serpente").
- Se è bloccata: la freccia trema, **la freccia che la blocca lampeggia**, si perde 1 tentativo e
  **la freccia sbagliata resta rossa** finché non viene liberata (dalla 0.6.0).
- **3 tentativi per livello** (cuori). A zero: tentativo extra (vedi economia) o si ricomincia.
- Nessun quadrato sotto le frecce (deciso: rendeva il gioco troppo facile).
- Zoom con due dita, trascinamento con un dito; pulsante "Vista intera" quando ingrandito.

### Stelle
| Errori | Stelle |
| --- | --- |
| 0 | 3 |
| 1 | 2 |
| 2 (oppure tentativi comprati) | 1 |
| 3 | livello perso |

### Livelli
- **250 livelli** (dalla 0.6.0), sbloccati in sequenza (tutti aperti in modalità Tester).
- Griglie piene al 100%, più alte che larghe: da 6×8 (liv. 1–2) a **26×36 al livello 40**, poi fino a 28×40.
- Frecce di lunghezza molto varia (da 2 fino a 30 caselle), curve, U e **spirali**.
- Sotto il titolo la fascia di difficoltà: Facile / Medio / Difficile / Esperto (dal valore `difficulty`).
- Circa 2 livelli su 3 sono rettangoli pieni; gli altri hanno forme (rombo, cerchio, croce, clessidra,
  triangolo, stella, freccia) con griglia un po' più grande.
- Tutorial di una riga nei livelli 1 e 2.

| Livello | Griglia | Frecce | Libere all'inizio |
| --- | --- | --- | --- |
| 1 | 6×8 | 14 | 43% |
| 10 (boss Goccia) | 14×19 | 39 | 21% |
| 41 | 26×36 | 176 | 16% |
| 100 (boss Corona) | 29×40 | circa 110 | 10% |
| 250 (boss Barca a vela) | 30×43 | 79 | 8% |

### Boss (livelli 10, 20, … 250)
- Bordo viola e scritta BOSS nella griglia dei livelli; schermata di presentazione prima di iniziare.
- **Sagoma** (dalla 0.6.0): ogni boss disegna un'immagine (goccia, mela, abete, fungo, fiamma, razzo, gatto…),
  con il nome mostrato nella presentazione e sotto il titolo.
- **A tempo**: circa 1 s per freccia, minimo 45 s, a passi di 5 s.
  Ultimi 10 secondi in rosso. Restano anche i 3 tentativi ("è pur sempre un boss").
- Monete ×1,5.

### Sfida del giorno
- Livello 11×15 generato dalla data: **uguale su tutti i telefoni**.
- Cronometro in avanti, record del giorno salvato, **50 monete** alla prima vittoria del giorno.
- Base della futura classifica giornaliera.

---

## 3. Economia (monete del gioco)

| Voce | Valore |
| --- | --- |
| Livello normale 3★ / 2★ / 1★ | 20 / 10 / 5 monete |
| Boss 3★ / 2★ / 1★ (×1,5) | 30 / 15 / 8 monete |
| Rigiocare un livello | solo la differenza se migliori le stelle |
| Tentativo extra (o +15 s sui boss) | 30, poi 60, poi 120… nello stesso livello |
| Aiuto in partita (evidenzia una freccia libera) | 25 monete (o dalla scorta) |
| Rivela (mostra tutte le frecce libere per 2 s) | 50 monete (o dalla scorta) |
| Sfida del giorno | 50 monete alla prima vittoria del giorno |
| Regalo giornaliero | 20, 30, 40, 50, 60, 80, 100 (giorni di fila; dal 7° resta 100) |
| Trofei | da 20 a 500 monete ciascuno |

**Decisioni:** ricompensa ×2 riservata alla futura pubblicità (video). Il potenziamento "tentativi" è
**consumabile**, non permanente, per non svuotare l'economia.

### Negozio (4 sezioni, Potenziamenti in cima)
| Sezione | Oggetti |
| --- | --- |
| Potenziamenti | 3 aiuti 60 · tentativo di scorta 70 · +15 s di scorta 80 · 2 Rivela 100 |
| Temi | Classico e Scuro inclusi · Pastello 150 · Oceano 250 · Tramonto 400 · Neon 600 · Oro (liv. 50) · Maestro (liv. 100) |
| Frecce (stili) | **Linea** (predefinita, tinta unica) e **Facile da vedere** (a colori) incluse · Marker 200 · Gesso 300 · Neon 350 · Pixel 400 · Binario 450 |
| Scie | Nessuna inclusa · Scintille 150 · Coriandoli 200 · Stelline 250 · Fumo 300 · Arcobaleno 350 |

Stili e scie hanno un'anteprima toccabile nel negozio. Tema, stile e scia si combinano liberamente.

### Trofei (14)
Primo livello, 10 / 50 / 100 livelli, primo boss, 5 boss, boss in metà tempo, 3★ su 10 livelli,
1.000 e 10.000 frecce liberate, regalo 7 giorni di fila, 1 e 7 sfide del giorno, primo acquisto.

---

## 4. Altre funzioni presenti

- Lingua al primo avvio (inglese principale, italiano), modificabile nelle impostazioni.
- **Nome giocatore** al primo avvio (si può saltare, max 16 caratteri): saluto in home e alla vittoria.
- **Profilo:** stelle, livelli e boss completati, frecce liberate, monete, giorni di fila, stile in uso,
  migliori tempi sui boss, accesso ai trofei.
- **Condividi:** immagine con logo, livello o sfida, stelle, tempo e nome (menu Condividi di Android).
- Home con frecce animate sullo sfondo; pulsanti: regalo, sfida del giorno, continua, livelli, negozio, profilo.
- Suoni (tocco, uscita, errore, vittoria, sconfitta) e vibrazione, disattivabili.
- **Tester:** tutti i livelli sbloccati, statistiche per livello (partite, vittorie, tempo, errori,
  tentativi comprati), pulsante "+500 monete".

---

## 5. Pubblicazione e aspetti amministrativi

- **Account Play Console:** creato su un account Google nuovo, **intestato alla moglie di Daniele**
  (futura P.IVA forfettaria). Daniele entrerà come amministratore tramite "Utenti e autorizzazioni".
  Stato al 13/11/2026: **account attivo** (documenti verificati). Checklist di pubblicazione → sezione 9.
- **Tipo di account:** personale per ora; organizzazione quando ci sarà la P.IVA (richiede D-U-N-S).
- **Commerciante (DSA UE):** dichiarare **"non commerciante"** finché l'app è gratuita e senza pubblicità;
  passare a "commerciante" quando si attivano AdMob o acquisti (recapiti dell'attività, non di casa).
- **Test chiuso obbligatorio** per i nuovi account personali: almeno **12 tester per 14 giorni
  consecutivi** prima della pubblicazione. Da organizzare con familiari e amici.
- **Fisco:** prima di attivare la pubblicità, verifica con il commercialista (ricavi pubblicitari e
  regime forfettario).
- **Marchio:** prima della pubblicazione verificare "Outflow" su TMview (classi di Nizza 9 e 41) e sul Play Store.

---

## 6. Da fare (in ordine)

> Aggiornato il 13/11/2026: l'ordine di lavoro attuale è nella sezione 8 (fase 0.6) e nella
> checklist di pubblicazione (sezione 9). L'elenco qui sotto resta valido come contenuto.

1. **Play Giochi** (l'account è attivo): creare l'app `com.airis.outflow` in Play Console,
   attivare i servizi Play Giochi, poi integrare:
   - classifiche: stelle totali, tempo sui boss, **sfida del giorno** (la più importante);
   - trofei Google (riprendere i 14 esistenti);
   - salvataggio cloud dei progressi (risolve il cambio telefono).
   Nota: la classifica mostra il nome del profilo Play Giochi, non il nome giocatore del gioco.
2. **Materiali per lo Store:** privacy policy (obbligatoria), descrizione in italiano e inglese,
   icona 512 px, immagine in evidenza, screenshot, testo di invito per i tester.
3. **Test chiuso** con 12 tester per 14 giorni; tarare la difficoltà con le statistiche Tester.
4. Versione Store: `ALPHA = false`, firma di Google Play, nuova chiave di caricamento.
5. **Pubblicità (fase successiva):** AdMob, solo video a premio (niente banner): ×2 monete a fine livello,
   tentativo/tempo extra, aiuto gratuito. Acquisto "Rimuovi pubblicità" a 1–2 €.
   Prima: P.IVA, stato "commerciante", consenso GDPR (UMP di AdMob), privacy policy aggiornata.
6. Idee approvate o proposte, non ancora fatte: pacchetti di livelli scaricabili senza aggiornare l'app;
   altri livelli oltre il 100.

## 7. Punti aperti / da verificare con i test

- Comodità del tocco sulle griglie grandi senza zoom (eventualmente ridurre la larghezza massima).
- Curva di difficoltà e tempi dei boss (con i dati della modalità Tester del padre).
- Equilibrio monete/prezzi (con 3 tentativi si guadagnano meno stelle).
- Livelli con forme: hanno meno frecce dei rettangoli; eventualmente renderli più rari o più grandi.

---

## 8. Brief del 13/11/2026 – fase 0.6 (DA FARE)

Richieste di Daniele dopo lo studio dei competitor. Le proposte tecniche sono di Claude e
**vanno confermate** prima dello sviluppo dove indicato.

### 8.1 Tempo di risoluzione per ogni livello
- Ogni livello registra il tempo impiegato (già misurato internamente, va salvato e mostrato).
- Salvare **miglior tempo** e **ultimo tempo** per livello; mostrarli a fine livello e nella griglia livelli.
- Il tempo serve al punteggio (8.2) e alle classifiche (8.4).

### 8.2 Valutazione a lettere S-A-B-C-D-E-F
Ogni livello concluso riceve un voto in base a **tempo, aiuti usati, errori**.
Proposta di calcolo (da confermare e tarare con i test):
- Si parte da 100 punti.
- **Tempo:** tempo di riferimento = circa 1,2 s per freccia (minimo 20 s). Entro il riferimento nessuna
  penalità; oltre, −1 punto ogni 3% in più, massimo −40.
- **Errori:** −15 per errore.
- **Aiuti:** −10 per Aiuto, −15 per Rivela, −20 per tentativo o tempo extra comprato.

| Punti | Voto |
| --- | --- |
| 95–100 | S |
| 85–94 | A |
| 70–84 | B |
| 55–69 | C |
| 40–54 | D |
| 25–39 | E |
| 0–24 | F |

Da decidere: se il voto **sostituisce** le stelle o le **affianca** (proposta: affianca; le stelle
restano per sbloccare livelli e monete, il voto è il punteggio "competitivo"). Mostrare il miglior voto
nella griglia livelli. Possibile premio monete extra per S.

### 8.3 Frecce più lunghe (più difficoltà)
- Dai competitor: le frecce sono **molto più lunghe** e attraversano gran parte della figura.
- Proposta: lunghezza tipica 6–15 caselle (oggi 2–9), molte curve, frecce che si avvolgono
  attorno ad altre. Meno frecce ma ognuna blocca molte traiettorie.
- Il generatore "riempi e ripara" va adattato: cammini più lunghi e la riparazione deve preferire
  "girare" a "spezzare" (spezzare accorcia le frecce).
- I 100 livelli andranno rigenerati → **nuovo file di salvataggio `outflow_v5`** (progressi azzerati,
  impostazioni conservate) e aggiornamento delle regole di backup.

### 8.4 Eventi con classifiche dedicate (da studiare insieme)
- Eventi a tempo (es. un weekend o una settimana) con un pacchetto di livelli a tema (vedi 8.7).
- Ogni evento ha la **sua classifica** in Play Giochi (somma dei punteggi o tempo totale) e premi
  (monete, temi o stili esclusivi dell'evento).
- Tecnica: i livelli degli eventi dovrebbero arrivare **senza aggiornare l'app** (file scaricato da un
  server o da Firebase Remote Config/hosting). Da decidere dove ospitarli.
- Le classifiche Play Giochi si possono creare in anticipo e mostrare solo durante l'evento.

### 8.5 Effetti durante la partita (combo)
- **Combo:** frecce liberate correttamente una dopo l'altra entro un tempo breve (proposta: entro 1,5 s
  l'una dall'altra) aumentano un contatore "×2, ×3…".
- Soglie proposte: 5 di fila → scritta e piccoli coriandoli; 10 → fuochi d'artificio; 20 → effetto grande
  sullo schermo. Un errore azzera la combo.
- Eventuale bonus al punteggio (8.2) o monete per le combo lunghe: da decidere.

### 8.6 Freccia sbagliata segnata in modo permanente
- Oggi la freccia bloccata trema in rosso solo per un attimo.
- Nuovo comportamento: dopo un errore la freccia **resta colorata in modo diverso** (es. rossa o
  tratteggiata) finché non viene liberata, così l'errore resta visibile.

### 8.7 Livelli-immagine (priorità)
- Nei competitor il labirinto di frecce **disegna un'immagine** (es. un razzo, un segno zodiacale).
- Daniele caricherà gli screen dei competitor in una **cartella dedicata** (in arrivo): servono come
  riferimento, non vanno copiati.
- Proposta tecnica: si parte da un'immagine semplice a pixel (es. 20×30) disegnata da noi, a colori;
  ogni colore è una zona; il generatore riempie ogni zona con frecce lunghe **del colore della zona**,
  così a griglia piena si vede il disegno. La risolvibilità resta verificata dal risolutore.
- Base per gli **eventi a tema**: spazio, segni zodiacali, animali, feste (Natale, Halloween…).
- Da decidere: chi disegna le immagini (Claude può crearle a pixel su richiesta) e quante per tema.

### 8.8 Google Play Console attivo → Play Giochi e pubblicazione
- Integrare **Play Games Services v2**: accesso automatico all'avvio (senza pulsante), obiettivi,
  classifiche (stelle totali, sfida del giorno, eventi), salvataggio cloud dei progressi.
- Serve: progetto Play Giochi in Play Console, client OAuth Android in Google Cloud con nome pacchetto
  e **impronta SHA-1** della chiave di firma (sia quella di upload sia quella di Play App Signing),
  account di prova abilitati.
- I 14 trofei esistenti diventano obiettivi Play Giochi (le icone vanno preparate).

---

## 9. Checklist di pubblicazione su Google Play

| Voce | Stato | Note |
| --- | --- | --- |
| Account sviluppatore | ✅ attivo | Intestato alla moglie di Daniele, personale |
| App in Play Console | ✅ creata | `com.airis.outflow`, nome "Outflow - Arrow Game" |
| Test interno | ✅ 0.5.1 (vc 6) caricata | Play App Signing attivo; avviso "file di deoffuscamento" ignorato (R8 non usato) |
| Dichiarazione commerciante (DSA) | da fare | "Non commerciante" finché senza pubblicità |
| Target API 36 (Android 16) | fatto in 0.5.1, da verificare sul telefono | AGP 8.11.1, Gradle 8.13, tasto indietro con OnBackInvokedCallback |
| App bundle (.aab) firmato | ✅ | Chiave di upload creata; il workflow produce `Outflow.aab` quando ci sono i secret |
| `ALPHA = false` | da fare | Toglie la modalità Tester |
| Privacy policy (URL pubblico) | in corso | Testo pronto (IT/EN), da pubblicare su GitHub Pages |
| Sicurezza dei dati (Data safety) | da fare | Oggi nessun dato raccolto; con Play Giochi e AdMob va aggiornata |
| Classificazione contenuti (IARC) | da fare | Questionario in Play Console |
| Pubblico di destinazione | da fare | Proposta: 13+ o "tutti" (evitare la sezione bambini) |
| Annunci: "contiene pubblicità?" | da fare | No, per ora |
| Scheda dello Store | da fare | Nome, descrizione breve e lunga IT/EN, icona 512 px, immagine 1024×500, almeno 2 screenshot |
| Test chiuso | da fare | 12 tester per 14 giorni consecutivi (account personale) |
| Play Giochi | da fare | Vedi 8.8 |
| Marchio "Outflow" | da verificare | TMview, classi 9 e 41 |

### 8.9 Decisioni del 13/11/2026 (briefing)
- **Voto S-F:** si **aggiunge** alle stelle (le stelle restano per sblocchi e monete). **Voto S = +5 monete.**
- **Combo:** la finestra di 1,5 s si misura **da un tocco riuscito al successivo** (non dalla fine
  dell'animazione, che con le frecce lunghe dura di più).
- **Chiave di upload:** la crea Claude; va conservata **fuori dal repository** e caricata nei secret di GitHub.
- **Privacy policy:** su **GitHub Pages**, in un repository pubblico separato (il repo dell'app è privato).
- **Sfida del giorno:** si allinea al **reset di Play Giochi a mezzanotte del Pacifico** (ora di
  Los Angeles, le 9:00 in Italia; le 8:00 o le 10:00 per pochi giorni l'anno quando l'ora legale non coincide).
  La data della sfida e il seme si calcolano con quel fuso.
- **Generatore unico:** il nuovo generatore delle frecce lunghe (8.3) nasce già con il supporto alle
  **zone colorate** dei livelli-immagine (8.7), così si riscrive e si cambia salvataggio una volta sola.
- I punteggi delle build con modalità Tester attiva **non vanno inviati** alle classifiche.

### 8.10 Analisi degli screen dei competitor e decisioni (13/11/2026)
Screen in `screen concorrenza` (livelli 8, 10, 13 "Difficile" di un altro gioco: solo riferimento, non si copia).
- Griglie circa **26×36** già dal livello 8; frecce di lunghezza molto varia (tante da 2–4, alcune da 15–30
  che attraversano la figura), **spirali** e U; **un solo colore**, linee sottili.
- Il "livello-immagine" è una **sagoma** monocolore (es. una fiamma), non zone colorate.
- Frecce gialle evidenziate nel loro livello 13: funzione del loro gioco, **per ora ignorata**.

Decisioni:
- **250 livelli.** Griglie che crescono fino a **26×36 al livello 40**, poi lentamente fino a 28×40.
- **Boss = sagome** disegnate da noi (una diversa per ogni boss).
- **Stile monocolore sottile ("Linea") predefinito**; lo stile a colori attuale diventa
  **"Facile da vedere"**, gratuito nel negozio.
- Il generatore costruisce i livelli nell'ordine di soluzione (dall'esterno verso l'interno): griglia
  piena al 100%, risolvibile per costruzione, frecce lunghe senza doverle spezzare.

## 10. Ordine di lavoro (fase 0.6, rivisto e approvato il 13/11/2026)

1. Target API 36, AGP aggiornato, tasto indietro compatibile con Android 16, chiave di upload,
   prima build `.aab` in **test interno** (sblocca le impronte SHA-1 per Play Giochi).
2. Privacy policy (GitHub Pages) e dichiarazioni in Play Console → **avvio del test chiuso**
   (i 14 giorni scorrono mentre si sviluppano i punti seguenti).
3. Nuovo generatore (frecce lunghe + zone), freccia sbagliata permanente, `outflow_v5` (8.3, 8.6).
4. Tempo, voto S-F e combo (8.1, 8.2, 8.5) → versione 0.6 al padre per tarare il voto.
5. Play Giochi: accesso, salvataggio cloud, obiettivi, classifiche (8.8).
6. Livelli-immagine (8.7) con gli screen di riferimento (cartella `screen concorrenza`).
7. Eventi (8.4).

---

## Storico versioni

| Versione | Data | Cosa |
| --- | --- | --- |
| 0.1.0 | 10/11/2026 | Prototipo: 30 livelli con frecce da 1 casella, 10 tentativi, stelle, monete |
| 0.2.0 | 10/11/2026 | Frecce lunghe e curve, 100 livelli, boss, 3 tentativi, colori, suoni, Tester |
| 0.3.0 | 10/11/2026 | Griglie più grandi, zoom, boss a tempo, negozio, temi, regalo giornaliero |
| 0.4.0 | 10/11/2026 | Griglie piene, stili e scie, nome giocatore, profilo, vista intera |
| 0.5.0 | 10/11/2026 | Negozio a sezioni, sfida del giorno, trofei, condivisione, backup |
| 0.5.1 | 13/11/2026 | Target API 36, AGP 8.11.1, tasto indietro per Android 16, bundle .aab con chiave di upload |
| 0.6.0 | 13/11/2026 | 250 livelli nuovi (frecce lunghe, spirali, griglie fino a 28×40), boss a sagoma, stile Linea monocolore + Facile da vedere, freccia sbagliata segnata, fascia di difficoltà, salvataggio `outflow_v5` |
