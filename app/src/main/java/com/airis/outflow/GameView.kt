package com.airis.outflow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** Colori dello stile base: minimal caldo, frecce colorate. */
object Palette {
    const val BG = 0xFFFBF6EE.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val INK = 0xFF26323A.toInt()
    const val QUIET = 0xFF8A8478.toInt()
    const val LINE = 0xFFE9E1D3.toInt()
    const val PRIMARY = 0xFF2A9D8F.toInt()
    const val ACCENT = 0xFFE76F51.toInt()
    const val ERROR = 0xFFD63B3B.toInt()
    const val STAR = 0xFFE9A82E.toInt()
    const val LOCKED = 0xFFD8D0C2.toInt()
    const val BOSS = 0xFF7B5CC4.toInt()

    val ARROWS = intArrayOf(
        0xFF2A9D8F.toInt(), // verde acqua
        0xFFE76F51.toInt(), // corallo
        0xFF5B6CC9.toInt(), // indaco
        0xFFE0A21E.toInt(), // senape
        0xFFD9668F.toInt(), // rosa
        0xFF4F9F5A.toInt(), // verde
        0xFF8E6CC0.toInt(), // viola
        0xFF3A94D0.toInt(), // azzurro
    )
}

/** Il campo di gioco: disegna le frecce, gestisce i tocchi e le animazioni. */
class GameView(context: Context) : View(context) {

    interface Listener {
        fun onTap()
        fun onArrowOut(remaining: Int)
        fun onMistake(attemptsLeft: Int)
        fun onWin()
        fun onLose()
    }

    var listener: Listener? = null
    var vibrate = true

    var attempts = Rules.MAX_ATTEMPTS
        private set
    var errors = 0
        private set

    private var level: Level? = null
    private var arrows: List<Arrow> = emptyList()
    private var alive = BooleanArray(0)
    private var occ = IntArray(0)          // cella -> indice freccia, -1 libera
    private var colors = IntArray(0)
    private var paths: Array<FloatArray> = emptyArray() // per freccia: x0,y0,x1,y1,... in celle
    private var finished = false

    private class Fly(val idx: Int, val start: Long)
    private val flying = ArrayList<Fly>()

    private var shakeIdx = -1
    private var shakeStart = 0L
    private var blockIdx = -1
    private var blockStart = 0L
    private var hintIdx = -1
    private var hintStart = 0L

    private var cell = 0f
    private var ox = 0f
    private var oy = 0f

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val body = Path()
    private val tip = Path()
    private val pt = FloatArray(2)

    fun setLevel(l: Level) {
        level = l
        arrows = l.arrows
        alive = BooleanArray(arrows.size) { true }
        occ = IntArray(l.w * l.h) { -1 }
        colors = IntArray(arrows.size) { Palette.ARROWS[it % Palette.ARROWS.size] }
        paths = Array(arrows.size) { buildPath(arrows[it]) }
        for ((i, a) in arrows.withIndex()) for (k in 0 until a.size) occ[a.ys[k] * l.w + a.xs[k]] = i
        attempts = Rules.MAX_ATTEMPTS
        errors = 0
        finished = false
        flying.clear()
        shakeIdx = -1; blockIdx = -1; hintIdx = -1
        relayout()
        invalidate()
    }

    /** Aggiunge un tentativo (tentativo extra comprato) e riprende la partita da dove era. */
    fun addAttempt() {
        attempts += 1
        finished = false
        invalidate()
    }

    /** Evidenzia una freccia che si puo' liberare. Restituisce false se non serve. */
    fun hint(): Boolean {
        val l = level ?: return false
        if (finished) return false
        if (hintIdx >= 0 && alive[hintIdx]) return false
        for (i in arrows.indices) {
            if (alive[i] && blocker(i, l) < 0) {
                hintIdx = i
                hintStart = SystemClock.uptimeMillis()
                invalidate()
                return true
            }
        }
        return false
    }

