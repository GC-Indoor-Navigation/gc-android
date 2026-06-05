package com.gc.collector.feedback

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.gc.collector.model.PhoneAlertFeedbackPolicy

fun interface PhoneAlertFeedbackPlayer {
    fun play(policy: PhoneAlertFeedbackPolicy)
}

object NoOpPhoneAlertFeedbackPlayer : PhoneAlertFeedbackPlayer {
    override fun play(policy: PhoneAlertFeedbackPolicy) = Unit
}

class AndroidPhoneAlertFeedbackPlayer(
    context: Context,
) : PhoneAlertFeedbackPlayer {
    private val appContext = context.applicationContext

    override fun play(policy: PhoneAlertFeedbackPolicy) {
        if (policy.vibrate && policy.vibrationPatternMs.isNotEmpty()) {
            vibrate(policy.vibrationPatternMs)
        }
        if (policy.playSound) {
            playNotificationSound()
        }
    }

    private fun vibrate(patternMs: LongArray) {
        val vibrator = getVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(patternMs, -1),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(patternMs, -1)
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext
                .getSystemService(VibratorManager::class.java)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun playNotificationSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        RingtoneManager.getRingtone(appContext, uri)?.play()
    }
}
