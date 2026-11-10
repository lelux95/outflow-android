package com.airis.outflow

import android.content.Context
import org.json.JSONObject
import kotlin.math.roundToInt

/** Una freccia: celle dalla coda alla punta (coppie x,y) e direzione della punta (0 su, 1 destra, 2 giu', 3 sinistra). */
class Arrow(val xs: IntArray, val ys: IntArray, val dir: Int) {
    val size: Int get() = xs.size
    val headX: Int get() = xs[xs.size - 1]
    val headY: Int get() = ys[ys.size - 1]
}

class Level(val id: Int, val shape: String, val w: Int, val h: Int, val boss: Boolean, val arrows: List<Arrow>)

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
            val arrows = ArrayList<Arrow>(a.length())
            for (j in 0 until a.length()) {
                val ao = a.getJSONObject(j)
                val c = ao.getJSONArray("c")
                val n = c.length() / 2
                val xs = IntArray(n) { c.getInt(it * 2) }
                val ys = IntArray(n) { c.getInt(it * 2 + 1) }
                arrows.add(Arrow(xs, ys, ao.getInt("d")))
            }
            list.add(Level(o.getInt("id"), o.getString("shape"), o.getInt("w"), o.getInt("h"),
                o.optBoolean("boss", false), arrows))
        }
        cache = list
        return list
    }
}

/** Regole di punteggio ed economia delle monete. */
object Rules {
    const val MAX_ATTEMPTS = 3
    const val HINT_COST = 25
    const val EXTRA_BASE_COST = 30

    /** 0 errori = 3 stelle, 1 = 2, 2 o piu' (o tentativi comprati) = 1. */
    fun starsFor(errors: Int, boughtExtra: Int): Int = when {
        boughtExtra > 0 -> 1
        errors == 0 -> 3
        errors == 1 -> 2
        else -> 1
    }

    fun coinsFor(stars: Int, boss: Boolean): Int {
        val base = when (stars) {
            3 -> 20
            2 -> 10
            1 -> 5
            else -> 0
        }
        return if (boss) (base * 1.5).roundToInt() else base
    }

    /** Prezzo del tentativo extra: raddoppia a ogni acquisto nello stesso livello (30, 60, 120...). */
    fun extraCost(alreadyBought: Int): Int = EXTRA_BASE_COST shl alreadyBought
}

/** Statistiche di una partita, registrate per la modalita' Tester. */
class PlayStat(val plays: Int, val wins: Int, val bestStars: Int, val lastMs: Long, val lastErrors: Int, val extrasBought: Int)

/** Salvataggio locale. Il file "outflow_v2" riparte da zero rispetto all'alpha 0.1. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("outflow_v2", Context.MODE_PRIVATE)

    init {
        // porta con se' solo lingua e vibrazione dalla versione precedente
        if (!p.contains("migrated")) {
            val old = ctx.getSharedPreferences("outflow", Context.MODE_PRIVATE)
            val e = p.edit().putBoolean("migrated", true)
            old.getString("lang", null)?.let { e.putString("lang", it) }
            if (old.contains("vibration")) e.putBoolean("vibration", old.getBoolean("vibration", true))
            e.apply()
        }
    }

    var lang: String?
        get() = p.getString("lang", null)
        set(v) { p.edit().putString("lang", v).apply() }

    var coins: Int
        get() = p.getInt("coins", 0)
        set(v) { p.edit().putInt("coins", v).apply() }

    var vibration: Boolean
        get() = p.getBoolean("vibration", true)
        set(v) { p.edit().putBoolean("vibration", v).apply() }

    var sound: Boolean
        get() = p.getBoolean("sound", true)
        set(v) { p.edit().putBoolean("sound", v).apply() }

    /** Modalita' Tester: tutti i livelli sbloccati (solo nelle versioni alpha). */
    var tester: Boolean
        get() = BuildConfig.ALPHA && p.getBoolean("tester", false)
        set(v) { p.edit().putBoolean("tester", v).apply() }

    fun stars(id: Int): Int = p.getInt("stars_$id", 0)

    fun totalStars(count: Int): Int = (1..count).sumOf { stars(it) }

    fun unlocked(id: Int): Boolean = tester || id == 1 || stars(id - 1) > 0

    /** Registra il risultato. Restituisce le monete guadagnate (solo il miglioramento). */
    fun recordResult(level: Level, newStars: Int): Int {
        val old = stars(level.id)
        if (newStars <= old) return 0
        val earned = Rules.coinsFor(newStars, level.boss) - Rules.coinsFor(old, level.boss)
        p.edit().putInt("stars_${level.id}", newStars).putInt("coins", coins + earned).apply()
        return earned
    }

    fun recordPlay(id: Int, won: Boolean, ms: Long, errors: Int, extras: Int) {
        val s = stat(id)
        p.edit()
            .putInt("st_plays_$id", s.plays + 1)
            .putInt("st_wins_$id", s.wins + if (won) 1 else 0)
            .putLong("st_ms_$id", ms)
            .putInt("st_err_$id", errors)
            .putInt("st_ext_$id", s.extrasBought + extras)
            .apply()
    }

    fun stat(id: Int) = PlayStat(
        p.getInt("st_plays_$id", 0), p.getInt("st_wins_$id", 0), stars(id),
        p.getLong("st_ms_$id", 0), p.getInt("st_err_$id", 0), p.getInt("st_ext_$id", 0)
    )

    fun resetProgress() {
        val l = lang
        val v = vibration
        val s = sound
        val t = p.getBoolean("tester", false)
        p.edit().clear().putBoolean("migrated", true).apply()
        lang = l
        vibration = v
        sound = s
        tester = t
    }
}
