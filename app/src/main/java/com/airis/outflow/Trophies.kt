package com.airis.outflow

/** Trofei: si sbloccano giocando e regalano monete. */
enum class Trophy(val id: String, val reward: Int, val en: String, val it: String) {
    FIRST_WIN("first_win", 20, "First steps · Complete a level", "Primi passi · Completa un livello"),
    TEN_LEVELS("ten_levels", 50, "Warming up · Complete 10 levels", "Riscaldamento · Completa 10 livelli"),
    FIFTY_LEVELS("fifty_levels", 150, "Halfway · Complete 50 levels", "A metà strada · Completa 50 livelli"),
    ALL_LEVELS("all_levels", 500, "Outflow master · Complete all levels", "Maestro di Outflow · Completa tutti i livelli"),
    FIRST_BOSS("first_boss", 50, "Boss slayer · Defeat a boss", "Ammazzaboss · Sconfiggi un boss"),
    FIVE_BOSSES("five_bosses", 150, "Boss hunter · Defeat 5 bosses", "Cacciatore di boss · Sconfiggi 5 boss"),
    FAST_BOSS("fast_boss", 100, "Lightning · Beat a boss in half the time", "Fulmine · Batti un boss in metà tempo"),
    PERFECT_10("perfect_10", 100, "Perfectionist · 3 stars on 10 levels", "Perfezionista · 3 stelle su 10 livelli"),
    ARROWS_1K("arrows_1k", 100, "Free them all · Free 1,000 arrows", "Liberatutto · Libera 1.000 frecce"),
    ARROWS_10K("arrows_10k", 300, "Arrow storm · Free 10,000 arrows", "Tempesta di frecce · Libera 10.000 frecce"),
    STREAK_7("streak_7", 150, "Faithful · Daily gift 7 days in a row", "Fedele · Regalo 7 giorni di fila"),
    DAILY_1("daily_1", 50, "Daily player · Complete a daily challenge", "Giocatore del giorno · Completa una sfida del giorno"),
    DAILY_7("daily_7", 200, "Regular · Complete 7 daily challenges", "Habitué · Completa 7 sfide del giorno"),
    SHOPPER("shopper", 30, "Shopper · Buy something in the shop", "Shopping · Compra qualcosa nel negozio");

    val title: String get() = (if (S.lang == "it") it else en).substringBefore(" · ")
    val goal: String get() = (if (S.lang == "it") it else en).substringAfter(" · ")
}
