package com.example.screentranslator

import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import com.google.mlkit.nl.translate.*
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class TranslatorA11yService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var overlay: OverlayController

    private val translators = mutableMapOf<String, Translator>()
    private val langId by lazy { LanguageIdentification.getClient() }

    private var enabled = true
    private var lastAt = 0L
    private var lastHash = 0
    private val triple = ArrayDeque<Long>()

    override fun onServiceConnected() {
        overlay = OverlayController(this)
        overlay.showState("Đang chạy… Nhấn bubble để ghim/thu nhỏ. Volume↑×3 bật/tắt.")
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.elapsedRealtime()
            triple.addLast(now)
            while (triple.isNotEmpty() && now - triple.first() > 900) triple.removeFirst()
            if (triple.size >= 3) {
                enabled = !enabled
                triple.clear()
                overlay.showState(if (enabled) "Dịch: BẬT" else "Dịch: TẮT")
                return true
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !enabled) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> Unit
            else -> return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastAt < 500) return
        lastAt = now

        val nodeText = collectNodeText(rootInActiveWindow).take(1500)

        scope.launch {
            var combined = nodeText

            if (ProjectionKeeper.hasPermission()) {
                ProjectionKeeper.grabBitmap()?.let { bmp ->
                    val ocr = runCatching { OcrHelper.ocr(bmp) }.getOrNull().orEmpty()
                    if (ocr.isNotBlank()) combined = (nodeText + "\n" + ocr).trim()
                }
            }

            if (combined.isBlank()) return@launch

            val tag = runCatching { langId.identifyLanguage(combined.take(500)).await() }.getOrNull()
            if (tag == "vi") { // không phải dịch
                // overlay.showState("VI — bỏ qua")
                return@launch
            }

            val h = combined.hashCode()
            if (h == lastHash) return@launch
            lastHash = h

            overlay.showWorking("Đang dịch…")

            val srcCode = TranslateLanguage.fromLanguageTag(tag ?: "en") ?: TranslateLanguage.ENGLISH
            val translator = translators.getOrPut(srcCode) {
                val opts = TranslatorOptions.Builder()
                    .setSourceLanguage(srcCode)
                    .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                    .build()
                Translation.getClient(opts)
            }
            runCatching { translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await() }

            val out = runCatching { translator.translate(combined.take(4000)).await() }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { overlay.showResult(out, tag) }
        }
    }

    private fun collectNodeText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val acc = ArrayList<String>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            n.text?.let { val s = it.toString(); if (s.isNotBlank()) acc += s }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return acc.joinToString("\n")
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        runCatching { langId.close() }
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
        scope.cancel()
    }
}
