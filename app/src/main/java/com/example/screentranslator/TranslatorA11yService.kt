package com.example.screentranslator

import android.os.SystemClock
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

    private var lastShownAt = 0L
    private var lastHash = 0

    override fun onServiceConnected() {
        overlay = OverlayController(this)
        overlay.showText("Screen Translator: Đang chạy…")
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> Unit
            else -> return
        }

        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<CharSequence>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            n.text?.let { if (it.isNotBlank()) texts += it }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        val chunk = texts.joinToString("\n").take(600)

        if (chunk.isBlank()) {
            // chỉ nhắc nhẹ 2 giây/lần để biết app đang hoạt động
            val now = SystemClock.elapsedRealtime()
            if (now - lastShownAt > 2000) {
                overlay.showText("Không thấy chữ trên màn hình (thử cuộn/trang khác)")
                lastShownAt = now
            }
            return
        }

        // Debounce nội dung lặp lại
        val h = chunk.hashCode()
        if (h == lastHash) return
        lastHash = h

        scope.launch {
            val tag = runCatching { langId.identifyLanguage(chunk).await() }.getOrNull()
            val bcp47 = if (tag == null || tag == "und") "en" else tag
            val srcCode = TranslateLanguage.fromLanguageTag(bcp47) ?: TranslateLanguage.ENGLISH

            val translator = translators.getOrPut(srcCode) {
                val opts = TranslatorOptions.Builder()
                    .setSourceLanguage(srcCode)
                    .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                    .build()
                Translation.getClient(opts)
            }

            // Cho phép tải qua 4G hoặc Wi-Fi
            val conds = DownloadConditions.Builder().build()
            val dl = runCatching { translator.downloadModelIfNeeded(conds).await() }.exceptionOrNull()
            if (dl != null) {
                withContext(Dispatchers.Main) { overlay.showText("Đang tải model dịch…") }
                return@launch
            }

            val out = runCatching { translator.translate(chunk).await() }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { overlay.showText(out) }
        }
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
