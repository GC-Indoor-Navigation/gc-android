package com.gc.collector.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAlertFeedbackPolicyTest {
    @Test
    fun infoDoesNotVibrateOrPlaySound() {
        val policy = PhoneAlertFeedbackPolicyMapper.fromSeverity(ProcessingAlertSeverity.Info)

        assertFalse(policy.vibrate)
        assertArrayEquals(longArrayOf(), policy.vibrationPatternMs)
        assertFalse(policy.playSound)
    }

    @Test
    fun warningUsesShortVibrationOnly() {
        val policy = PhoneAlertFeedbackPolicyMapper.fromSeverity(ProcessingAlertSeverity.Warning)

        assertTrue(policy.vibrate)
        assertArrayEquals(longArrayOf(0L, 450L), policy.vibrationPatternMs)
        assertFalse(policy.playSound)
    }

    @Test
    fun dangerUsesStrongerVibrationAndSound() {
        val policy = PhoneAlertFeedbackPolicyMapper.fromSeverity(ProcessingAlertSeverity.Danger)

        assertTrue(policy.vibrate)
        assertArrayEquals(longArrayOf(0L, 350L, 120L, 450L, 120L, 650L), policy.vibrationPatternMs)
        assertTrue(policy.playSound)
    }

    @Test
    fun policyEqualityUsesVibrationPatternContent() {
        val first = PhoneAlertFeedbackPolicy(
            vibrate = true,
            vibrationPatternMs = longArrayOf(0L, 180L),
            playSound = false,
        )
        val second = PhoneAlertFeedbackPolicy(
            vibrate = true,
            vibrationPatternMs = longArrayOf(0L, 180L),
            playSound = false,
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
