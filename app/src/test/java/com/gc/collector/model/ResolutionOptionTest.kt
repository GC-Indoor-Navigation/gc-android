package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionOptionTest {
    @Test
    fun defaultResolutionRemainsHd() {
        assertEquals(ResolutionOption.HD, CameraCaptureSettings().resolution)
    }

    @Test
    fun resolutionOptionsHaveUniqueDimensions() {
        val dimensions = ResolutionOption.commonOptions.map { option -> option.width to option.height }

        assertEquals(dimensions.size, dimensions.toSet().size)
    }

    @Test
    fun resolutionOptionsCoverCommonLowMidAndHighProfiles() {
        val dimensions = ResolutionOption.commonOptions.map { option -> option.width to option.height }.toSet()

        assertTrue(320 to 240 in dimensions)
        assertTrue(1280 to 720 in dimensions)
        assertTrue(1920 to 1080 in dimensions)
        assertTrue(3840 to 2160 in dimensions)
    }

    @Test
    fun createsDynamicOptionForNonPresetDimensions() {
        val option = ResolutionOption.fromDimensions(width = 1440, height = 1080)

        assertEquals("1440 x 1080", option.label)
        assertEquals(1440, option.width)
        assertEquals(1080, option.height)
    }

    @Test
    fun normalizesPortraitDimensions() {
        val option = ResolutionOption.fromDimensions(width = 720, height = 1280)

        assertEquals(ResolutionOption.HD, option)
    }
}
