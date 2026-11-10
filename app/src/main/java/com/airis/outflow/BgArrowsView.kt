package com.airis.outflow

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View
import kotlin.random.Random

/** Sfondo della home: frecce colorate e tenui che corrono sulla griglia, curvando ogni tanto. */
class BgArrowsView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val grid = 44f * density
    private val rnd = Random(SystemClock.uptimeMillis())

    private class Runner(
        val nodesX: ArrayList<Int>,   // nodi visitati sulla griglia (in celle), dal piu' vecchio
        val nodesY: ArrayList<Int>,
        var dir: Int,
        var f: Float,                 // avanzamento verso il nodo successivo, 0..1
        val speed: Float,             // celle al secondo
        val len: Int,                 // lunghezza in celle
        val color: Int,
    )

    private val runners = ArrayList<Runner>()
    private var last = 0L
    private var running = false

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 7f * density
        pathEffect = CornerPathEffect(14f * density)
    }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val tip = Path()

    fun resume() {
        if (running) return
        running = true
        last = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    fun pause() {
        running = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resume()
    }

    override fun onDetachedFromWindow() {
        pause()
        super.onDetachedFromWindow()
    }

    private fun cols() = (width / grid).toInt() + 2
    private fun rows() = (height / grid).toInt() + 2

    private fun spawn(scatter: Boolean): Runner {
        val c = cols()
        val r = rows()
        val side = rnd.nextInt(4)
        var x: Int
        var y: Int
        val d: Int
        when (side) {
            0 -> { x = rnd.nextInt(c); y = r + 1; d = 0 }        // dal basso verso l'alto
            1 -> { x = -2; y = rnd.nextInt(r); d = 1 }           // da sinistra
            2 -> { x = rnd.nextInt(c); y = -2; d = 2 }           // dall'alto
            else -> { x = c + 1; y = rnd.nextInt(r); d = 3 }     // da destra
        }
        if (scatter) { x = rnd.nextInt(c); y = rnd.nextInt(r) }
        val len = 2 + rnd.nextInt(4)
        val nx = arrayListOf(x)
        val ny = arrayListOf(y)
        return Runner(nx, ny, d, 0f, 0.9f + rnd.nextFloat() * 1.1f, len,
            Palette.ARROWS[rnd.nextInt(Palette.ARROWS.size)])
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        runners.clear()
        repeat(9) { runners.add(spawn(scatter = true)) }
    }

    private fun step(r: Runner, dt: Float) {
        r.f += r.speed * dt
        while (r.f >= 1f) {
            r.f -= 1f
            r.nodesX.add(r.nodesX.last() + DX[r.dir])
            r.nodesY.add(r.nodesY.last() + DY[r.dir])
            // ogni tanto curva (mai all'indietro)
            if (rnd.nextFloat() < 0.22f) r.dir = (r.dir + if (rnd.nextBoolean()) 1 else 3) % 4
            while (r.nodesX.size > r.len + 2) { r.nodesX.removeAt(0); r.nodesY.removeAt(0) }
        }
    }

    private fun offscreen(r: Runner): Boolean {
        val c = cols()
        val rr = rows()
        return r.nodesX.all { it < -3 || it > c + 2 } || r.nodesY.all { it < -3 || it > rr + 2 }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dt = ((now - last) / 1000f).coerceIn(0f, 0.05f)
        last = now

        for (i in runners.indices) {
            val r = runners[i]
            if (running) step(r, dt)
            if (offscreen(r) && r.nodesX.size > r.len) runners[i] = spawn(scatter = false)
            draw(canvas, runners[i])
        }
        if (running && isShown) postInvalidateOnAnimation()
    }

    private fun draw(c: Canvas, r: Runner) {
        val n = r.nodesX.size
        val hx = (r.nodesX[n - 1] + DX[r.dir] * r.f) * grid
        val hy = (r.nodesY[n - 1] + DY[r.dir] * r.f) * grid
        path.reset()
        path.moveTo(hx, hy)
        var remaining = r.len.toFloat()
        var px = hx
        var py = hy
        var k = n - 1
        // la testa avanza di f dall'ultimo nodo: quel tratto conta nella lunghezza
        remaining -= r.f
        path.lineTo(r.nodesX[k] * grid, r.nodesY[k] * grid)
        px = r.nodesX[k] * grid; py = r.nodesY[k] * grid
        while (k > 0 && remaining > 0f) {
            val qx = r.nodesX[k - 1] * grid
            val qy = r.nodesY[k - 1] * grid
            if (remaining >= 1f) {
                path.lineTo(qx, qy)
                remaining -= 1f
            } else {
                path.lineTo(px + (qx - px) * remaining, py + (qy - py) * remaining)
                remaining = 0f
            }
            px = qx; py = qy
            k--
        }
        stroke.color = r.color
        stroke.alpha = 64
        head.color = r.color
        head.alpha = 64
        c.drawPath(path, stroke)
        val len = 15f * density
        val half = 10f * density
        val d = r.dir
        tip.reset()
        tip.moveTo(hx + DX[d] * len, hy + DY[d] * len)
        tip.lineTo(hx - DY[d] * half, hy + DX[d] * half)
        tip.lineTo(hx + DY[d] * half, hy - DX[d] * half)
        tip.close()
        c.drawPath(tip, head)
    }

    companion object {
        private val DX = intArrayOf(0, 1, 0, -1)
        private val DY = intArrayOf(-1, 0, 1, 0)
    }
}
