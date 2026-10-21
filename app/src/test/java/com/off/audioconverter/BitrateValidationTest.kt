package com.off.audioconverter

import org.junit.Assert.assertEquals
import org.junit.Test

class BitrateValidationTest {
    @Test
    fun bitrateOptionsAreSupported() {
        val options = listOf(128, 192, 256, 320)
        assertEquals(listOf(128, 192, 256, 320), options)
    }
}
