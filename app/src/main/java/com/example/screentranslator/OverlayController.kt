package com.example.screentranslator

import android.content.*
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.TextUtils
import android.view.*
import android.widget.*
import kotlin.math.roundToInt

class OverlayController(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val h = Handler(Looper.getMainLooper())
    private val sp = ctx.getSharedPreferences("overlay", Context.MODE_PRIVATE)

    // ---- Bubble (idle) ----
    private val bubble = ImageView(ctx).apply {
        setImageBitmap(dot(0xFF4CAF50.toInt()))
        setOnClickListener { togglePinned() }
        setOnTouchListener(DragListener(::applyBubblePos))
        contentDescription = "Screen Translator bubble"
    }
    private val bubbleLp = WindowManager.LayoutParams(
        dp(42), dp(42),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        x = sp.getInt("bubble_x", dp(12))
        y = sp.getInt("bubble_y", 0)
    }

    // ---- Views cho card phụ đề ----
    private val titleView = TextView(ctx).apply {
        setTextColor(Color.WHITE); textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
    }
    private val textView = TextView(ctx).apply {
        setTextColor(Color.WHITE); textSize = 15f
        maxLines = 4; ellipsize = TextUtils.TruncateAt.END
    }
    private val card = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(0xCC000000.toInt())
        setPadding(dp(12), dp(8), dp(12), dp(8))
        addView(titleView)
        addView(textView)
        alpha = 0.98f
        isClickable = false
        visibility = View.GONE
    }
    private val cardLp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = sp.getInt("card_y", dp(80))
    }

    private var pinned = false
    private var attached = false
    private var hideTask: Runnable? = null

    init { attach() }

    fun attach() {
        if (attached) return
        try {
            wm.addView(bubble, bubbleLp)
            wm.addView(card, cardLp)
            attached = true
        } catch (_: Exception) {}
    }

    fun detach() {
        if (!attached) return
        try { wm.removeView(card) } catch (_: Exception) {}
        try { wm.removeView(bubble) } catch (_: Exception) {}
        attached = false
    }

    // ---- API hiển thị ----
    fun showState(msg: String) {
        titleView.text = "Trạng thái"; textView.text = msg
        showCard(autoHideMs = 1500)
    }
    fun showWorking(msg: String = "Đang dịch…") {
        titleView.text = "Đang xử lý"; textView.text = msg
        showCard(autoHideMs = 0)
    }
    fun showResult(text: String, srcTag: String? = null) {
        titleView.text = if (srcTag == null) "Kết quả dịch" else "Dịch từ ${srcTag.uppercase()}"
        textView.text = text
        showCard(autoHideMs = 3500)
    }
    fun showText(text: String) = showResult(text)
    fun minimize() { card.visibility = View.GONE }

    private fun showCard(autoHideMs: Long) {
        card.visibility = View.VISIBLE
        bubble.visibility = View.VISIBLE
        hideTask?.let { h.removeCallbacks(it) }
        if (!pinned && autoHideMs > 0) {
            hideTask = Runnable { card.visibility = View.GONE }
            h.postDelayed(hideTask!!, autoHideMs)
        }
    }
    private fun togglePinned() {
        pinned = !pinned
        titleView.text = if (pinned) "Ghim phụ đề" else "Kết quả dịch"
        if (!pinned) {
            hideTask?.let { h.removeCallbacks(it) }
            hideTask = Runnable { card.visibility = View.GONE }
            h.postDelayed(hideTask!!, 1200)
        }
        bubble.setImageBitmap(dot(if (pinned) 0xFFE91E63.toInt() else 0xFF4CAF50.toInt()))
    }

    // ---- Helpers ----
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).roundToInt()
    private fun rounded(color: Int): Drawable = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(color)
    }
    private fun dot(color: Int): Bitmap {
        val s = dp(42)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = color
        c.drawCircle(s/2f, s/2f, s/2.4f, p)
        return bmp
    }
    private fun applyBubblePos(dx: Int, dy: Int) {
        bubbleLp.x += dx; bubbleLp.y += dy
        try { wm.updateViewLayout(bubble, bubbleLp) } catch (_: Exception) {}
        sp.edit().putInt("bubble_x", bubbleLp.x).putInt("bubble_y", bubbleLp.y).apply()
    }
    private class DragListener(val onMove: (dx: Int, dy: Int)->Unit) : View.OnTouchListener {
        var lx=0f; var ly=0f
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lx=e.rawX; ly=e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    onMove((e.rawX-lx).roundToInt(), (e.rawY-ly).roundToInt())
                    lx=e.rawX; ly=e.rawY
                }
            }
            return false
        }
    }
}
