package com.airis.outflow

import android.content.Context
import org.json.JSONObject

/** Un livello: griglia w x h, frecce come [x, y, direzione] (0 su, 1 destra, 2 giu', 3 sinistra). */
class Level(val id: Int, val shape: String, val w: Int, val h: Int, val arrows: List<IntArray>)

object Levels {
    @Volatile private var cache: List<Level>? = null

    fun all(ctx: Context): List<Level> {
        cache?.let { return it }
        val text = ctx.assets.open("levels.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("levels")
        val list = ArrayList<Level>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val a = o.getJSONArray("arrows")
            val arrows = ArrayList<IntArray>(a.length())
            for (j in 0 until a.length()) {
                val t = a.getJSONArray(j)
                arrows.add(intArrayOf(t.getInt(0), t.getInt(1), t.getInt(2)))
            }
            list.add(Level(o.getInt("id"), o.getString("shape"), o.getInt("w"), o.getInt("h"), arrows))
        }
        cache = list
        return list
    }
}

/** Regole di punteggio: stelle in base agli errori, monete in base alle stelle. */
object Rules {
    const val MAX_ATTEMPTS = 10
    const val HINT_COST = 25

    fun starsFor(errors: Int): Int = when {
        errors == 0 -> 3
        errors <= 3 -> 2
        else -> 1
    }

    fun coinsFor(stars: Int): Int = when (stars) {
        3 -> 20
        2 -> 10
        1 -> 5
        else -> 0
    }
}

/** Salvataggio locale: lingua, monete, stelle per livello, impostazioni. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("outflow", Context.MODE_PRIVATE)

    var lang: String?
        get() = p.getString("lang", null)
        set(v) { p.edit().putString("lang", v).apply() }

    var coins: Int
        get() = p.getInt("coins", 0)
        set(v) { p.edit().putInt("coins", v).apply() }

    var vibration: Boolean
        get() = p.getBoolean("vibration", true)
        set(v) { p.edit().putBoolean("vibration", v).apply() }

    fun stars(id: Int): Int = p.getInt("stars_$id", 0)

    fun totalStars(count: Int): Int = (1..count).sumOf { stars(it) }

    fun unlocked(id: Int): Boolean = id == 1 || stars(id - 1) > 0

    /** Registra il risultato. Restituisce le monete guadagnate (solo il miglioramento). */
    fun recordResult(id: Int, newStars: Int): Int {
        val old = stars(id)
        if (newStars <= old) return 0
        val earned = Rules.coinsFor(newStars) - Rules.coinsFor(old)
        p.edit().putInt("stars_$id", newStars).putInt("coins", coins + earned).apply()
        return earned
    }

    fun resetProgress() {
        val l = lang
        val v = vibration
        p.edit().clear().apply()
        lang = l
        vibration = v
    }
}
