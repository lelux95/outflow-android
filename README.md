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

## Versione alpha 0.1.0

- 30 livelli in 7 forme (quadrato, rombo, croce, cerchio, clessidra, cuore, triangolo, stella, anello)
- 10 tentativi per livello, stelle in base agli errori (0 = 3★, 1–3 = 2★, 4–9 = 1★)
- Monete in base alle stelle (5/10/20), rigiocando si guadagna solo il miglioramento
- Aiuto: evidenzia una freccia liberabile (25 monete)
- Inglese e italiano, scelta al primo avvio
- Salvataggio locale dei progressi
