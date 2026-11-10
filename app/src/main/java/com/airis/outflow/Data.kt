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

/** time > 0 = livello a tempo (boss), in secondi. */
class Level(val id: Int, val shape: String, val w: Int, val h: Int, val boss: Boolean, val time: Int, val arrows: List<Arrow>)

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
                o.optBoolean("boss", false), o.optInt("time", 0), arrows))
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
    const val REVEAL_COST = 50
    const val EXTRA_SECONDS = 15

    /** Regalo giornaliero: cresce se si gioca piu' giorni di fila (dal 7° giorno resta a 100). */
    fun dailyGift(streak: Int): Int = intArrayOf(20, 30, 40, 50, 60, 80, 100)[(streak - 1).coerceIn(0, 6)]

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

/** Oggetti del negozio (potenziamenti consumabili). */
enum class Item(val key: String, val price: Int, val qty: Int) {
    HINTS("hint", 60, 3),
    ATTEMPT("attempt", 70, 1),
    TIME("time", 80, 1),
    REVEAL("reveal", 100, 2),
}

/** Statistiche di una partita, registrate per la modalita' Tester. */
class PlayStat(val plays: Int, val wins: Int, val bestStars: Int, val lastMs: Long, val lastErrors: Int, val extrasBought: Int)

/** Salvataggio locale. "outflow_v4": livelli nuovi, si riparte da zero (restano lingua e impostazioni). */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("outflow_v4", Context.MODE_PRIVATE)

    init {
        // porta con se' solo lingua e vibrazione dalla versione precedente
        if (!p.contains("migrated")) {
            val e = p.edit().putBoolean("migrated", true)
            for (name in listOf("outflow_v3", "outflow_v2", "outflow")) {
                val old = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
                val lang = old.getString("lang", null) ?: continue
                e.putString("lang", lang)
                for (k in listOf("vibration", "sound", "tester")) if (old.contains(k)) e.putBoolean(k, old.getBoolean(k, true))
                old.getString("name", null)?.let { e.putString("name", it) }
                if (old.contains("name_asked")) e.putBoolean("name_asked", old.getBoolean("name_asked", false))
                break
            }
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

    var themeId: String
        get() = p.getString("theme", "classic") ?: "classic"
        set(v) { p.edit().putString("theme", v).apply() }

    /** Un tema e' disponibile se incluso, comprato o sbloccato completando il suo livello. */
    fun hasTheme(t: Theme): Boolean = when {
        t.unlockLevel > 0 -> stars(t.unlockLevel) > 0
        t.price == 0 -> true
        else -> p.getBoolean("owned_${t.id}", false)
    }

    /** Compra un tema. Restituisce false se le monete non bastano. */
    fun buyTheme(t: Theme): Boolean {
        if (hasTheme(t) || t.unlockLevel > 0 || coins < t.price) return false
        p.edit().putInt("coins", coins - t.price).putBoolean("owned_${t.id}", true).apply()
        return true
    }

    // ------------------------------------------------ nome, stili, scie, profilo
    var playerName: String
        get() = p.getString("name", "") ?: ""
        set(v) { p.edit().putString("name", v.trim().take(16)).apply() }

    /** true dopo che il giocatore ha visto la richiesta del nome (anche se l'ha saltata). */
    var nameAsked: Boolean
        get() = p.getBoolean("name_asked", false)
        set(v) { p.edit().putBoolean("name_asked", v).apply() }

    var styleId: String
        get() = p.getString("style", "classic") ?: "classic"
        set(v) { p.edit().putString("style", v).apply() }

    var trailId: String
        get() = p.getString("trail", "none") ?: "none"
        set(v) { p.edit().putString("trail", v).apply() }

    fun hasStyle(s: ArrowStyle) = s.price == 0 || p.getBoolean("own_style_${s.id}", false)
    fun hasTrail(t: Trail) = t.price == 0 || p.getBoolean("own_trail_${t.id}", false)

    fun buyStyle(s: ArrowStyle): Boolean {
        if (hasStyle(s) || coins < s.price) return false
        p.edit().putInt("coins", coins - s.price).putBoolean("own_style_${s.id}", true).apply()
        return true
    }

    fun buyTrail(t: Trail): Boolean {
        if (hasTrail(t) || coins < t.price) return false
        p.edit().putInt("coins", coins - t.price).putBoolean("own_trail_${t.id}", true).apply()
        return true
    }

    var arrowsFreed: Int
        get() = p.getInt("arrows_freed", 0)
        set(v) { p.edit().putInt("arrows_freed", v).apply() }

    /** Miglior tempo su un boss (ms), 0 se mai vinto. */
    fun bossBest(id: Int): Long = p.getLong("boss_best_$id", 0)

    fun recordBossTime(id: Int, ms: Long) {
        val b = bossBest(id)
        if (b == 0L || ms < b) p.edit().putLong("boss_best_$id", ms).apply()
    }

    fun giftStreak(): Int {
        val last = p.getLong("gift_day", -10)
        val today = java.time.LocalDate.now().toEpochDay()
        return if (last >= today - 1) p.getInt("gift_streak", 0) else 0
    }

    fun count(item: Item): Int = p.getInt("inv_${item.key}", 0)

    fun addItem(item: Item, n: Int) {
        p.edit().putInt("inv_${item.key}", (count(item) + n).coerceAtLeast(0)).apply()
    }

    /** Usa un pezzo dalla scorta. Restituisce false se la scorta e' vuota. */
    fun useItem(item: Item): Boolean {
        if (count(item) <= 0) return false
        addItem(item, -1)
        return true
    }

    fun buyItem(item: Item): Boolean {
        if (coins < item.price) return false
        p.edit().putInt("coins", coins - item.price).putInt("inv_${item.key}", count(item) + item.qty).apply()
        return true
    }

    private fun today(): Long = java.time.LocalDate.now().toEpochDay()

    /** Monete del regalo di oggi, oppure 0 se e' gia' stato ritirato. */
    fun giftAvailable(): Int {
        val last = p.getLong("gift_day", -10)
        if (last == today()) return 0
        val streak = if (last == today() - 1) p.getInt("gift_streak", 0) + 1 else 1
        return Rules.dailyGift(streak)
    }

    fun giftStreakNext(): Int {
        val last = p.getLong("gift_day", -10)
        return if (last == today() - 1) p.getInt("gift_streak", 0) + 1 else 1
    }

    /** Ritira il regalo di oggi. Restituisce le monete ricevute. */
    fun claimGift(): Int {
        val amount = giftAvailable()
        if (amount == 0) return 0
        val streak = giftStreakNext()
        p.edit().putLong("gift_day", today()).putInt("gift_streak", streak).putInt("coins", coins + amount).apply()
        return amount
    }

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
        val name = playerName
        val asked = nameAsked
        p.edit().clear().putBoolean("migrated", true).apply()
        themeId = "classic"
        playerName = name
        nameAsked = asked
        lang = l
        vibration = v
        sound = s
        tester = t
    }
}
