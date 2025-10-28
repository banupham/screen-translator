package com.example.screentranslator

import android.app.*
import android.content.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build

class ProjectionFgService : Service() {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP  = "stop"
        const val EXTRA_CODE   = "code"
        const val EXTRA_DATA   = "data"
        private const val CH_ID = "screen_translator_projection"
        private const val NOTI_ID = 11
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CH_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CH_ID, "Screen capture", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Tạo builder cho cả 2 nhánh, rồi build sau
        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH_ID)
            else Notification.Builder(this)

        val noti: Notification = builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Screen Translator")
            .setContentText("Đang chụp màn hình để OCR")
            .setOngoing(true)
            .build()

        startForeground(NOTI_ID, noti)

        when (intent?.action) {
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (code == Activity.RESULT_OK && data != null) {
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val proj: MediaProjection = mpm.getMediaProjection(code, data)
                    ProjectionKeeper.init(this, proj)
                    proj.registerCallback(object: MediaProjection.Callback() {
                        override fun onStop() {
                            stopForeground(true); stopSelf()
                        }
                    }, null)
                } else {
                    stopForeground(true); stopSelf()
                }
            }
            ACTION_STOP -> { stopForeground(true); stopSelf() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null
}
