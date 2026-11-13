# Istruzioni per Claude (Outflow)

- Lingua: italiano. Il progetto è di Daniele; il tester principale è suo padre.
- Prima di tutto leggi **PROGETTO.md**: contiene decisioni, regole, economia, stato e prossimi passi.
  Le sezioni 8–10 descrivono la fase in corso.
- Stile di lavoro richiesto: quando Daniele scrive "BRIEFING" o "brief", **non produrre nulla**:
  discuti, fai domande di chiarimento e proponi sempre almeno una miglioria. Si sviluppa solo dopo l'ok.
- Dopo ogni versione: aumenta `versionCode`/`versionName` in `app/build.gradle.kts`, aggiorna
  README.md e PROGETTO.md (storico versioni compreso).
- Compilazione: GitHub Actions (push su `main` → APK nel ramo `build-output`). In locale serve JDK 17
  e Android SDK (`gradle assembleRelease`).
- Livelli: `python3 tools/generate_levels.py`; ogni livello deve risultare risolvibile dal risolutore.
  Se i livelli cambiano, nuovo file di salvataggio (`outflow_vN`) e regole di backup aggiornate.
- Non usare la chiave alpha (`app/outflow.keystore`) per lo Store.
