# Outflow – Arrow Puzzle · Registro di progetto

Documento di riferimento per chi riprende il lavoro (persona o nuova chat con Claude).
Contiene **tutte le decisioni prese**, lo stato attuale e le cose da fare.
Va aggiornato a ogni nuova versione.

- **App:** Outflow – Arrow Puzzle (nome di lavoro, sembra libero; marchio da verificare prima della pubblicazione)
- **ID app:** `com.airis.outflow` (definitivo, non si può più cambiare dopo la pubblicazione)
- **Repository:** `lelux95/outflow-android` (privato)
- **Versione attuale:** 0.5.0-alpha (versionCode 5), 10 novembre 2026
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
  **Per lo Store si userà la firma di Google Play (Play App Signing)**: la chiave alpha non va usata in produzione.
- **Versioni:** a ogni rilascio aumentare `versionCode` e `versionName` in `app/build.gradle.kts`,
  aggiornare README e questo file.
- **Modalità Tester:** visibile solo se `ALPHA = true` in `app/build.gradle.kts`. Per lo Store va messo `false`.

### Strumenti (cartella `tools/`)
- `generate_levels.py` → genera i 100 livelli in `app/src/main/assets/levels.json`.
  Ogni livello è verificato dal risolutore (tutti risolvibili, nessuna sovrapposizione).
  Il seme è fisso: rilanciarlo produce gli stessi livelli.
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
- `SharedPreferences` nel file `outflow_v4`. Al cambio di file (nuovi livelli) si azzerano stelle e monete
  ma si conservano lingua, nome, suoni, vibrazione e Tester.
- Backup Android attivo solo su quel file (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`).
- **Se i livelli cambiano di nuovo**, passare a `outflow_v5` e aggiornare le regole di backup.

---

## 2. Regole di gioco

- Ogni freccia occupa da 1 a più caselle, dritta, a L o a zig-zag; la punta indica dove esce.
- Tocchi un punto qualsiasi della freccia: esce se la strada davanti alla punta è libera fino al bordo,
  e scivola via seguendo il proprio percorso ("uscita a serpente").
- Se è bloccata: la freccia trema in rosso, **la freccia che la blocca lampeggia**, si perde 1 tentativo.
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
- **100 livelli**, sbloccati in sequenza (tutti aperti in modalità Tester).
- Griglie piene al 100% (metodo "riempi e ripara"), rettangolari, più alte che larghe.
- Circa 2 livelli su 3 sono rettangoli pieni; gli altri hanno forme (rombo, cerchio, croce, clessidra,
  triangolo, stella, freccia) con griglia un po' più grande.
- Tutorial di una riga nei livelli 1 e 2.

| Livello | Griglia | Frecce |
| --- | --- | --- |
| 1 | 6×8 | 20 |
| 10 (boss) | 10×14 | 39 |
| 30 (boss) | 13×18 | 67 |
| 60 (boss) | 15×22 | 93 |
| 100 (boss) | 17×27 | 116 |

### Boss (livelli 10, 20, … 100)
- Bordo viola e scritta BOSS nella griglia dei livelli; schermata di presentazione prima di iniziare.
- Sempre griglia piena, la più difficile del suo blocco di 10.
- **A tempo**: circa 0,9 s per freccia, minimo 45 s (da 45 s al liv. 10 a 1:45 al liv. 100).
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
| Frecce (stili) | Classica inclusa · Marker 200 · Gesso 300 · Neon 350 · Pixel 400 · Binario 450 |
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
  Stato al 10/11/2026: **in attesa della verifica dei documenti**.
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

1. **Play Giochi** (appena l'account è verificato): creare l'app `com.airis.outflow` in Play Console,
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

## Storico versioni

| Versione | Data | Cosa |
| --- | --- | --- |
| 0.1.0 | 10/11/2026 | Prototipo: 30 livelli con frecce da 1 casella, 10 tentativi, stelle, monete |
| 0.2.0 | 10/11/2026 | Frecce lunghe e curve, 100 livelli, boss, 3 tentativi, colori, suoni, Tester |
| 0.3.0 | 10/11/2026 | Griglie più grandi, zoom, boss a tempo, negozio, temi, regalo giornaliero |
| 0.4.0 | 10/11/2026 | Griglie piene, stili e scie, nome giocatore, profilo, vista intera |
| 0.5.0 | 10/11/2026 | Negozio a sezioni, sfida del giorno, trofei, condivisione, backup |
