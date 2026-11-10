# Outflow – Arrow Puzzle

Puzzle casual per Android: libera tutte le frecce dalla figura. Ogni freccia esce solo
nella direzione in cui punta e solo se la strada è libera. 10 tentativi per livello.

## Dove trovare l'APK

L'ultima build di prova è nel ramo [`build-output`](../../tree/build-output)
(`Outflow.apk` + `build.log`). Tutte le build sono firmate con la stessa chiave:
si installano una sopra l'altra senza perdere i progressi.

## Livelli

I livelli sono generati da `tools/generate_levels.py` (generatore + risolutore:
ogni livello è verificato come risolvibile) e salvati in `app/src/main/assets/levels.json`.

```
python3 tools/generate_levels.py
```

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
