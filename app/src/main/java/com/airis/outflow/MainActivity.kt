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

    private enum class Screen { LANGUAGE, MENU, LEVELS, GAME, SETTINGS, STATS, SHOP }

    private lateinit var store: Store
    private lateinit var sounds: Sounds
    private lateinit var root: FrameLayout
    private lateinit var levels: List<Level>
    private var screen = Screen.MENU
    private var bg: BgArrowsView? = null

    // timer dei boss
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timerLeftMs = 0L
    private var timerRunning = false
    private var timerLast = 0L
    private var timerTick: Runnable? = null
    private var timerHold = false // fermo mentre e' aperta la schermata di livello perso

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        sounds = Sounds(this)
        sounds.enabled = store.sound
        levels = Levels.all(this)
        S.lang = store.lang ?: "en"
        Palette.apply(Themes.byId(store.themeId))

        root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        setContentView(root)
        setupSystemBars()

        if (store.lang == null) showLanguage() else showMenu()
    }

    override fun onResume() {
        super.onResume()
        bg?.resume()
        if (timerTick != null && timerLeftMs > 0 && !timerHold) startTimer()
    }

    override fun onPause() {
        bg?.pause()
        pauseTimer()
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
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (Palette.DARK) 0 else light, light)
            root.setOnApplyWindowInsetsListener { v, insets ->
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                insets
            }
        } else {
            window.statusBarColor = Palette.BG
            window.navigationBarColor = Palette.BG
            window.decorView.systemUiVisibility = if (Palette.DARK) 0 else
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
        if (s != Screen.GAME) stopTimer()
        root.setBackgroundColor(Palette.BG)
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

        val gift = store.giftAvailable()
        if (gift > 0) {
            col.addFull(button(S.f("gift", gift), primary = true, fill = Palette.ACCENT) {
                val got = store.claimGift()
                if (got > 0) Toast.makeText(this, S.f("gift_got", got, store.giftStreakNext()), Toast.LENGTH_LONG).show()
                showMenu()
            }, 24f)
        }
        val next = nextLevelId()
        col.addFull(button(S.f("continue_lv", next), primary = true) { startLevel(next) }, if (gift > 0) 12f else 32f)
        col.addFull(button(S.t("levels")) { showLevels() }, 12f)
        col.addFull(button(S.t("shop")) { showShop() }, 12f)
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
                lv.boss -> rounded(if (st > 0) Palette.tint(Palette.BOSS, 0.16f) else Palette.CARD, 14f, Palette.BOSS, 2.5f)
                st > 0 -> rounded(Palette.tint(Palette.PRIMARY, 0.14f), 14f, Palette.PRIMARY, 1.5f)
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
        col.addWrap(text("⏱ ${fmtTime(lv.time * 1000L)}", 30f, bold = true, color = Palette.BOSS), 16f)
        col.addFull(button(S.t("start"), primary = true, fill = Palette.BOSS) { showGame(lv) }, 36f)
        col.addFull(button(S.t("levels")) { showLevels() }, 12f)
        setScreen(Screen.GAME, col)
    }

    private fun fmtTime(ms: Long): String {
        val secs = ((ms + 999) / 1000).coerceAtLeast(0)
        return String.format("%d:%02d", secs / 60, secs % 60)
    }

    // ------------------------------------------------------------ timer dei boss
    private var timerLabel: TextView? = null
    private var onTimeUp: (() -> Unit)? = null

    private fun startTimer() {
        if (timerRunning || timerLeftMs <= 0) return
        timerRunning = true
        timerLast = SystemClock.elapsedRealtime()
        val tick = timerTick ?: return
        handler.post(tick)
    }

    private fun pauseTimer() {
        if (!timerRunning) return
        val now = SystemClock.elapsedRealtime()
        timerLeftMs -= now - timerLast
        timerRunning = false
        timerTick?.let { handler.removeCallbacks(it) }
    }

    private fun stopTimer() {
        timerRunning = false
        timerTick?.let { handler.removeCallbacks(it) }
        timerTick = null
        timerLeftMs = 0
        timerHold = false
        timerLabel = null
        onTimeUp = null
    }

    private fun setupTimer(seconds: Int, label: TextView, timeUp: () -> Unit) {
        stopTimer()
        timerLeftMs = seconds * 1000L
        timerLabel = label
        onTimeUp = timeUp
        timerTick = object : Runnable {
            override fun run() {
                if (!timerRunning) return
                val now = SystemClock.elapsedRealtime()
                timerLeftMs -= now - timerLast
                timerLast = now
                if (timerLeftMs <= 0) {
                    timerLeftMs = 0
                    timerRunning = false
                    updateTimerLabel()
                    onTimeUp?.invoke()
                    return
                }
                updateTimerLabel()
                handler.postDelayed(this, 100)
            }
        }
        updateTimerLabel()
    }

    private fun updateTimerLabel() {
        val l = timerLabel ?: return
        l.text = "⏱ ${fmtTime(timerLeftMs)}"
        l.setTextColor(if (timerLeftMs <= 10_000) Palette.ERROR else Palette.BOSS)
    }

    // ------------------------------------------------------------ partita
    private fun smallButton(label: String, primary: Boolean = false, fill: Int = Palette.PRIMARY, onClick: () -> Unit): TextView =
        button(label, primary = primary, fill = fill, onClick = onClick).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8f), dp(13f), dp(8f), dp(13f))
        }

    private fun stockOrCost(item: Item, cost: Int): String {
        val n = store.count(item)
        return if (n > 0) S.f("free_n", n) else "● $cost"
    }

    private fun showGame(lv: Level) {
        stopTimer()
        val id = lv.id
        val frame = FrameLayout(this)
        val col = column()
        val heartsLabel = text("", 20f)
        val title = if (lv.boss) "${S.f("level", id)} · ${S.t("boss")}" else S.f("level", id)
        col.addFull(topBar(title, { showLevels() }, heartsLabel), 0f)

        val timeLabel = text("", 24f, bold = true, color = Palette.BOSS)
        if (lv.time > 0) col.addWrap(timeLabel, 0f)

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
            setPadding(dp(12f), dp(8f), dp(12f), dp(16f))
        }
        var extras = 0
        var startedAt = SystemClock.elapsedRealtime()
        lateinit var hintBtn: TextView
        lateinit var revealBtn: TextView

        fun refreshBar() {
            coinsLabel.text = "● ${store.coins}"
            hintBtn.text = S.f("hint_btn", stockOrCost(Item.HINTS, Rules.HINT_COST))
            revealBtn.text = S.f("reveal", stockOrCost(Item.REVEAL, Rules.REVEAL_COST))
        }

        val restart = smallButton(S.t("restart")) {
            showGame(lv) // ricomincia da capo, timer compreso
        }
        hintBtn = smallButton("") {
            val stock = store.count(Item.HINTS) > 0
            if (!stock && store.coins < Rules.HINT_COST) {
                Toast.makeText(this, S.t("no_coins"), Toast.LENGTH_SHORT).show()
            } else if (game.hint()) {
                if (!store.useItem(Item.HINTS)) store.coins = store.coins - Rules.HINT_COST
                refreshBar()
            }
        }
        revealBtn = smallButton("") {
            val stock = store.count(Item.REVEAL) > 0
            if (!stock && store.coins < Rules.REVEAL_COST) {
                Toast.makeText(this, S.t("no_coins"), Toast.LENGTH_SHORT).show()
            } else if (game.reveal()) {
                if (!store.useItem(Item.REVEAL)) store.coins = store.coins - Rules.REVEAL_COST
                refreshBar()
            }
        }
        val lp = { l: Float, r: Float -> LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(l); rightMargin = dp(r) } }
        bottom.addView(restart, lp(0f, 4f))
        bottom.addView(hintBtn, lp(4f, 4f))
        bottom.addView(revealBtn, lp(4f, 0f))
        col.addFull(bottom, 4f)
        frame.addView(col)
        refreshBar()

        fun totalHearts() = maxOf(Rules.MAX_ATTEMPTS, game.attempts)

        val lost = { byTime: Boolean ->
            sounds.lose()
            pauseTimer()
            timerHold = true
            showLost(frame, lv, extras, byTime,
                onContinue = {
                    extras++
                    if (byTime) {
                        timerLeftMs += Rules.EXTRA_SECONDS * 1000L
                        updateTimerLabel()
                        game.resumeGame()
                    } else {
                        game.addAttempt()
                    }
                    heartsLabel.text = hearts(game.attempts, totalHearts())
                    refreshBar()
                    timerHold = false
                    startTimer()
                },
                onGiveUp = {
                    store.recordPlay(id, false, SystemClock.elapsedRealtime() - startedAt, game.errors, extras)
                })
        }

        game.listener = object : GameView.Listener {
            override fun onTap() {}
            override fun onArrowOut(remaining: Int) = sounds.out()
            override fun onMistake(attemptsLeft: Int) {
                sounds.error()
                heartsLabel.text = hearts(attemptsLeft.coerceAtLeast(0), totalHearts())
            }
            override fun onWin() {
                stopTimer()
                sounds.win()
                val ms = SystemClock.elapsedRealtime() - startedAt
                store.recordPlay(id, true, ms, game.errors, extras)
                showResult(frame, lv, game.errors, extras)
            }
            override fun onLose() = lost(false)
        }
        game.setLevel(lv)
        heartsLabel.text = hearts(game.attempts, Rules.MAX_ATTEMPTS)
        setScreen(Screen.GAME, frame)

        if (lv.time > 0) {
            setupTimer(lv.time, timeLabel) {
                game.stop()
                lost(true)
            }
            startTimer()
        }
        startedAt = SystemClock.elapsedRealtime()
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

    private fun showLost(
        frame: FrameLayout, lv: Level, extrasSoFar: Int, byTime: Boolean,
        onContinue: () -> Unit, onGiveUp: () -> Unit
    ) {
        val item = if (byTime) Item.TIME else Item.ATTEMPT
        val stock = store.count(item)
        val cost = Rules.extraCost(extrasSoFar)
        val canPay = store.coins >= cost
        overlay(frame) { card, close ->
            card.addWrap(text(S.t(if (byTime) "time_up" else "lose"), 26f, bold = true), 0f)
            if (!byTime) card.addWrap(text(hearts(0, Rules.MAX_ATTEMPTS), 26f), 8f)
            card.addFull(text(S.t(if (byTime) "time_sub" else "lose_sub"), 15f, color = Palette.QUIET), 8f)
            if (stock > 0) {
                card.addFull(button(S.f(if (byTime) "use_time" else "use_attempt", stock), primary = true, fill = Palette.PRIMARY) {
                    store.useItem(item)
                    close()
                    onContinue()
                }, 22f)
            }
            card.addFull(button(S.f(if (byTime) "extra_time" else "extra", cost), primary = stock == 0,
                enabled = canPay, fill = Palette.ACCENT) {
                store.coins = store.coins - cost
                close()
                onContinue()
            }, if (stock > 0) 10f else 22f)
            card.addFull(text(
                if (canPay || stock > 0) S.t("extra_note") else "${S.t("no_coins")} · ● ${store.coins}",
                12f, color = Palette.QUIET), 6f)
            card.addPair(
                button(S.t("retry")) { onGiveUp(); showGame(lv) },
                button(S.t("levels")) { onGiveUp(); showLevels() }, 16f)
        }
    }

    // ------------------------------------------------------------ negozio
    private fun swatches(t: Theme): CharSequence {
        val sb = SpannableStringBuilder()
        for (i in 0 until 6) {
            val start = sb.length
            sb.append("●")
            sb.setSpan(ForegroundColorSpan(t.arrows[i]), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" ")
        }
        return sb
    }

    private fun showShop() {
        val outer = column()
        val coins = text("● ${store.coins}", 16f, bold = true, color = Palette.INK)
        outer.addFull(topBar(S.t("shop"), { showMenu() }, coins), 0f)
        val col = column().apply { setPadding(dp(18f), dp(4f), dp(18f), dp(28f)) }

        fun section(key: String, top: Float) =
            col.addFull(text(S.t(key), 18f, bold = true).apply { gravity = Gravity.START }, top)

        section("shop_themes", 4f)
        val current = store.themeId
        for (t in Themes.all) {
            val has = store.hasTheme(t)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // anteprima: la scheda usa lo sfondo del tema
                background = rounded(t.bg, 16f, if (t.id == current) Palette.PRIMARY else t.line, if (t.id == current) 2.5f else 1.5f)
                setPadding(dp(14f), dp(10f), dp(10f), dp(10f))
            }
            val info = column().apply { gravity = Gravity.START }
            info.addFull(text(t.name, 16f, bold = true, color = t.ink).apply { gravity = Gravity.START }, 0f)
            info.addFull(text(swatches(t), 16f).apply { gravity = Gravity.START }, 2f)
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val action: View = when {
                t.id == current -> text(S.t("in_use"), 14f, bold = true, color = t.primary)
                has -> smallButton(S.t("use"), primary = true, fill = t.primary) {
                    store.themeId = t.id
                    Palette.apply(t)
                    setupSystemBars()
                    showShop()
                }
                t.unlockLevel > 0 -> text(S.f("unlock_at", t.unlockLevel), 12f, color = t.quiet)
                else -> smallButton(S.f("buy", t.price), primary = store.coins >= t.price, fill = t.primary) {
                    if (store.buyTheme(t)) {
                        Toast.makeText(this, S.t("bought"), Toast.LENGTH_SHORT).show()
                        showShop()
                    } else Toast.makeText(this, S.t("no_coins"), Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addFull(row, 10f)
        }

        section("shop_items", 26f)
        val items = listOf(
            Triple(Item.HINTS, "item_hint", "item_hint_sub"),
            Triple(Item.ATTEMPT, "item_attempt", "item_attempt_sub"),
            Triple(Item.TIME, "item_time", "item_time_sub"),
            Triple(Item.REVEAL, "item_reveal", "item_reveal_sub"),
        )
        for ((item, nameKey, subKey) in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Palette.CARD, 16f, Palette.LINE)
                setPadding(dp(14f), dp(10f), dp(10f), dp(10f))
            }
            val info = column().apply { gravity = Gravity.START }
            info.addFull(text(S.t(nameKey), 16f, bold = true).apply { gravity = Gravity.START }, 0f)
            info.addFull(text(S.t(subKey), 12f, color = Palette.QUIET).apply { gravity = Gravity.START }, 2f)
            info.addFull(text(S.f("owned", store.count(item)), 12f, bold = true, color = Palette.PRIMARY).apply { gravity = Gravity.START }, 2f)
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(smallButton(S.f("buy", item.price), primary = store.coins >= item.price) {
                if (store.buyItem(item)) {
                    Toast.makeText(this, S.t("bought"), Toast.LENGTH_SHORT).show()
                    showShop()
                } else Toast.makeText(this, S.t("no_coins"), Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addFull(row, 10f)
        }
        col.addFull(text(S.t("shop_soon"), 13f, color = Palette.QUIET), 22f)

        val scroll = ScrollView(this).apply { addView(col) }
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(Screen.SHOP, outer)
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
        col.addFull(button(S.t("test_coins"), primary = true, fill = Palette.BOSS) {
            store.coins = store.coins + 500
            Toast.makeText(this, "● ${store.coins}", Toast.LENGTH_SHORT).show()
        }, 24f)
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
            Screen.LEVELS, Screen.SETTINGS, Screen.SHOP -> showMenu()
            Screen.MENU, Screen.LANGUAGE -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }
}
