package com.example.ytdownloader.bridge

/**
 * Video-ID extraction. [extract] is a faithful port of the VIDEO_URL_RE regex in
 * ytproject/src/main/main.js:69 (loose match used by will-navigate/did-navigate
 * fallbacks). [isYoutubeHost] is the stricter hostname check used for deciding
 * whether a URL is a YouTube navigation vs. an external link.
 */
object VideoId {

    private val RE = Regex(
        """(?:youtube\.com/(?:watch\?[^#]*\bv=|shorts/|live/)|youtu\.be/)([\w-]{6,})""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(url: String?): String? =
        RE.find(url ?: "")?.groupValues?.getOrNull(1)

    fun isYoutubeHost(url: String?): Boolean = runCatching {
        val host = java.net.URI(url).host ?: return false
        host.equals("youtube.com", ignoreCase = true) ||
            host.endsWith(".youtube.com") ||
            host.equals("youtu.be", ignoreCase = true)
    }.getOrDefault(false)
}
