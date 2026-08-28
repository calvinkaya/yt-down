package com.example.ytdownloader

import com.example.ytdownloader.download.ProgressParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mirrors the parseProgressLine cases in ytproject/scripts/test-fallback.js.
 */
class ProgressParserTest {

    @Test
    fun dbTb_small() {
        assertEquals(0, Math.round(ProgressParser.parse("download:PROGRESS:1024:5685561")!!).toInt())
    }

    @Test
    fun dbTb_fifty() {
        assertEquals(50, Math.round(ProgressParser.parse("download:PROGRESS:500:1000")!!).toInt())
    }

    @Test
    fun dbNa_null() {
        assertNull(ProgressParser.parse("download:PROGRESS:1024:NA"))
    }

    @Test
    fun fallbackPct() {
        assertEquals(46, Math.round(ProgressParser.parse("[download]  45.6% of 10MiB at 1MiB/s")!!).toInt())
    }

    @Test
    fun garbage_null() {
        assertNull(ProgressParser.parse("[youtube] Extracting URL: x"))
    }

    @Test
    fun capAt100() {
        assertEquals(100.0, ProgressParser.parse("download:PROGRESS:2000:1000")!!, 0.0)
    }

    @Test
    fun nullLine_null() {
        assertNull(ProgressParser.parse(null))
    }
}
