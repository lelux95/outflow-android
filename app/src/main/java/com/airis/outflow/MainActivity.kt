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

    private enum class Screen { LANGUAGE, MENU, LEVELS, GAME, SETTINGS }

    private lateinit var store: Store
    private lateinit var root: FrameLayout
    private var screen = Screen.MENU
    private lateinit var levels: List<Level>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        levels = Levels.all(this)
        S.lang = store.lang ?: "en"

        root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        setContentView(root)
        setupSystemBars()

        if (store.lang == null) showLanguage() else showMenu()
    }

    // ------------------------------------------------------------ barre di sistema
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
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    // ------------------------------------------------------------ aiuti grafici
    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    ).toInt()

    private fun text(s: String, sizeSp: Float, bold: Boolean = false, color: Int = Palette.INK): TextView =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

    private fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1.5f), strokeColor)
        }

    private fun button(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit): TextView {
        val bg = when {
            !enabled -> rounded(Palette.BG, 14f, Palette.LOCKED)
            primary -> rounded(Palette.INK, 14f)
            else -> rounded(Palette.CARD, 14f, Palette.TILE)
        }
        return text(label, 17f, bold = primary, color = when {
            !enabled -> Palette.QUIET
            primary -> Color.WHITE
            else -> Palette.INK
        }).apply {
            background = if (enabled) RippleDrawable(ColorStateList.valueOf(0x33000000), bg, null) else bg
            setPadding(dp(20f), dp(14f), dp(20f), dp(14f))
            isEnabled = enabled
            if (enabled) setOnClickListener { onClick() }
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

    private fun setScreen(s: Screen, content: View) {
        screen = s
        root.removeAllViews()
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun starsText(n: Int): String = "★".repeat(n) + "☆".repeat(3 - n)

    private fun topBar(title: String, onBack: () -> Unit, right: View? = null): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(8f), dp(16f), dp(8f))
        }
        val back = text("‹", 34f, color = Palette.INK).apply {
            setPadding(dp(12f), 0, dp(16f), dp(4f))
            contentDescription = S.t("back")
            setOnClickListener { onBack() }
        }
        bar.addView(back)
        bar.addView(text(title, 20f, bold = true).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (right != null) bar.addView(right)
        return bar
    }

    // ------------------------------------------------------------ schermate
    private fun showLanguage() {
        val col = column().apply { gravity = Gravity.CENTER; setPadding(dp(32f), 0, dp(32f), 0) }
        col.addWrap(text("Outflow", 44f, bold = true), 0f)
        col.addWrap(text("Choose your language · Scegli la lingua", 15f, color = Palette.QUIET), 8f)
        col.addFull(button("English", primary = true) { pickLanguage("en") }, 40f)
        col.addFull(button("Italiano", primary = true) { pickLanguage("it") }, 12f)
        setScreen(Screen.LANGUAGE, col)
    }

    private fun pickLanguage(l: String) {
        store.lang = l
        S.lang = l
        showMenu()
    }

    private fun showMenu() {
        val col = column().apply { gravity = Gravity.CENTER; setPadding(dp(32f), 0, dp(32f), 0) }
        col.addWrap(text("Outflow", 48f, bold = true), 0f)
        col.addWrap(text(S.t("subtitle"), 16f, color = Palette.QUIET), 2f)

        val stats = text(
            "${S.t("stars")} ${store.totalStars(levels.size)}/${levels.size * 3}    ·    ${S.t("coins")} ${store.coins}",
            15f, color = Palette.INK
        )
        col.addWrap(stats, 28f)

        col.addFull(button(S.t("play"), primary = true) { showLevels() }, 36f)
        col.addFull(button(S.t("themes_soon"), enabled = false) {}, 12f)
        col.addFull(button(S.t("settings")) { showSettings() }, 12f)
        setScreen(Screen.MENU, col)
    }

    private fun showLevels() {
        val outer = column()
        outer.addFull(topBar(S.t("levels"), { showMenu() },
            text("${S.t("coins")} ${store.coins}", 15f, color = Palette.QUIET)), 0f)

        val grid = GridLayout(this).apply {
            columnCount = 5
            setPadding(dp(12f), dp(4f), dp(12f), dp(24f))
        }
        for (lv in levels) {
            val open = store.unlocked(lv.id)
            val st = store.stars(lv.id)
            val cellCol = column().apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10f), 0, dp(10f))
                background = if (open) {
                    RippleDrawable(ColorStateList.valueOf(0x22000000),
                        rounded(Palette.CARD, 12f, if (st == 0) Palette.INK else Palette.TILE), null)
                } else rounded(Palette.BG, 12f, Palette.LOCKED)
            }
            cellCol.addWrap(text(lv.id.toString(), 20f, bold = true,
                color = if (open) Palette.INK else Palette.LOCKED), 0f)
            cellCol.addWrap(text(if (open) starsText(st) else "·", 12f,
                color = if (open) Palette.STAR else Palette.LOCKED), 2f)
            if (open) cellCol.setOnClickListener { showGame(lv.id) }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(dp(5f), dp(5f), dp(5f), dp(5f))
            }
            grid.addView(cellCol, lp)
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(Screen.LEVELS, outer)
    }

    // ------------------------------------------------------------ partita
    private var currentLevel = 1

    private fun showGame(id: Int) {
        currentLevel = id
        val lv = levels.first { it.id == id }

        val frame = FrameLayout(this)
        val col = column()
        val attemptsLabel = text("", 15f, bold = true)
        col.addFull(topBar(S.f("level", id), { showLevels() }, attemptsLabel), 0f)

        val tutorial = when (id) {
            1 -> S.t("tut1")
            2 -> S.t("tut2")
            else -> null
        }
        if (tutorial != null) {
            col.addFull(text(tutorial, 15f, color = Palette.QUIET).apply {
                setPadding(dp(24f), 0, dp(24f), 0)
            }, 4f)
        }

        val game = GameView(this)
        game.vibrate = store.vibration
        col.addView(game, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(16f))
        }
        val restart = button(S.t("restart")) { game.setLevel(lv); updateAttempts(attemptsLabel, game) }
        val hint = button(S.f("hint", Rules.HINT_COST)) {
            if (store.coins < Rules.HINT_COST) {
                Toast.makeText(this, S.t("no_coins"), Toast.LENGTH_SHORT).show()
            } else if (game.hint()) {
                store.coins = store.coins - Rules.HINT_COST
            }
        }
        val lpA = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6f) }
        val lpB = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6f) }
        bottom.addView(restart, lpA)
        bottom.addView(hint, lpB)
        col.addFull(bottom, 0f)

        frame.addView(col)

        game.listener = object : GameView.Listener {
            override fun onMistake(attemptsLeft: Int) = updateAttempts(attemptsLabel, game)
            override fun onArrowOut(remaining: Int) {}
            override fun onWin(errors: Int) = showResult(frame, lv, errors)
            override fun onLose() = showLost(frame, lv)
        }
        game.setLevel(lv)
        updateAttempts(attemptsLabel, game)
        setScreen(Screen.GAME, frame)
    }

    private fun updateAttempts(label: TextView, game: GameView) {
        label.text = S.f("attempts", game.attempts, Rules.MAX_ATTEMPTS)
        label.setTextColor(if (game.attempts <= 3) Palette.ERROR else Palette.INK)
    }

    private fun overlay(frame: FrameLayout, build: (LinearLayout) -> Unit) {
        val shade = FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true // blocca i tocchi sul campo sotto
        }
        val card = column().apply {
            background = rounded(Palette.BG, 24f)
            setPadding(dp(28f), dp(28f), dp(28f), dp(24f))
        }
        build(card)
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.setMargins(dp(28f), 0, dp(28f), 0)
        shade.addView(card, lp)
        frame.addView(shade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showResult(frame: FrameLayout, lv: Level, errors: Int) {
        val stars = Rules.starsFor(errors)
        val earned = store.recordResult(lv.id, stars)
        val hasNext = levels.any { it.id == lv.id + 1 }
        overlay(frame) { card ->
            card.addWrap(text(S.t("win"), 26f, bold = true), 0f)
            card.addWrap(text(starsText(stars), 44f, color = Palette.STAR), 12f)
            card.addWrap(text(
                if (earned > 0) S.f("earned", earned) else S.t("no_earned"),
                15f, color = Palette.QUIET), 8f)
            if (hasNext) {
                card.addFull(button(S.t("next"), primary = true) { showGame(lv.id + 1) }, 24f)
            } else {
                card.addFull(text(S.t("all_done"), 15f), 20f)
            }
            card.addFull(button(S.t("retry")) { showGame(lv.id) }, 12f)
            card.addFull(button(S.t("levels")) { showLevels() }, 12f)
        }
    }

    private fun showLost(frame: FrameLayout, lv: Level) {
        overlay(frame) { card ->
            card.addWrap(text(S.t("lose"), 26f, bold = true), 0f)
            card.addFull(text(S.t("lose_sub"), 15f, color = Palette.QUIET), 10f)
            card.addFull(button(S.t("retry"), primary = true) { showGame(lv.id) }, 24f)
            card.addFull(button(S.t("levels")) { showLevels() }, 12f)
        }
    }

    // ------------------------------------------------------------ impostazioni
    private fun showSettings() {
        val outer = column()
        outer.addFull(topBar(S.t("settings"), { showMenu() }), 0f)
        val col = column().apply { setPadding(dp(24f), dp(8f), dp(24f), dp(24f)) }

        col.addFull(text(S.t("language"), 14f, color = Palette.QUIET).apply { gravity = Gravity.START }, 8f)
        val langRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val en = button("English", primary = S.lang == "en") { pickLanguageFromSettings("en") }
        val it = button("Italiano", primary = S.lang == "it") { pickLanguageFromSettings("it") }
        langRow.addView(en, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6f) })
        langRow.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6f) })
        col.addFull(langRow, 8f)

        col.addFull(text(S.t("vibration"), 14f, color = Palette.QUIET).apply { gravity = Gravity.START }, 24f)
        val vibRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val on = button(S.t("on"), primary = store.vibration) { store.vibration = true; showSettings() }
        val off = button(S.t("off"), primary = !store.vibration) { store.vibration = false; showSettings() }
        vibRow.addView(on, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6f) })
        vibRow.addView(off, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6f) })
        col.addFull(vibRow, 8f)

        col.addFull(button(S.t("reset")) { confirmReset() }, 40f)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        col.addFull(text(S.f("version", version), 13f, color = Palette.QUIET), 32f)

        val scroll = ScrollView(this).apply { addView(col) }
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(Screen.SETTINGS, outer)
    }

    private fun pickLanguageFromSettings(l: String) {
        store.lang = l
        S.lang = l
        showSettings()
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
            Screen.LEVELS, Screen.SETTINGS -> showMenu()
            Screen.MENU, Screen.LANGUAGE -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }
}
