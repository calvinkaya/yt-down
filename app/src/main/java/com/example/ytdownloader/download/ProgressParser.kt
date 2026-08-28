package com.example.ytdownloader.download

/**
 * Byte-for-byte logic port of `parseProgressLine` from ytproject/src/main/downloader.js.
 * Pure (no Android APIs) so it can be unit-tested on the JVM.
 */
object ProgressParser {
    private val RE_DB_TB = Regex("""^download:PROGRESS:(\d+):(\d+)""")
    private val RE_DB_NA = Regex("""^download:PROGRESS:(\d+):NA""")
    private val RE_PCT = Regex("""\[download\]\s+(\d+(?:\.\d+)?)%""")

    /**
     * Parse one yt-dlp output line into a percent (0-100) or null if it carries
     * no usable progress.
     */
    fun parse(line: String?): Double? {
        val s = line ?: return null

        RE_DB_TB.find(s)?.let { m ->
            val db = m.groupValues[1].toDouble()
            val tb = m.groupValues[2].toDouble()
            return if (tb > 0) minOf(100.0, (db / tb) * 100.0) else 0.0
        }

        // PROGRESS:db:NA — unknown total, keep last percent (return null).
        if (RE_DB_NA.containsMatchIn(s)) return null

        RE_PCT.find(s)?.let { m ->
            val p = m.groupValues[1].toDouble()
            return minOf(100.0, p)
        }
        return null
    }
}
