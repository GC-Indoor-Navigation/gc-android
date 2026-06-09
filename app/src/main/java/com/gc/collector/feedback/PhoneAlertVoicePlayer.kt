package com.gc.collector.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

fun interface PhoneAlertVoicePlayer {
    fun speak(message: String)
}

object NoOpPhoneAlertVoicePlayer : PhoneAlertVoicePlayer {
    override fun speak(message: String) = Unit
}

class AndroidPhoneAlertVoicePlayer(
    context: Context,
) : PhoneAlertVoicePlayer {
    private val appContext = context.applicationContext
    private var ready = false
    private var textToSpeech: TextToSpeech? = null

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                runCatching {
                    textToSpeech?.language = Locale.US
                }.onFailure {
                    ready = false
                }
            }
        }
    }

    override fun speak(message: String) {
        if (!ready || message.isBlank()) return
        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "alert-${System.currentTimeMillis()}",
        )
    }
}
