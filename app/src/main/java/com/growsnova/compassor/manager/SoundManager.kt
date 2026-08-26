package com.growsnova.compassor.manager

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundManager @Inject constructor() {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            Log.e("SoundManager", "Failed to initialize ToneGenerator", e)
        }
    }

    fun playArrivalTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
        } catch (e: Exception) {
            Log.e("SoundManager", "Failed to play tone", e)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e("SoundManager", "Failed to release ToneGenerator", e)
        }
    }
}
