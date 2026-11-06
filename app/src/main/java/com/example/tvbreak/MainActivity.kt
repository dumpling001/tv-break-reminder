package com.example.tvbreak

import android.app.Activity
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.VideoView

class MainActivity : Activity() {
    // 声音增益，单位毫贝：800 约等于加大 8 分贝。觉得小就加大（建议不超过 1200），觉得破音就减小
    private val boostMb = 800

    private var video: VideoView? = null
    private var enhancer: LoudnessEnhancer? = null
    private var okCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val resId = resources.getIdentifier("rest_all", "raw", packageName)
        if (resId == 0) {
            beep()
        } else {
            val vv = VideoView(this)
            video = vv
            vv.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(1f, 1f)
                try {
                    val le = LoudnessEnhancer(mp.audioSessionId)
                    le.setTargetGain(boostMb)
                    le.setEnabled(true)
                    enhancer = le
                } catch (e: Exception) {
                }
            }
            vv.setOnErrorListener { _, _, _ ->
                beep()
                true
            }
            vv.setVideoURI(Uri.parse("android.resource://" + packageName + "/" + resId))
            root.addView(
                vv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
        setContentView(root)
        video?.start()
    }

    private fun beep() {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            .startTone(ToneGenerator.TONE_PROP_BEEP, 1000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event?.repeatCount == 0) {
                    okCount++
                    if (okCount >= 3) finish()
                }
                true
            }
            KeyEvent.KEYCODE_BACK -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        try {
            enhancer?.release()
        } catch (e: Exception) {
        }
        video?.stopPlayback()
        super.onDestroy()
    }
}