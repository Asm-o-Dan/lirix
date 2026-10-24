package com.lirix.app.feature.share.animation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class Mp4FrameEncoderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()





    @Test
    fun convertArgbToNv12_mapsColorChannelsCorrectly() {
        val width = 2
        val height = 2
        val pixels = IntArray(width * height)
        val yuv = ByteArray(width * height * 3 / 2)

        // 1. White pixel test
        val whiteBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        Mp4FrameEncoder.convertArgbToNv12(whiteBitmap, width, height, pixels, yuv)
        val whiteY = yuv[0].toInt() and 0xFF
        val whiteU = yuv[4].toInt() and 0xFF
        val whiteV = yuv[5].toInt() and 0xFF
        assertEquals(255, whiteY)
        assertEquals(128, whiteU)
        assertEquals(128, whiteV)
        whiteBitmap.recycle()

        // 2. Black pixel test
        val blackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        Mp4FrameEncoder.convertArgbToNv12(blackBitmap, width, height, pixels, yuv)
        val blackY = yuv[0].toInt() and 0xFF
        val blackU = yuv[4].toInt() and 0xFF
        val blackV = yuv[5].toInt() and 0xFF
        assertEquals(0, blackY)
        assertEquals(128, blackU)
        assertEquals(128, blackV)
        blackBitmap.recycle()
    }


}
