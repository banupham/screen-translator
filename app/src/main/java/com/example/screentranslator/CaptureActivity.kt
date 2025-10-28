package com.example.screentranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

class CaptureActivity : Activity() {
    companion object { private const val REQ = 1001 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj: MediaProjection = mpm.getMediaProjection(resultCode, data)
            ProjectionKeeper.init(this, proj)
            Toast.makeText(this, "Đã cấp quyền chụp màn hình", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Chưa cấp quyền chụp màn hình", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