    // percorso: un punto prima della coda, i centri delle celle, un punto molto avanti alla punta
    private fun buildPath(a: Arrow): FloatArray {
        val n = a.size
        val out = FloatArray((n + 2) * 2)
        val d0 = if (n >= 2) dirBetween(a.xs[0], a.ys[0], a.xs[1], a.ys[1]) else a.dir
        out[0] = a.xs[0] + 0.5f - DX[d0]
        out[1] = a.ys[0] + 0.5f - DY[d0]
        for (k in 0 until n) {
            out[(k + 1) * 2] = a.xs[k] + 0.5f
            out[(k + 1) * 2 + 1] = a.ys[k] + 0.5f
        }
        out[(n + 1) * 2] = a.headX + 0.5f + DX[a.dir] * FAR
        out[(n + 1) * 2 + 1] = a.headY + 0.5f + DY[a.dir] * FAR
        return out
    }

    private fun dirBetween(ax: Int, ay: Int, bx: Int, by: Int): Int = when {
        by < ay -> 0
        bx > ax -> 1
        by > ay -> 2
        else -> 3
    }

    /** Punto del percorso al parametro u (in celle; la coda e' a u=1, la punta a u=n). */
    private fun pointAt(p: FloatArray, n: Int, u: Float) {
        val uu = if (u < 0f) 0f else u
        if (uu < n) {
            val k = floor(uu).toInt()
            val f = uu - k
            pt[0] = p[k * 2] + (p[k * 2 + 2] - p[k * 2]) * f
            pt[1] = p[k * 2 + 1] + (p[k * 2 + 3] - p[k * 2 + 1]) * f
        } else {
            val f = min(1f, (uu - n) / FAR)
            pt[0] = p[n * 2] + (p[n * 2 + 2] - p[n * 2]) * f
            pt[1] = p[n * 2 + 1] + (p[n * 2 + 3] - p[n * 2 + 1]) * f
        }
    }

