package org.fossify.phone.helpers

import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.STREAM_DTMF
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class ToneGeneratorHelper(context: Context, private val minToneLengthMs: Long) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val toneGenerator: ToneGenerator? = try {
        ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME)
    } catch (ignored: Exception) {
        null
    }

    private var toneStartTimeMs = 0L

    private fun isSilent(): Boolean {
        return audioManager.ringerMode in arrayOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)
    }

    fun startTone(char: Char) {
        toneStartTimeMs = System.currentTimeMillis()
        startTone(charToTone[char] ?: -1)
    }

    private fun startTone(tone: Int) {
        if (tone != -1 && !isSilent()) {
            toneGenerator?.startTone(tone)
        }
    }

    fun stopTone() {
        val timeSinceStartMs = System.currentTimeMillis() - toneStartTimeMs
        if (timeSinceStartMs < minToneLengthMs) {
            Handler(Looper.getMainLooper()).postDelayed({
                toneGenerator?.stopTone()
            }, minToneLengthMs - timeSinceStartMs)
        } else {
            toneGenerator?.stopTone()
        }
    }

    companion object {
        const val TONE_RELATIVE_VOLUME = 80 // The DTMF tone volume relative to other sounds in the stream
        const val DIAL_TONE_STREAM_TYPE = STREAM_DTMF

        private val charToTone = HashMap<Char, Int>().apply {
            put('0', ToneGenerator.TONE_DTMF_0)
            put('1', ToneGenerator.TONE_DTMF_1)
            put('2', ToneGenerator.TONE_DTMF_2)
            put('3', ToneGenerator.TONE_DTMF_3)
            put('4', ToneGenerator.TONE_DTMF_4)
            put('5', ToneGenerator.TONE_DTMF_5)
            put('6', ToneGenerator.TONE_DTMF_6)
            put('7', ToneGenerator.TONE_DTMF_7)
            put('8', ToneGenerator.TONE_DTMF_8)
            put('9', ToneGenerator.TONE_DTMF_9)
            put('#', ToneGenerator.TONE_DTMF_P)
            put('*', ToneGenerator.TONE_DTMF_S)
        }
    }
}
