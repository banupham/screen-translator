package com.example.screentranslator

import android.app.Activity
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.*

class CaptureActivity : Activity() {
    companion object { private const val REQ = 1001 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val svc = Intent(this, ProjectionFgService::class.java).apply {
            action = ProjectionFgService.ACTION_START
            putExtra(ProjectionFgService.EXTRA_CODE, resultCode)
            putExtra(ProjectionFgService.EXTRA_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
        finish()
    }
}
