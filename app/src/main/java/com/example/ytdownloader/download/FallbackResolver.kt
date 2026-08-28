package com.example.ytdownloader.download

import kotlin.math.abs

/**
 * Byte-for-byte logic port of `resolveTarget` from ytproject/src/main/downloader.js.
 * Pure (no Android APIs) so it can be unit-tested on the JVM.
 */
object FallbackResolver {

    data class Decision(
        val target: Int? = null,
        val auto: Boolean = false,
        val ask: List<Int>? = null,
        val closest: Int? = null,
    )

    /**
     * @param H preferred height (number) or "best".
     * @param available available heights.
     * @return [Decision] with either an auto [target] or an [ask] prompt.
     */
    fun resolveTarget(H: Any?, available: List<Int>): Decision {
        val set = available.distinct().filter { it > 0 }.sorted()
        if (set.isEmpty()) return Decision(target = null)
        if (H == "best") return Decision(target = set.last(), auto = true)

        val hn: Int? = when (H) {
            is Number -> H.toInt()
            is String -> H.toIntOrNull()
            else -> null
        }
        if (hn == null) return Decision(target = null)

        if (set.contains(hn)) return Decision(target = hn, auto = true)

        val lower = set.filter { it < hn }
        val higher = set.filter { it > hn }
        val lo = lower.lastOrNull()
        val hi = higher.firstOrNull()

        if (hi == null) return Decision(target = lo, auto = true) // DOWNGRADE → auto
        if (lo == null) return Decision(ask = listOf(hi), closest = hi) // UPGRADE → ask

        // Both exist → ask, closest first, listing both as options.
        val options = if (abs(hn - lo) <= abs(hi - hn)) listOf(lo, hi) else listOf(hi, lo)
        return Decision(ask = options, closest = options[0])
    }
}