    private fun segDir(p: FloatArray, n: Int, u: Float, fallback: Int): Int {
        if (u >= n) return fallback
        val k = floor(if (u < 0f) 0f else u).toInt()
        val dx = p[k * 2 + 2] - p[k * 2]
        val dy = p[k * 2 + 3] - p[k * 2 + 1]
        return when {
            abs(dx) >= abs(dy) && dx > 0 -> 1
            abs(dx) >= abs(dy) -> 3
            dy > 0 -> 2
            else -> 0
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val l = level ?: return
        if (width == 0 || height == 0) return
        val pad = 18f * resources.displayMetrics.density
        val maxCell = 88f * resources.displayMetrics.density
        cell = min(min((width - 2 * pad) / l.w, (height - 2 * pad) / l.h), maxCell)
        ox = (width - cell * l.w) / 2f
        oy = (height - cell * l.h) / 2f
        stroke.pathEffect = CornerPathEffect(cell * 0.32f)
        glow.pathEffect = CornerPathEffect(cell * 0.32f)
    }

    /** Indice della prima freccia che blocca la freccia i, oppure -1 se la strada e' libera. */
    private fun blocker(i: Int, l: Level): Int {
        val a = arrows[i]
        var x = a.headX + DX[a.dir]
        var y = a.headY + DY[a.dir]
        while (x in 0 until l.w && y in 0 until l.h) {
            val o = occ[y * l.w + x]
            if (o >= 0) return o
            x += DX[a.dir]; y += DY[a.dir]
        }
        return -1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val l = level ?: return true
                val cx = floor((event.x - ox) / cell).toInt()
                val cy = floor((event.y - oy) / cell).toInt()
                if (cx in 0 until l.w && cy in 0 until l.h) {
                    val i = occ[cy * l.w + cx]
                    if (i >= 0) tap(i, l)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun tap(i: Int, l: Level) {
        if (finished) return
        val now = SystemClock.uptimeMillis()
        listener?.onTap()
        val b = blocker(i, l)
        if (b < 0) {
            alive[i] = false
            val a = arrows[i]
            for (k in 0 until a.size) occ[a.ys[k] * l.w + a.xs[k]] = -1
            flying.add(Fly(i, now))
            if (hintIdx == i) hintIdx = -1
            val remaining = alive.count { it }
            listener?.onArrowOut(remaining)
            if (remaining == 0) {
                finished = true
                postDelayed({ listener?.onWin() }, 550)
            }
        } else {
            attempts--
            errors++
            shakeIdx = i; shakeStart = now
            blockIdx = b; blockStart = now
            buzz()
            listener?.onMistake(attempts)
            if (attempts <= 0) {
                finished = true
                postDelayed({ listener?.onLose() }, 650)
            }
        }
        invalidate()
    }

    private fun buzz() {
        if (!vibrate) return
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            v?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (level == null || cell <= 0f) return
        val now = SystemClock.uptimeMillis()
        var animating = false

        for (i in arrows.indices) {
            if (!alive[i]) continue
            var dx = 0f
            var color = colors[i]
            var widthMul = 1f
            if (i == shakeIdx) {
                val t = (now - shakeStart) / 400f
                if (t < 1f) {
                    dx = (sin(t * PI * 6) * cell * 0.09f * (1 - t)).toFloat()
                    color = Palette.ERROR
                    animating = true
                } else shakeIdx = -1
            }
            if (i == blockIdx) {
                val t = (now - blockStart) / 900f
                if (t < 1f) {
                    val pulse = abs(sin(t * PI * 3)).toFloat()
                    widthMul = 1f + 0.45f * pulse
                    color = Palette.INK
                    animating = true
                } else blockIdx = -1
            }
            if (i == hintIdx) {
                val t = (now - hintStart) / 1000f
                val a = (90 + 70 * sin(t * 2 * PI * 1.1)).toInt().coerceIn(0, 255)
                drawArrow(canvas, i, 0f, dx, colors[i], 1f, a)
                animating = true
            }
            drawArrow(canvas, i, 0f, dx, color, widthMul, -1)
        }

        // frecce in uscita: scivolano lungo il proprio percorso e accelerano
        val it = flying.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val t = (now - f.start) / 1000f
            val s = 5f * t + 34f * t * t
            val a = arrows[f.idx]
            val p = paths[f.idx]
            pointAt(p, a.size, 1f - TAIL + s)
            val tx = ox + pt[0] * cell
            val ty = oy + pt[1] * cell
            if (s > FAR || tx < -cell || ty < -cell || tx > width + cell || ty > height + cell) {
                it.remove(); continue
            }
            drawArrow(canvas, f.idx, s, 0f, colors[f.idx], 1f, -1)
            animating = true
        }

        if (animating) postInvalidateOnAnimation()
    }

    /** Disegna la freccia i spostata di s celle lungo il percorso. glowAlpha >= 0 = alone dell'aiuto. */
    private fun drawArrow(c: Canvas, i: Int, s: Float, dx: Float, color: Int, widthMul: Float, glowAlpha: Int) {
        val a = arrows[i]
        val p = paths[i]
        val n = a.size
        val from = 1f - TAIL + s
        val to = n.toFloat() + s
        body.reset()
        pointAt(p, n, from)
        body.moveTo(ox + pt[0] * cell + dx, oy + pt[1] * cell)
        var k = floor(from).toInt() + 1
        while (k < to && k <= n) {
            body.lineTo(ox + p[k * 2] * cell + dx, oy + p[k * 2 + 1] * cell)
            k++
        }
        pointAt(p, n, to)
        val hx = ox + pt[0] * cell + dx
        val hy = oy + pt[1] * cell
        body.lineTo(hx, hy)

        val d = segDir(p, n, to - 0.001f, a.dir)
        val len = cell * 0.36f
        val half = cell * 0.24f
        tip.reset()
        tip.moveTo(hx + DX[d] * len, hy + DY[d] * len)
        tip.lineTo(hx - DY[d] * half, hy + DX[d] * half)
        tip.lineTo(hx + DY[d] * half, hy - DX[d] * half)
        tip.close()

        if (glowAlpha >= 0) {
            glow.color = color
            glow.alpha = glowAlpha
            glow.strokeWidth = cell * 0.5f
            c.drawPath(body, glow)
            return
        }
        stroke.color = color
        stroke.strokeWidth = cell * 0.17f * widthMul
        head.color = color
        c.drawPath(body, stroke)
        c.drawPath(tip, head)
    }

    companion object {
        private val DX = intArrayOf(0, 1, 0, -1)
        private val DY = intArrayOf(-1, 0, 1, 0)
        private const val FAR = 60f
        private const val TAIL = 0.28f
    }
}
