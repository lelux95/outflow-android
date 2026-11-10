package com.airis.outflow

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Effetti sonori brevi del gioco. Si spengono dalle impostazioni. */
class Sounds(ctx: Context) {
    var enabled = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val tapId = pool.load(ctx, R.raw.tap, 1)
    private val outId = pool.load(ctx, R.raw.out, 1)
    private val errorId = pool.load(ctx, R.raw.error, 1)
    private val winId = pool.load(ctx, R.raw.win, 1)
    private val loseId = pool.load(ctx, R.raw.lose, 1)

    private fun play(id: Int, vol: Float) {
        if (enabled) pool.play(id, vol, vol, 1, 0, 1f)
    }

    fun tap() = play(tapId, 0.5f)
    fun out() = play(outId, 0.7f)
    fun error() = play(errorId, 0.8f)
    fun win() = play(winId, 0.8f)
    fun lose() = play(loseId, 0.8f)

    fun release() = pool.release()
}
