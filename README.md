# Outflow – Arrow Game

> Decisioni, regole, economia, stato e prossimi passi: vedi **[PROGETTO.md](PROGETTO.md)**.
>
> **Stato al 13/11/2026:** versione 0.6.0-alpha (250 livelli, target API 36). Account Google Play Console attivo.
> Fase 0.6 in corso (frecce più lunghe, voto S-F, tempo per livello, combo con fuochi d'artificio,
> livelli-immagine, eventi con classifiche, Play Giochi): vedi PROGETTO.md, sezioni 8–10.

Puzzle casual per Android: libera tutte le frecce dalla figura. Ogni freccia esce solo
nella direzione in cui punta e solo se la strada è libera. 3 tentativi per livello.

## Dove trovare l'APK e il bundle per Google Play

L'ultima build è nel ramo [`build-output`](../../tree/build-output):
- `Outflow.apk` + `build.log`: versione di prova, firmata con la chiave alpha. Tutte le alpha
  si installano una sopra l'altra senza perdere i progressi.
- `Outflow.aab` + `bundle.log`: bundle per Google Play, firmato con la chiave di upload
  (prodotto solo se i secret `UPLOAD_*` sono configurati nel repository).

## Versione alpha 0.6.0

- **250 livelli nuovi**: frecce molto più lunghe (fino a 30 caselle), curve, U e spirali;
  griglie che crescono fino a 26×36 al livello 40 e 28×40 alla fine
- Nuovo generatore "a buccia di cipolla" (`tools/generate_levels.py`): griglia piena al 100%,
  ogni livello risolvibile per costruzione e verificato dal risolutore
- **Boss a sagoma**: ogni boss disegna un'immagine (goccia, mela, abete, fungo, fiamma, razzo, gatto…),
  25 sagome in `tools/silhouettes.py`
- Stile **Linea** (tinta unica, sottile) predefinito; **Facile da vedere** (una freccia un colore) gratuito nel negozio
- La freccia toccata quando è bloccata **resta rossa** finché non viene liberata
- Fascia di difficoltà sotto il titolo (Facile, Medio, Difficile, Esperto)
- Nuovo salvataggio `outflow_v5`: progressi e monete ripartono da zero (restano lingua, nome e impostazioni)

## Versione alpha 0.5.1

- Target API 36 (Android 16), Android Gradle Plugin 8.11.1, Gradle 8.13
- Tasto indietro compatibile con Android 16 (OnBackInvokedCallback, gesto "indietro predittivo")
- Bundle `.aab` firmato con la chiave di upload per Google Play
- Livelli e progressi invariati

## Livelli

I livelli sono generati da `tools/generate_levels.py` (generatore + risolutore:
ogni livello è verificato come risolvibile) e salvati in `app/src/main/assets/levels.json`.

```
python3 tools/generate_levels.py
```

## Versione alpha 0.5.0

- Negozio a sezioni: Potenziamenti, Temi, Frecce, Scie
- Sfida del giorno: livello 11×15 generato dalla data (uguale su tutti i telefoni, `Daily.kt`),
  cronometro, record del giorno, 50 monete alla prima vittoria del giorno
- 14 trofei con premi in monete (schermata dal Profilo)
- Condividi risultato: immagine con livello/sfida, stelle, tempo e nome (menu Condividi di Android)
- Regole di backup: i progressi (`outflow_v4.xml`) vanno nel backup Google e nel passaggio a un nuovo telefono
- Progressi conservati dalla 0.4.0 (livelli invariati)

## Versione alpha 0.4.0

- Griglie piene al 100%: generatore "riempi e ripara" (copre tutta la griglia, poi gira o spezza
  le frecce che creano blocchi senza uscita); da 19 frecce (liv. 1) a 129 (liv. 100), griglie fino a 17×27
- Boss sempre a griglia piena; tempo ≈ 0,9 s per freccia, minimo 45 s (fino a 1:45)
- Pulsante "Vista intera" quando la griglia è ingrandita
- Negozio: 6 stili freccia (Classica, Marker, Gesso, Neon, Pixel, Binario) e 6 scie
  (Nessuna, Scintille, Coriandoli, Stelline, Fumo, Arcobaleno) con anteprima toccabile
- Nome giocatore al primo avvio (modificabile), saluto in home e alla vittoria
- Profilo: stelle, livelli e boss completati, frecce liberate, monete, giorni di regalo, migliori tempi sui boss

## Versione alpha 0.3.0

- Livelli più grandi e difficili: griglie rettangolari da 6×7 a 15×19, frecce da 2 a 9 caselle,
  poche frecce libere in partenza, niente frecce corte sul bordo verso l'esterno
- Zoom con due dita e spostamento della griglia; frecce più sottili
- Boss a tempo: 45 s al livello 10, +5 s per boss fino a 90 s al livello 100; restano i 3 tentativi
- Tempo scaduto: +15 s (30, 60, 120… monete) oppure dalla scorta
- Negozio: temi (Classico, Scuro, Pastello, Oceano, Tramonto, Neon; Oro al liv. 50, Maestro al liv. 100),
  aiuti, tentativi e secondi di scorta, Rivela
- Regalo giornaliero (20 → 100 monete con giorni consecutivi)
- Tester: pulsante "+500 monete" nelle statistiche

## Versione alpha 0.2.0

- 100 livelli con frecce a serpente da 1 a 9 caselle, dritte, a L e a zig-zag; boss ogni 10 livelli
- 3 tentativi per livello; stelle: 0 errori = 3★, 1 = 2★, 2 o tentativi comprati = 1★
- Monete 20/10/5 (boss ×1,5: 30/15/8); rigiocando si guadagna solo il miglioramento
- Tentativo extra a livello perso: 30, 60, 120… monete nello stesso livello
- Aiuto (25 monete), freccia che blocca evidenziata quando sbagli
- Uscita a serpente, frecce colorate, home con frecce animate, suoni
- Modalità Tester (solo alpha): tutti i livelli sbloccati + statistiche per livello
- Suoni generati da `tools/make_sounds.py`

Per la versione Store: in `app/build.gradle.kts` mettere `ALPHA` a `false` (toglie la modalità Tester).
