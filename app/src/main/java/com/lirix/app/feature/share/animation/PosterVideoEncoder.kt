package com.lirix.app.feature.share.animation

import android.graphics.Bitmap

interface PosterVideoEncoder {
    fun encodeFrame(bitmap: Bitmap)
    fun finish()
    fun abort()
}
