package com.example.ytdownloader

import com.example.ytdownloader.download.FallbackResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mirrors the resolveTarget cases in ytproject/scripts/test-fallback.js.
 */
class FallbackResolverTest {

    @Test
    fun example1_4kAsked_1080Max_auto1080() {
        val d = FallbackResolver.resolveTarget(2160, listOf(360, 720, 1080))
        assertEquals(1080, d.target)
        assertEquals(true, d.auto)
    }

    @Test
    fun example2_120Asked_240Min_ask240() {
        val d = FallbackResolver.resolveTarget(120, listOf(240, 360, 720))
        assertEquals(listOf(240), d.ask)
        assertEquals(240, d.closest)
    }

    @Test
    fun exactMatch_auto() {
        val d = FallbackResolver.resolveTarget(1080, listOf(360, 720, 1080, 1440))
        assertEquals(1080, d.target)
        assertEquals(true, d.auto)
    }

    @Test
    fun best_maxAvailable() {
        val d = FallbackResolver.resolveTarget("best", listOf(360, 720, 1080))
        assertEquals(1080, d.target)
        assertEquals(true, d.auto)
    }

    @Test
    fun betweenBoth_askClosestFirst() {
        val d = FallbackResolver.resolveTarget(480, listOf(360, 720, 1080))
        assertEquals(listOf(360, 720), d.ask)
        assertEquals(360, d.closest)
    }

    @Test
    fun tie720_1440_around1080() {
        val d = FallbackResolver.resolveTarget(1080, listOf(720, 1440))
        assertEquals(listOf(720, 1440), d.ask)
        assertEquals(720, d.closest)
    }

    @Test
    fun noFormats_targetNull() {
        val d = FallbackResolver.resolveTarget(1080, emptyList())
        assertNull(d.target)
    }

    @Test
    fun duplicatesCollapsed() {
        val d = FallbackResolver.resolveTarget(720, listOf(720, 720, 1080))
        assertEquals(720, d.target)
        assertEquals(true, d.auto)
    }

    @Test
    fun nonNumericPreferred_targetNull() {
        val d = FallbackResolver.resolveTarget("garbage", listOf(720, 1080))
        assertNull(d.target)
    }
}
