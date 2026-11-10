package com.airis.outflow

import android.app.Activity
import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/** Espone in sola lettura le immagini da condividere (cartella cache/share). */
class ShareProvider : ContentProvider() {
    override fun onCreate() = true

    private fun fileFor(uri: Uri): File? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        if (name.contains("/") || name.contains("..")) return null
        val f = File(File(ctx.cacheDir, "share"), name)
        return if (f.exists()) f else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = fileFor(uri) ?: return null
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = fileFor(uri) ?: return null
        val c = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        c.addRow(arrayOf<Any>(f.name, f.length()))
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

/** Crea l'immagine del risultato e apre il menu Condividi di Android. */
object ShareCard {

    fun share(activity: Activity, title: String, subtitle: String, stars: Int, time: String?, name: String) {
        val size = 1080
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Palette.BG)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // frecce decorative sullo sfondo
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 18f
        val deco = listOf(floatArrayOf(90f, 140f, 360f, 140f), floatArrayOf(990f, 260f, 990f, 520f),
            floatArrayOf(120f, 900f, 120f, 680f), floatArrayOf(700f, 960f, 960f, 960f))
        for ((i, l) in deco.withIndex()) {
            p.color = Palette.ARROWS[(i * 2) % Palette.ARROWS.size]
            p.alpha = 70
            c.drawLine(l[0], l[1], l[2], l[3], p)
        }
        p.style = Paint.Style.FILL
        p.alpha = 255

        // logo colorato
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 130f
        val word = "Outflow"
        var x = (size - p.measureText(word)) / 2f
        for ((i, ch) in word.withIndex()) {
            p.color = Palette.ARROWS[(i * 3) % Palette.ARROWS.size]
            c.drawText(ch.toString(), x, 300f, p)
            x += p.measureText(ch.toString())
        }

        fun centered(text: String, y: Float, sizePx: Float, color: Int, bold: Boolean = false) {
            p.textSize = sizePx
            p.color = color
            p.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            c.drawText(text, (size - p.measureText(text)) / 2f, y, p)
        }

        centered(title, 450f, 78f, Palette.INK, bold = true)
        if (subtitle.isNotBlank()) centered(subtitle, 525f, 46f, Palette.QUIET)
        centered("★".repeat(stars) + "☆".repeat((3 - stars).coerceAtLeast(0)), 680f, 130f, Palette.STAR)
        if (time != null) {
            val r = RectF(330f, 730f, 750f, 840f)
            p.color = Palette.PRIMARY
            c.drawRoundRect(r, 55f, 55f, p)
            centered("⏱ $time", 805f, 64f, android.graphics.Color.WHITE, bold = true)
        }
        val footer = if (name.isNotBlank()) S.f("share_by", name) else S.t("share_beat")
        centered(footer, 960f, 44f, Palette.QUIET)

        val dir = File(activity.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "outflow.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val uri = Uri.parse("content://${activity.packageName}.share/outflow.png")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, S.t("share_text"))
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, S.t("share")))
    }
}
