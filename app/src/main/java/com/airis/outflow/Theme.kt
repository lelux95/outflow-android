package com.airis.outflow

/** Un tema: colori di sfondo, testi, pulsanti e frecce. */
class Theme(
    val id: String,
    val nameEn: String,
    val nameIt: String,
    val price: Int,          // 0 = incluso
    val unlockLevel: Int,    // > 0 = si ottiene completando quel livello (non acquistabile)
    val dark: Boolean,
    val bg: Int,
    val card: Int,
    val ink: Int,
    val quiet: Int,
    val line: Int,
    val locked: Int,
    val primary: Int,
    val accent: Int,
    val arrows: IntArray,
) {
    val name: String get() = if (S.lang == "it") nameIt else nameEn
}

object Themes {
    private fun c(v: Long) = v.toInt()
    private fun cs(vararg v: Long) = IntArray(v.size) { v[it].toInt() }

    val all = listOf(
        Theme("classic", "Classic", "Classico", 0, 0, false,
            c(0xFFFBF6EE), c(0xFFFFFFFF), c(0xFF26323A), c(0xFF8A8478), c(0xFFE9E1D3), c(0xFFD8D0C2),
            c(0xFF2A9D8F), c(0xFFE76F51),
            cs(0xFF2A9D8F, 0xFFE76F51, 0xFF5B6CC9, 0xFFE0A21E, 0xFFD9668F, 0xFF4F9F5A, 0xFF8E6CC0, 0xFF3A94D0)),
        Theme("dark", "Dark", "Scuro", 0, 0, true,
            c(0xFF1C2124), c(0xFF283035), c(0xFFEDE8DF), c(0xFF98A0A4), c(0xFF384148), c(0xFF4A5459),
            c(0xFF2A9D8F), c(0xFFE76F51),
            cs(0xFF3CC4B3, 0xFFFF8A6B, 0xFF8796F0, 0xFFF2C04A, 0xFFF28AB0, 0xFF73C27D, 0xFFB394E6, 0xFF64B5F0)),
        Theme("pastel", "Pastel", "Pastello", 150, 0, false,
            c(0xFFFDF5F8), c(0xFFFFFFFF), c(0xFF3F3A4F), c(0xFF948CA3), c(0xFFF0E4EB), c(0xFFDCCFD7),
            c(0xFF8C8FD6), c(0xFFF0A07E),
            cs(0xFF7FB7BE, 0xFFF0A07E, 0xFF9FA8DA, 0xFFE3BE5E, 0xFFE59BB5, 0xFF8CC084, 0xFFB79FD9, 0xFF7EB6E0)),
        Theme("ocean", "Ocean", "Oceano", 250, 0, false,
            c(0xFFEEF6F9), c(0xFFFFFFFF), c(0xFF16324A), c(0xFF6F8796), c(0xFFDCE9EF), c(0xFFC5D6DE),
            c(0xFF1F6F8B), c(0xFF14A89A),
            cs(0xFF1F6F8B, 0xFF2A9D8F, 0xFF3A94D0, 0xFF5B6CC9, 0xFF14B8A6, 0xFF0E7490, 0xFF5C9FE8, 0xFF0891B2)),
        Theme("sunset", "Sunset", "Tramonto", 400, 0, false,
            c(0xFFFFF3E8), c(0xFFFFFFFF), c(0xFF3D2230), c(0xFF9A7F82), c(0xFFF5E1D2), c(0xFFE3CBBA),
            c(0xFFE76F51), c(0xFFB5179E),
            cs(0xFFE76F51, 0xFFF4A261, 0xFFE0B23C, 0xFFD9668F, 0xFFB5179E, 0xFFF77F00, 0xFFC2410C, 0xFF9D4EDD)),
        Theme("neon", "Neon", "Neon", 600, 0, true,
            c(0xFF0D0D1A), c(0xFF1A1A2E), c(0xFFF5F5FF), c(0xFF9A9AC0), c(0xFF2A2A46), c(0xFF3A3A5A),
            c(0xFF9B5DE5), c(0xFF00BBF9),
            cs(0xFF00F5D4, 0xFFFF4FD8, 0xFFFFE600, 0xFF00BBF9, 0xFF9B5DE5, 0xFF39FF14, 0xFFFF6B35, 0xFFF15BB5)),
        Theme("gold", "Gold", "Oro", 0, 50, false,
            c(0xFFFBF7EC), c(0xFFFFFFFF), c(0xFF3A2E12), c(0xFF9A8A64), c(0xFFEFE5CC), c(0xFFDCCFAE),
            c(0xFFB8860B), c(0xFFCD7F32),
            cs(0xFFC9A227, 0xFFB8860B, 0xFFDAA520, 0xFF8C6D1F, 0xFFE0B84F, 0xFFA67C00, 0xFFCD7F32, 0xFF9C7A3C)),
        Theme("master", "Master", "Maestro", 0, 100, true,
            c(0xFF12100E), c(0xFF1E1B18), c(0xFFF3E9D2), c(0xFFA39A88), c(0xFF2E2A25), c(0xFF443E36),
            c(0xFFB8922A), c(0xFFE07A5F),
            cs(0xFFE9C46A, 0xFFF4F1DE, 0xFFE07A5F, 0xFF81B29A, 0xFFF2CC8F, 0xFFD4AF37, 0xFFB5838D, 0xFF6D9DC5)),
    )

    fun byId(id: String?): Theme = all.firstOrNull { it.id == id } ?: all[0]
}

/** Colori attivi: cambiano con il tema scelto. */
object Palette {
    var BG = 0; var CARD = 0; var INK = 0; var QUIET = 0; var LINE = 0; var LOCKED = 0
    var PRIMARY = 0; var ACCENT = 0
    var ARROWS = IntArray(8)
    var DARK = false
    var ERROR = 0xFFD63B3B.toInt()
    const val STAR = 0xFFE9A82E.toInt()
    const val BOSS = 0xFF7B5CC4.toInt()

    init { apply(Themes.all[0]) }

    fun apply(t: Theme) {
        BG = t.bg; CARD = t.card; INK = t.ink; QUIET = t.quiet; LINE = t.line; LOCKED = t.locked
        PRIMARY = t.primary; ACCENT = t.accent; ARROWS = t.arrows; DARK = t.dark
        ERROR = if (t.dark) 0xFFFF5A5A.toInt() else 0xFFD63B3B.toInt()
    }

    /** Mescola un colore con lo sfondo (amount 0..1 = quanto colore). */
    fun tint(color: Int, amount: Float): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun mix(s: Int) = (ch(BG, s) + (ch(color, s) - ch(BG, s)) * amount).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
