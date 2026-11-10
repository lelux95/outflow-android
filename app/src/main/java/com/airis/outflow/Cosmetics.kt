package com.airis.outflow

/** Stili di disegno delle frecce, acquistabili nel negozio. */
enum class ArrowStyle(val id: String, val price: Int, val nameEn: String, val nameIt: String, val descEn: String, val descIt: String) {
    CLASSIC("classic", 0, "Classic", "Classica", "Clean rounded line", "Linea pulita e arrotondata"),
    MARKER("marker", 200, "Marker", "Marker", "Bold felt-tip stroke", "Tratto spesso da pennarello"),
    CHALK("chalk", 300, "Chalk", "Gesso", "Rough hand-drawn line", "Tratto ruvido, disegnato a mano"),
    NEON("neon", 350, "Neon", "Neon", "Glowing light tubes", "Tubi di luce con alone"),
    PIXEL("pixel", 400, "Pixel", "Pixel", "Square retro blocks", "Blocchi squadrati retrò"),
    RAIL("rail", 450, "Rail", "Binario", "Double parallel line", "Doppia linea parallela");

    val label: String get() = if (S.lang == "it") nameIt else nameEn
    val desc: String get() = if (S.lang == "it") descIt else descEn

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** Scie lasciate dalle frecce quando escono. */
enum class Trail(val id: String, val price: Int, val nameEn: String, val nameIt: String, val descEn: String, val descIt: String) {
    NONE("none", 0, "None", "Nessuna", "Just the arrow", "Solo la freccia"),
    SPARKS("sparks", 150, "Sparks", "Scintille", "Bright little sparks", "Piccole scintille luminose"),
    CONFETTI("confetti", 200, "Confetti", "Coriandoli", "Colorful paper bits", "Pezzetti di carta colorata"),
    STARS("stars", 250, "Stars", "Stelline", "Twinkling small stars", "Stelline che brillano"),
    SMOKE("smoke", 300, "Smoke", "Fumo", "Soft puffs of smoke", "Sbuffi di fumo leggero"),
    RAINBOW("rainbow", 350, "Rainbow", "Arcobaleno", "A rainbow wake", "Una scia arcobaleno");

    val label: String get() = if (S.lang == "it") nameIt else nameEn
    val desc: String get() = if (S.lang == "it") descIt else descEn

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: NONE
    }
}
