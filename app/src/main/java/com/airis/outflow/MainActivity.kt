package com.airis.outflow

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private enum class Screen { LANGUAGE, MENU, LEVELS, GAME, SETTINGS, STATS }

    private lateinit var store: Store
    private lateinit var sounds: Sounds
    private lateinit var root: FrameLayout
    private lateinit var levels: List<Level>
    private var screen = Screen.MENU
    private var bg: BgArrowsView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        sounds = Sounds(this)
        sounds.enabled = store.sound
        levels = Levels.all(this)
        S.lang = store.lang ?: "en"

        root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        setContentView(root)
        setupSystemBars()

        if (store.lang == null) showLanguage() else showMenu()
    }

    override fun onResume() {
        super.onResume()
        bg?.resume()
    }

    override fun onPause() {
        bg?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        sounds.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------ barre di sistema
    @Suppress("DEPRECATION")
    private fun setupSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
            root.setOnApplyWindowInsetsListener { v, insets ->
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                insets
            }
        } else {
            window.statusBarColor = Palette.BG
            window.navigationBarColor = Palette.BG
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    // ------------------------------------------------------------ aiuti grafici
    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    ).toInt()

    private fun text(s: CharSequence, sizeSp: Float, bold: Boolean = false, color: Int = Palette.INK): TextView =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

    private fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null, strokeDp: Float = 1.5f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
        }

    private fun button(
        label: String,
        primary: Boolean = false,
        enabled: Boolean = true,
        fill: Int = Palette.PRIMARY,
        onClick: () -> Unit
    ): TextView {
        val bg = when {
            !enabled -> rounded(Palette.BG, 16f, Palette.LOCKED)
            primary -> rounded(fill, 16f)
            else -> rounded(Palette.CARD, 16f, Palette.LINE)
        }
        return text(label, 17f, bold = primary, color = when {
            !enabled -> Palette.QUIET
            primary -> Color.WHITE
            else -> Palette.INK
        }).apply {
            background = if (enabled) RippleDrawable(ColorStateList.valueOf(0x33000000), bg, null) else bg
            setPadding(dp(20f), dp(15f), dp(20f), dp(15f))
            isEnabled = enabled
            if (enabled) setOnClickListener { sounds.tap(); onClick() }
        }
    }

    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun LinearLayout.addFull(v: View, topDp: Float = 12f) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(topDp)
        addView(v, lp)
    }

    private fun LinearLayout.addWrap(v: View, topDp: Float = 12f) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(topDp)
        addView(v, lp)
    }

    private fun LinearLayout.addPair(a: View, b: View, topDp: Float = 8f) {
        val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6f) })
        row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6f) })
        addFull(row, topDp)
    }

    private fun setScreen(s: Screen, content: View) {
        screen = s
        bg?.pause()
        bg = null
        root.removeAllViews()
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun starsText(n: Int): String = "★".repeat(n) + "☆".repeat(3 - n)

    /** Titolo "Outflow" con le lettere nei colori delle frecce. */
    private fun logo(sizeSp: Float): TextView {
        val word = "Outflow"
        val sb = SpannableStringBuilder(word)
        for (i in word.indices) {
            sb.setSpan(ForegroundColorSpan(Palette.ARROWS[(i * 3) % Palette.ARROWS.size]), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text(sb, sizeSp, bold = true)
    }

    private fun hearts(left: Int, total: Int): CharSequence {
        val sb = SpannableStringBuilder()
        for (i in 0 until total) {
            val start = sb.length
            sb.append(if (i < left) "♥" else "♡")
            sb.setSpan(ForegroundColorSpan(if (i < left) Palette.ERROR else Palette.LOCKED), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (i < total - 1) sb.append(" ")
        }
        return sb
    }

    private fun topBar(title: String, onBack: () -> Unit, right: View? = null): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(8f), dp(16f), dp(8f))
        }
        val back = text("‹", 34f, color = Palette.INK).apply {
            setPadding(dp(12f), 0, dp(16f), dp(4f))
            contentDescription = S.t("back")
            setOnClickListener { sounds.tap(); onBack() }
        }
        bar.addView(back)
        bar.addView(text(title, 20f, bold = true).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (right != null) bar.addView(right)
        return bar
    }

    private fun nextLevelId(): Int =
        levels.firstOrNull { store.stars(it.id) == 0 && store.unlocked(it.id) }?.id ?: levels.last().id

    // ------------------------------------------------------------ schermate
    private fun showLanguage() {
        val col = column().apply { gravity = Gravity.CENTER; setPadding(dp(32f), 0, dp(32f), 0) }
        col.addWrap(logo(46f), 0f)
        col.addWrap(text("Choose your language · Scegli la lingua", 15f, color = Palette.QUIET), 8f)
        col.addFull(button("English", primary = true) { pickLanguage("en") }, 40f)
        col.addFull(button("Italiano", primary = true, fill = Palette.ACCENT) { pickLanguage("it") }, 12f)
        setScreen(Screen.LANGUAGE, col)
    }

    private fun pickLanguage(l: String) {
        store.lang = l
        S.lang = l
        showMenu()
    }

    private fun showMenu() {
        val frame = FrameLayout(this)
        val anim = BgArrowsView(this)
        frame.addView(anim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val col = column().apply { gravity = Gravity.CENTER; setPadding(dp(32f), 0, dp(32f), 0) }
        col.addWrap(logo(52f), 0f)
        col.addWrap(text(S.t("subtitle"), 16f, color = Palette.QUIET), 0f)

        val stats = text(
            "★ ${store.totalStars(levels.size)}/${levels.size * 3}     ● ${store.coins} ${S.t("coins").lowercase()}",
            15f, bold = true, color = Palette.INK
        ).apply {
            background = rounded(Palette.CARD, 20f, Palette.LINE)
            setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
        }
        col.addWrap(stats, 24f)

        val next = nextLevelId()
        col.addFull(button(S.f("continue_lv", next), primary = true) { startLevel(next) }, 32f)
        col.addFull(button(S.t("levels")) { showLevels() }, 12f)
        col.addFull(button(S.t("themes_soon"), enabled = false) {}, 12f)
        col.addFull(button(S.t("settings")) { showSettings() }, 12f)
        frame.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setScreen(Screen.MENU, frame)
        bg = anim
    }

    private fun showLevels() {
        val outer = column()
        outer.addFull(topBar(S.t("levels"), { showMenu() },
            text("● ${store.coins}", 15f, bold = true, color = Palette.QUIET)), 0f)

        val grid = GridLayout(this).apply {
            columnCount = 5
            setPadding(dp(12f), dp(4f), dp(12f), dp(24f))
        }
        for (lv in levels) {
            val open = store.unlocked(lv.id)
            val st = store.stars(lv.id)
            val cellBg = when {
                !open -> rounded(Palette.BG, 14f, Palette.LOCKED)
                lv.boss -> rounded(if (st > 0) 0xFFEDE6F8.toInt() else Palette.CARD, 14f, Palette.BOSS, 2.5f)
                st > 0 -> rounded(0xFFE4F3F0.toInt(), 14f, Palette.PRIMARY, 1.5f)
                else -> rounded(Palette.CARD, 14f, Palette.INK, 2f)
            }
            val cellCol = column().apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8f), 0, dp(8f))
                background = if (open) RippleDrawable(ColorStateList.valueOf(0x22000000), cellBg, null) else cellBg
            }
            if (lv.boss) {
                cellCol.addWrap(text(S.t("boss"), 9f, bold = true, color = if (open) Palette.BOSS else Palette.LOCKED), 0f)
            }
            cellCol.addWrap(text(lv.id.toString(), 19f, bold = true,
                color = if (open) (if (lv.boss) Palette.BOSS else Palette.INK) else Palette.LOCKED), 0f)
            cellCol.addWrap(text(if (open) starsText(st) else "·", 11f,
                color = if (open) Palette.STAR else Palette.LOCKED), 1f)
            if (open) cellCol.setOnClickListener { sounds.tap(); startLevel(lv.id) }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = dp(76f)
                setMargins(dp(5f), dp(5f), dp(5f), dp(5f))
            }
            grid.addView(cellCol, lp)
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(Screen.LEVELS, outer)
        // porta in vista il prossimo livello da giocare
        val target = nextLevelId()
        scroll.post {
            val idx = (target - 1).coerceIn(0, grid.childCount - 1)
            val child = grid.getChildAt(idx)
            if (child != null) scroll.scrollTo(0, (child.top - dp(120f)).coerceAtLeast(0))
        }
    }

    // ------------------------------------------------------------ partita
    private fun startLevel(id: Int) {
        val lv = levels.first { it.id == id }
        if (lv.boss) showBossIntro(lv) else showGame(lv)
    }

    private fun showBossIntro(lv: Level) {
        val col = column().apply { gravity = Gravity.CENTER; setPadding(dp(32f), 0, dp(32f), 0) }
        col.addWrap(text(S.t("boss"), 15f, bold = true, color = Color.WHITE).apply {
            background = rounded(Palette.BOSS, 20f)
            setPadding(dp(18f), dp(6f), dp(18f), dp(6f))
            letterSpacing = 0.2f
        }, 0f)
        col.addWrap(text(S.t("boss_title"), 34f, bold = true, color = Palette.BOSS), 18f)
        col.addWrap(text(S.f("boss_sub", lv.id, lv.arrows.size), 16f, color = Palette.QUIET), 8f)
        col.addFull(button(S.t("start"), primary = true, fill = Palette.BOSS) { showGame(lv) }, 36f)
        col.addFull(button(S.t("levels")) { showLevels() }, 12f)
        setScreen(Screen.GAME, col)
    }

    private fun showGame(lv: Level) {
        val id = lv.id
        val frame = FrameLayout(this)
        val col = column()
        val heartsLabel = text("", 20f)
        val title = if (lv.boss) "${S.f("level", id)} · ${S.t("boss")}" else S.f("level", id)
        col.addFull(topBar(title, { showLevels() }, heartsLabel), 0f)

        val tutorial = when (id) {
            1 -> S.t("tut1")
            2 -> S.t("tut2")
            else -> null
        }
        if (tutorial != null) {
            col.addFull(text(tutorial, 15f, color = Palette.QUIET).apply { setPadding(dp(24f), 0, dp(24f), 0) }, 2f)
        }

        val game = GameView(this)
        game.vibrate = store.vibration
        col.addView(game, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val coinsLabel = text("● ${store.coins}", 14f, bold = true, color = Palette.QUIET)
        col.addWrap(coinsLabel, 0f)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(16f))
        }
        var extras = 0
        var startedAt = SystemClock.elapsedRealtime()
        val restart = button(S.t("restart")) {
            game.setLevel(lv); extras = 0; startedAt = SystemClock.elapsedRealtime()
            heartsLabel.text = hearts(game.attempts, Rules.MAX_ATTEMPTS)
        }
        val hint = button(S.f("hint", Rules.HINT_COST)) {
            if (store.coins < Rules.HINT_COST) {
                Toast.makeText(this, S.t("no_coins"), Toast.LENGTH_SHORT).show()
            } else if (game.hint()) {
                store.coins = store.coins - Rules.HINT_COST
                coinsLabel.text = "● ${store.coins}"
            }
        }
        bottom.addView(restart, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6f) })
        bottom.addView(hint, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6f) })
        col.addFull(bottom, 4f)
        frame.addView(col)

        game.listener = object : GameView.Listener {
            override fun onTap() {}
            override fun onArrowOut(remaining: Int) = sounds.out()
            override fun onMistake(attemptsLeft: Int) {
                sounds.error()
                heartsLabel.text = hearts(attemptsLeft.coerceAtLeast(0), Rules.MAX_ATTEMPTS + extras)
            }
            override fun onWin() {
                sounds.win()
                val ms = SystemClock.elapsedRealtime() - startedAt
                store.recordPlay(id, true, ms, game.errors, extras)
                showResult(frame, lv, game.errors, extras)
            }
            override fun onLose() {
                sounds.lose()
                showLost(frame, lv, extras,
                    onContinue = {
                        extras++
                        game.addAttempt()
                        heartsLabel.text = hearts(game.attempts, Rules.MAX_ATTEMPTS + extras)
                        coinsLabel.text = "● ${store.coins}"
                    },
                    onGiveUp = {
                        store.recordPlay(id, false, SystemClock.elapsedRealtime() - startedAt, game.errors, extras)
                    })
            }
        }
        game.setLevel(lv)
        heartsLabel.text = hearts(game.attempts, Rules.MAX_ATTEMPTS)
        setScreen(Screen.GAME, frame)
    }

    private fun overlay(frame: FrameLayout, build: (LinearLayout, () -> Unit) -> Unit) {
        val shade = FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true // blocca i tocchi sul campo sotto
        }
        val card = column().apply {
            background = rounded(Palette.BG, 26f)
            setPadding(dp(28f), dp(28f), dp(28f), dp(24f))
        }
        build(card) { frame.removeView(shade) }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.setMargins(dp(26f), 0, dp(26f), 0)
        shade.addView(card, lp)
        frame.addView(shade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showResult(frame: FrameLayout, lv: Level, errors: Int, extras: Int) {
        val stars = Rules.starsFor(errors, extras)
        val earned = store.recordResult(lv, stars)
        val hasNext = levels.any { it.id == lv.id + 1 }
        overlay(frame) { card, _ ->
            card.addWrap(text(S.t(if (lv.boss) "boss_win" else "win"), 26f, bold = true,
                color = if (lv.boss) Palette.BOSS else Palette.INK), 0f)
            card.addWrap(text(starsText(stars), 46f, color = Palette.STAR), 10f)
            card.addWrap(text(
                if (earned > 0) S.f("earned", earned) else S.t("no_earned"),
                15f, bold = earned > 0, color = if (earned > 0) Palette.PRIMARY else Palette.QUIET), 6f)
            if (hasNext) {
                card.addFull(button(S.t("next"), primary = true) { startLevel(lv.id + 1) }, 24f)
            } else {
                card.addFull(text(S.t("all_done"), 15f), 20f)
            }
            card.addPair(
                button(S.t("retry")) { showGame(lv) },
                button(S.t("levels")) { showLevels() }, 12f)
        }
    }

    private fun showLost(frame: FrameLayout, lv: Level, extrasSoFar: Int, onContinue: () -> Unit, onGiveUp: () -> Unit) {
        val cost = Rules.extraCost(extrasSoFar)
        val canPay = store.coins >= cost
        overlay(frame) { card, close ->
            card.addWrap(text(S.t("lose"), 26f, bold = true), 0f)
            card.addWrap(text(hearts(0, Rules.MAX_ATTEMPTS), 26f), 8f)
            card.addFull(text(S.t("lose_sub"), 15f, color = Palette.QUIET), 8f)
            card.addFull(button(S.f("extra", cost), primary = true, enabled = canPay, fill = Palette.ACCENT) {
                store.coins = store.coins - cost
                close()
                onContinue()
            }, 22f)
            card.addFull(text(
                if (canPay) S.t("extra_note") else "${S.t("no_coins")} · ● ${store.coins}",
                12f, color = Palette.QUIET), 6f)
            card.addPair(
                button(S.t("retry")) { onGiveUp(); showGame(lv) },
                button(S.t("levels")) { onGiveUp(); showLevels() }, 16f)
        }
    }

    // ------------------------------------------------------------ impostazioni
    private fun showSettings() {
        val outer = column()
        outer.addFull(topBar(S.t("settings"), { showMenu() }), 0f)
        val col = column().apply { setPadding(dp(24f), dp(8f), dp(24f), dp(24f)) }

        fun label(key: String, top: Float) =
            col.addFull(text(S.t(key), 14f, color = Palette.QUIET).apply { gravity = Gravity.START }, top)

        label("language", 8f)
        col.addPair(
            button("English", primary = S.lang == "en") { setLang("en") },
            button("Italiano", primary = S.lang == "it") { setLang("it") })

        label("sound", 22f)
        col.addPair(
            button(S.t("on"), primary = store.sound) { store.sound = true; sounds.enabled = true; showSettings() },
            button(S.t("off"), primary = !store.sound) { store.sound = false; sounds.enabled = false; showSettings() })

        label("vibration", 22f)
        col.addPair(
            button(S.t("on"), primary = store.vibration) { store.vibration = true; showSettings() },
            button(S.t("off"), primary = !store.vibration) { store.vibration = false; showSettings() })

        if (BuildConfig.ALPHA) {
            label("tester", 22f)
            col.addPair(
                button(S.t("on"), primary = store.tester, fill = Palette.BOSS) { store.tester = true; showSettings() },
                button(S.t("off"), primary = !store.tester, fill = Palette.BOSS) { store.tester = false; showSettings() })
            col.addFull(text(S.t("tester_sub"), 12f, color = Palette.QUIET).apply { gravity = Gravity.START }, 6f)
            if (store.tester) col.addFull(button(S.t("tester_stats")) { showStats() }, 10f)
        }

        col.addFull(button(S.t("reset")) { confirmReset() }, 32f)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        col.addFull(text(S.f("version", version), 13f, color = Palette.QUIET), 28f)

        val scroll = ScrollView(this).apply { addView(col) }
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(Screen.SETTINGS, outer)
    }

    private fun setLang(l: String) {
        store.lang = l
        S.lang = l
        showSettings()
    }

    private fun showStats() {
        val outer = column()
        outer.addFull(topBar(S.t("tester_stats"), { showSettings() }), 0f)
        val col = column().apply { setPadding(dp(20f), dp(4f), dp(20f), dp(24f)) }
        var any = false
        for (lv in levels) {
            val s = store.stat(lv.id)
            if (s.plays == 0) continue
            any = true
            val secs = s.lastMs / 1000
            val time = String.format("%d:%02d", secs / 60, secs % 60)
            val row = column().apply {
                gravity = Gravity.START
                background = rounded(Palette.CARD, 12f, if (lv.boss) Palette.BOSS else Palette.LINE)
                setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            }
            row.addFull(text("${S.f("level", lv.id)}${if (lv.boss) " · ${S.t("boss")}" else ""}   ${starsText(s.bestStars)}",
                15f, bold = true).apply { gravity = Gravity.START }, 0f)
            row.addFull(text(S.f("stats_row", s.plays, s.wins, time, s.lastErrors, s.extrasBought),
                13f, color = Palette.QUIET).apply { gravity = Gravity.START }, 2f)
            col.addFull(row, 8f)
        }
        if (!any) col.addFull(text(S.t("stats_empty"), 15f, color = Palette.QUIET), 24f)
        val scroll = ScrollView(this).apply { addView(col) }
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(Screen.STATS, outer)
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setMessage(S.t("reset_q"))
            .setNegativeButton(S.t("cancel"), null)
            .setPositiveButton(S.t("yes")) { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage(S.t("reset_q2"))
                    .setNegativeButton(S.t("cancel"), null)
                    .setPositiveButton(S.t("yes")) { _, _ ->
                        store.resetProgress()
                        Toast.makeText(this, S.t("reset_done"), Toast.LENGTH_SHORT).show()
                        showSettings()
                    }
                    .show()
            }
            .show()
    }

    // ------------------------------------------------------------ tasto indietro
    @Deprecated("Gestione semplice del tasto indietro")
    override fun onBackPressed() {
        when (screen) {
            Screen.GAME -> showLevels()
            Screen.STATS -> showSettings()
            Screen.LEVELS, Screen.SETTINGS -> showMenu()
            Screen.MENU, Screen.LANGUAGE -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }
}
