package com.wickwirez.goingdark

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Proof pictures. When you mark an opt-out as sent, the page on screen is saved to your Gallery, in an album called
// Going Dark. The Texas Attorney General's complaint form takes picture uploads, so you can show what you sent.

const val PROOF_ALBUM = "Going Dark"

// "Spokeo 2026-10-10 10.41.jpg"
fun proofName(b: Broker, stamp: String): String {
    val name = b.company().replace(Regex("[^A-Za-z0-9 .&-]"), "").replace(Regex("\\s+"), " ").trim()
    return (if (name.isEmpty()) b.id else name) + " " + stamp + ".jpg"
}

// True when the picture was saved.
fun saveProof(ctx: Context, page: View, b: Broker): Boolean {
    if (Build.VERSION.SDK_INT < 29 || page.width <= 0 || page.height <= 0) return false
    return try {
        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        page.draw(canvas)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(Date())
        val v = ContentValues()
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, proofName(b, stamp))
        v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + PROOF_ALBUM)
        v.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val cr = ctx.contentResolver
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
        if (uri == null) {
            bmp.recycle()
            return false
        }
        val ok = cr.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) } ?: false
        bmp.recycle()
        if (!ok) {
            cr.delete(uri, null, null)
            return false
        }
        v.clear()
        v.put(MediaStore.MediaColumns.IS_PENDING, 0)
        cr.update(uri, v, null, null)
        true
    } catch (e: Exception) {
        false
    }
}
