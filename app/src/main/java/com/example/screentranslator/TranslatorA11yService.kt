package com.example.screentranslator

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

    override fun onServiceConnected() {
        overlay = OverlayController(this)
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return

        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<CharSequence>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            n.text?.let { if (it.toString().isNotBlank()) texts += it }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        val chunk = texts.joinToString("\n").take(600)
        if (chunk.isBlank()) return

        scope.launch {
            // 1) Nhận dạng ngôn ngữ nguồn
            val tag = runCatching { langId.identifyLanguage(chunk).await() }.getOrNull()
            val bcp47 = if (tag == null || tag == "und") "en" else tag
            val srcCode = TranslateLanguage.fromLanguageTag(bcp47) ?: TranslateLanguage.ENGLISH

            // 2) Lấy/khởi tạo translator cache theo ngôn ngữ nguồn
            val translator = translators.getOrPut(srcCode) {
                val opts = TranslatorOptions.Builder()
                    .setSourceLanguage(srcCode)
                    .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                    .build()
                Translation.getClient(opts)
            }

            // 3) Đảm bảo model có sẵn (wifi để lần đầu tải nhanh)
            runCatching {
                translator.downloadModelIfNeeded(
                    DownloadConditions.Builder().requireWifi().build()
                ).await()
            }

            // 4) Dịch và hiển thị
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
