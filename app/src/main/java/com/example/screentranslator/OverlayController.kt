package com.example.screentranslator

import android.content.Context
import android.graphics.PixelFormat
import android.view.*
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class OverlayController(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 120 }

    private val tv by lazy {
        TextView(ctx).apply {
            textSize = 16f
            setPadding(22, 14, 22, 14)
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(Color.WHITE)
        }
    }
    private var added = false

    fun showText(s: String) {
        tv.text = s
        if (!added) { wm.addView(tv, params); added = true }
    }
    fun hide() { if (added) { wm.removeView(tv); added = false } }
}
