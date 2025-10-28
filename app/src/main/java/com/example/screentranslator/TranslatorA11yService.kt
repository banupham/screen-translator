package com.example.screentranslator

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import com.google.mlkit.nl.translate.*
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class TranslatorA11yService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var overlay: OverlayController
    private var translator: Translator? = null

    override fun onServiceConnected() {
        overlay = OverlayController(this)

        val opts = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.AUTO)
            .setTargetLanguage(TranslateLanguage.VIETNAMESE)
            .build()
        translator = Translation.getClient(opts).also {
            it.downloadModelIfNeeded(
                DownloadConditions.Builder().requireWifi().build()
            )
        }

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
            n.text?.let { if (it.isNotBlank()) texts += it }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        val chunk = texts.joinToString("\n").take(600)
        if (chunk.isBlank()) return

        scope.launch {
            val out = runCatching { translator?.translate(chunk)?.await() }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { overlay.showText(out) }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
        scope.cancel()
    }
}
