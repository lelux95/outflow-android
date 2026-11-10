package com.airis.outflow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Colori dello stile base (minimal chiaro). I temi arriveranno nella fase 4. */
object Palette {
    const val BG = 0xFFF4F1EA.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val INK = 0xFF2B2A28.toInt()
    const val QUIET = 0xFF8A857C.toInt()
    const val TILE = 0xFFE6E0D4.toInt()
    const val ACCENT = 0xFFE0683C.toInt()
    const val ERROR = 0xFFD03B3B.toInt()
    const val STAR = 0xFFE9A92B.toInt()
    const val LOCKED = 0xFFCFC8BA.toInt()
}

/** Il campo di gioco: disegna la figura e le frecce, gestisce i tocchi. */
class GameView(context: Context) : View(context) {

    interface Listener {
        fun onMistake(attemptsLeft: Int)
        fun onArrowOut(remaining: Int)
        fun onWin(errors: Int)
        fun onLose()
    }

    var listener: Listener? = null
    var vibrate = true

    var attempts = Rules.MAX_ATTEMPTS
        private set
    var errors = 0
        private set

    private var level: Level? = null
    private val arrows = HashMap<Int, Int>()   // chiave y*w+x -> direzione
    private val tiles = ArrayList<Int>()       // celle della figura (restano visibili)
    private var finished = false

    private class Fly(val x: Int, val y: Int, val dir: Int, val start: Long)
    private val flying = ArrayList<Fly>()

    private var shakeKey = -1
    private var shakeStart = 0L
    private var hintKey = -1
    private var hintStart = 0L

