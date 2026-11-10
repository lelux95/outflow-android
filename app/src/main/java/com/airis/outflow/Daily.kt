package com.airis.outflow

import kotlin.random.Random

/**
 * Sfida del giorno: un livello generato dalla data, identico su tutti i telefoni.
 * Stesso metodo del generatore dei livelli: griglia piena di frecce, poi i blocchi
 * senza uscita vengono riparati girando o spezzando le frecce, finche' il livello si risolve.
 */
object Daily {
    private val DX = intArrayOf(0, 1, 0, -1)
    private val DY = intArrayOf(-1, 0, 1, 0)

    const val W = 11
    const val H = 15
    const val LEVEL_ID = 1000

    fun today(): Long = java.time.LocalDate.now().toEpochDay()

    fun dateLabel(day: Long): String {
        val d = java.time.LocalDate.ofEpochDay(day)
        return String.format("%02d/%02d/%d", d.dayOfMonth, d.monthValue, d.year)
    }

    private val cache = HashMap<Long, Level>()

    fun level(day: Long): Level = cache.getOrPut(day) { generate(day) }

    private fun shuffled4(rng: Random): IntArray {
        val a = intArrayOf(0, 1, 2, 3)
        for (i in 3 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
        return a
    }

    private fun headDir(cells: List<Int>, w: Int, fallback: Int): Int {
        if (cells.size < 2) return fallback
        val a = cells[cells.size - 2]
        val b = cells[cells.size - 1]
        val dx = b % w - a % w
        val dy = b / w - a / w
        return when {
            dy < 0 -> 0
            dx > 0 -> 1
            dy > 0 -> 2
            else -> 3
        }
    }

    private fun rayClear(occ: IntArray, head: Int, d: Int, w: Int, h: Int): Boolean {
        var x = head % w + DX[d]
        var y = head / w + DY[d]
        while (x in 0 until w && y in 0 until h) {
            if (occ[y * w + x] >= 0) return false
            x += DX[d]; y += DY[d]
        }
        return true
    }

    private fun edgeOut(head: Int, d: Int, w: Int, h: Int): Boolean {
        val x = head % w + DX[d]
        val y = head / w + DY[d]
        return x !in 0 until w || y !in 0 until h
    }

    private fun generate(day: Long): Level {
        val w = W
        val h = H
        val rng = Random(day * 7919L + 17L)
        val minLen = 2
        val maxLen = 6
        val turnP = 0.5f

        // 1) copre tutta la griglia con cammini casuali
        val occ = IntArray(w * h) { -1 }
        val paths = ArrayList<MutableList<Int>>()
        val order = (0 until w * h).toMutableList()
        order.shuffle(rng)
        for (start in order) {
            if (occ[start] >= 0) continue
            val target = rng.nextInt(minLen, maxLen + 1)
            val pi = paths.size
            val cells = mutableListOf(start)
            occ[start] = pi
            var cur = rng.nextInt(4)
            while (cells.size < target) {
                val cand: IntArray = when {
                    cells.size == 1 -> shuffled4(rng)
                    rng.nextFloat() < turnP ->
                        if (rng.nextBoolean()) intArrayOf((cur + 1) % 4, (cur + 3) % 4, cur)
                        else intArrayOf((cur + 3) % 4, (cur + 1) % 4, cur)
                    else -> intArrayOf(cur)
                }
                var moved = false
                for (nd in cand) {
                    val last = cells[cells.size - 1]
                    val x = last % w + DX[nd]
                    val y = last / w + DY[nd]
                    if (x in 0 until w && y in 0 until h && occ[y * w + x] < 0) {
                        cur = nd
                        cells.add(y * w + x)
                        occ[y * w + x] = pi
                        moved = true
                        break
                    }
                }
                if (!moved) break
            }
            paths.add(cells)
        }

        // 2) le frecce da 1 casella si uniscono a un vicino
        var changed = true
        while (changed) {
            changed = false
            for (i in paths.indices) {
                if (paths[i].size != 1) continue
                val c = paths[i][0]
                for (d in shuffled4(rng)) {
                    val x = c % w + DX[d]
                    val y = c / w + DY[d]
                    if (x !in 0 until w || y !in 0 until h) continue
                    val nb = y * w + x
                    val j = occ[nb]
                    if (j < 0 || j == i) continue
                    val pj = paths[j]
                    if (pj.isEmpty() || pj.size >= maxLen + 1) continue
                    when (nb) {
                        pj[0] -> pj.add(0, c)
                        pj[pj.size - 1] -> pj.add(c)
                        else -> continue
                    }
                    occ[c] = j
                    paths[i] = mutableListOf()
                    changed = true
                    break
                }
            }
        }

        // 3) verso delle frecce
        val cellsL = ArrayList<MutableList<Int>>()
        val dirs = ArrayList<Int>()
        for (p in paths) {
            if (p.isEmpty()) continue
            val c = if (p.size >= 2 && rng.nextBoolean()) p.asReversed().toMutableList() else p
            cellsL.add(c)
            dirs.add(headDir(c, w, rng.nextInt(4)))
        }
        for (i in cellsL.indices) {
            if (cellsL[i].size <= 2 && edgeOut(cellsL[i][cellsL[i].size - 1], dirs[i], w, h)) {
                if (cellsL[i].size >= 2) {
                    cellsL[i] = cellsL[i].asReversed().toMutableList()
                    dirs[i] = headDir(cellsL[i], w, 0)
                } else dirs[i] = (dirs[i] + 2) % 4
            }
        }

        // 4) riparazione dei blocchi senza uscita
        repeat(8000) {
            val o = IntArray(w * h) { -1 }
            for (i in cellsL.indices) for (c in cellsL[i]) o[c] = i
            val alive = BooleanArray(cellsL.size) { true }
            var left = cellsL.size
            while (true) {
                val free = cellsL.indices.filter { alive[it] && rayClear(o, cellsL[it][cellsL[it].size - 1], dirs[it], w, h) }
                if (free.isEmpty()) break
                for (i in free) {
                    alive[i] = false
                    left--
                    for (c in cellsL[i]) o[c] = -1
                }
            }
            if (left == 0) return build(day, cellsL, dirs, w, h)

            val stuck = cellsL.indices.filter { alive[it] }.shuffled(rng)
            for (i in stuck) {
                if (cellsL[i].size >= 2) {
                    val rc = cellsL[i].asReversed().toMutableList()
                    val rd = headDir(rc, w, 0)
                    if (rayClear(o, rc[rc.size - 1], rd, w, h)) {
                        cellsL[i] = rc
                        dirs[i] = rd
                        return@repeat
                    }
                }
            }
            for (i in stuck) {
                if (cellsL[i].size == 1) {
                    for (nd in shuffled4(rng)) {
                        if (rayClear(o, cellsL[i][0], nd, w, h)) {
                            dirs[i] = nd
                            return@repeat
                        }
                    }
                }
            }
            // spezza: la cella rimasta piu' estrema verso un lato ha sempre la strada libera
            val side = rng.nextInt(4)
            var best = -1
            var bestKey = Int.MAX_VALUE
            for (c in 0 until w * h) {
                if (o[c] < 0) continue
                val k = when (side) {
                    0 -> c / w
                    1 -> -(c % w)
                    2 -> -(c / w)
                    else -> c % w
                }
                if (k < bestKey) { bestKey = k; best = c }
            }
            val i = o[best]
            val cells = cellsL[i].toMutableList()
            val d = dirs[i]
            val k = cells.indexOf(best)
            val before = cells.subList(0, k).toMutableList()
            val after = cells.subList(k + 1, cells.size).toMutableList()
            val upto = cells.subList(0, k + 1).toMutableList()
            if (k >= 1 && headDir(upto, w, 0) == side) {
                cellsL[i] = upto
                dirs[i] = side
                if (after.isNotEmpty()) {
                    cellsL.add(after); dirs.add(if (after.size >= 2) headDir(after, w, d) else d)
                }
            } else {
                cellsL[i] = mutableListOf(best)
                dirs[i] = side
                if (before.isNotEmpty()) {
                    cellsL.add(before); dirs.add(if (before.size >= 2) headDir(before, w, 0) else headDir(upto, w, 0))
                }
                if (after.isNotEmpty()) {
                    cellsL.add(after); dirs.add(if (after.size >= 2) headDir(after, w, d) else d)
                }
            }
        }
        // in pratica non succede mai: ripiego su un livello normale
        return build(day, cellsL, dirs, w, h)
    }

    private fun build(day: Long, cellsL: List<List<Int>>, dirs: List<Int>, w: Int, h: Int): Level {
        val arrows = cellsL.indices.map { i ->
            val c = cellsL[i]
            Arrow(IntArray(c.size) { c[it] % w }, IntArray(c.size) { c[it] / w }, dirs[i])
        }
        return Level(LEVEL_ID, "daily-$day", w, h, false, 0, arrows)
    }
}
