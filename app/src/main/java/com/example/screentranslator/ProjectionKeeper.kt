package com.example.screentranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection

object ProjectionKeeper {
    @Volatile private var projection: MediaProjection? = null
    @Volatile private var reader: ImageReader? = null
    @Volatile private var vDisplay: VirtualDisplay? = null

    fun init(ctx: Context, proj: MediaProjection) {
        projection = proj
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val dpi = dm.densityDpi
        reader?.close(); vDisplay?.release()
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        vDisplay = proj.createVirtualDisplay(
            "screen-translator",
            w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, null
        )
        proj.registerCallback(object: MediaProjection.Callback() {
            override fun onStop() {
                reader?.close(); vDisplay?.release()
                reader = null; vDisplay = null; projection = null
            }
        }, null)
    }

    fun hasPermission(): Boolean = projection != null

    fun grabBitmap(): Bitmap? {
        val img = reader?.acquireLatestImage() ?: return null
        try {
            val plane = img.planes[0]
            val buf = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * img.width
            val bmp = Bitmap.createBitmap(
                img.width + rowPadding / pixelStride,
                img.height, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buf)
            return Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
        } finally {
            img.close()
        }
    }
}