    private var cell = 0f
    private var ox = 0f
    private var oy = 0f

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TILE }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT }
    private val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val headPath = Path()
    private val rect = RectF()

    fun setLevel(l: Level) {
        level = l
        arrows.clear()
        tiles.clear()
        for (a in l.arrows) {
            val k = a[1] * l.w + a[0]
            arrows[k] = a[2]
            tiles.add(k)
        }
        attempts = Rules.MAX_ATTEMPTS
        errors = 0
        finished = false
        flying.clear()
        shakeKey = -1
        hintKey = -1
        relayout()
        invalidate()
    }

    /** Evidenzia una freccia che si puo' liberare. Restituisce false se non serve. */
    fun hint(): Boolean {
        val l = level ?: return false
        if (finished) return false
        if (hintKey >= 0 && arrows.containsKey(hintKey)) return false // gia' mostrato
        for ((k, d) in arrows) {
            if (isFree(k, d, l)) {
                hintKey = k
                hintStart = SystemClock.uptimeMillis()
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val l = level ?: return
        if (width == 0 || height == 0) return
        val pad = 20f * resources.displayMetrics.density
        val maxCell = 96f * resources.displayMetrics.density
        cell = min(min((width - 2 * pad) / l.w, (height - 2 * pad) / l.h), maxCell)
        ox = (width - cell * l.w) / 2f
        oy = (height - cell * l.h) / 2f
    }

    private fun isFree(key: Int, dir: Int, l: Level): Boolean {
        var x = key % l.w
        var y = key / l.w
        val dx = DX[dir]
        val dy = DY[dir]
        x += dx; y += dy
        while (x in 0 until l.w && y in 0 until l.h) {
            if (arrows.containsKey(y * l.w + x)) return false
            x += dx; y += dy
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val l = level ?: return true
                val cx = floor((event.x - ox) / cell).toInt()
                val cy = floor((event.y - oy) / cell).toInt()
                if (cx in 0 until l.w && cy in 0 until l.h) tap(cy * l.w + cx, l)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun tap(key: Int, l: Level) {
        if (finished) return
        val dir = arrows[key] ?: return
        val now = SystemClock.uptimeMillis()
        if (isFree(key, dir, l)) {
            arrows.remove(key)
            flying.add(Fly(key % l.w, key / l.w, dir, now))
            if (hintKey == key) hintKey = -1
            listener?.onArrowOut(arrows.size)
            if (arrows.isEmpty()) {
                finished = true
                postDelayed({ listener?.onWin(errors) }, 450)
            }
        } else {
            attempts--
            errors++
            shakeKey = key
            shakeStart = now
            buzz()
            listener?.onMistake(attempts)
            if (attempts <= 0) {
                finished = true
                postDelayed({ listener?.onLose() }, 500)
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
        val l = level ?: return
        if (cell <= 0f) return
        val now = SystemClock.uptimeMillis()
        var animating = false

        // la figura: le celle restano anche quando le frecce escono
        val inset = cell * 0.07f
        val radius = cell * 0.2f
        for (k in tiles) {
            val x = ox + (k % l.w) * cell
            val y = oy + (k / l.w) * cell
            rect.set(x + inset, y + inset, x + cell - inset, y + cell - inset)
            canvas.drawRoundRect(rect, radius, radius, tilePaint)
        }

        // aiuto: alone pulsante sotto la freccia suggerita
        if (hintKey >= 0 && arrows.containsKey(hintKey)) {
            val t = (now - hintStart) / 1000f
            val x = ox + (hintKey % l.w) * cell
            val y = oy + (hintKey / l.w) * cell
            hintPaint.alpha = (70 + 60 * sin(t * 2 * PI * 1.2)).toInt().coerceIn(0, 255)
            rect.set(x + inset, y + inset, x + cell - inset, y + cell - inset)
            canvas.drawRoundRect(rect, radius, radius, hintPaint)
            animating = true
        }

        // frecce ferme
        for ((k, d) in arrows) {
            var dx = 0f
            var color = if (k == hintKey) Palette.ACCENT else Palette.INK
            if (k == shakeKey) {
                val t = (now - shakeStart) / 380f
                if (t < 1f) {
                    dx = (sin(t * PI * 6) * cell * 0.09f * (1 - t)).toFloat()
                    color = Palette.ERROR
                    animating = true
                } else {
                    shakeKey = -1
                }
            }
            val cx = ox + (k % l.w + 0.5f) * cell + dx
            val cy = oy + (k / l.w + 0.5f) * cell
            drawArrow(canvas, cx, cy, d, color, 255)
        }

        // frecce in uscita: scivolano via accelerando e svaniscono
        val travel = max(width, height).toFloat()
        val it = flying.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val t = (now - f.start) / FLY_MS
            if (t >= 1f) { it.remove(); continue }
            val dist = travel * t * t
            val cx = ox + (f.x + 0.5f) * cell + DX[f.dir] * dist
            val cy = oy + (f.y + 0.5f) * cell + DY[f.dir] * dist
            val alpha = if (t < 0.6f) 255 else (255 * (1 - (t - 0.6f) / 0.4f)).toInt()
            drawArrow(canvas, cx, cy, f.dir, Palette.ACCENT, alpha)
            animating = true
        }

        if (animating) postInvalidateOnAnimation()
    }

    private fun drawArrow(c: Canvas, cx: Float, cy: Float, dir: Int, color: Int, alpha: Int) {
        val s = cell
        shaftPaint.color = color
        shaftPaint.alpha = alpha
        shaftPaint.strokeWidth = s * 0.11f
        headPaint.color = color
        headPaint.alpha = alpha
        c.save()
        c.translate(cx, cy)
        c.rotate(dir * 90f) // disegnata verso l'alto, poi ruotata
        c.drawLine(0f, s * 0.28f, 0f, -s * 0.06f, shaftPaint)
        headPath.reset()
        headPath.moveTo(0f, -s * 0.32f)
        headPath.lineTo(-s * 0.19f, -s * 0.06f)
        headPath.lineTo(s * 0.19f, -s * 0.06f)
        headPath.close()
        c.drawPath(headPath, headPaint)
        c.restore()
    }

    companion object {
        private val DX = intArrayOf(0, 1, 0, -1)
        private val DY = intArrayOf(-1, 0, 1, 0)
        private const val FLY_MS = 420f
    }
}
