package com.example.ytdownloader.download

import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * MP3 extraction/encode. Builds the exact command from ANDROID_PORT_PLAN §8.1:
 *
 *   -f "bestaudio/best" -x --audio-format mp3 --audio-quality 0
 *   --embed-metadata --embed-thumbnail --postprocessor-args "-b:a <k>k"
 *   -o "<mp3Dir>/<id>.%(ext)s" --no-playlist --newline
 *
 * The encode + ID3 tags are performed by yt-dlp's ffmpeg postprocessor (the
 * bundled ffmpeg is made available to yt-dlp via youtubedl-android's ffmpeg
 * module and YoutubeDL.initFFmpeg). This is the recommended v1 approach —
 * see ANDROID_PORT_PLAN §8.2 ("v1 can rely on --embed-metadata").
 */
object Mp3Converter {

    fun buildRequest(
        url: String,
        outputDir: String,
        videoId: String,
        bitrateKbps: Int,
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
        request.addOption("-f", "bestaudio/best")
        request.addOption("-x")
        request.addOption("--audio-format", "mp3")
        request.addOption("--audio-quality", "0")
        request.addOption("--embed-metadata")
        request.addOption("--embed-thumbnail")
        request.addOption("--postprocessor-args", "-b:a ${bitrateKbps}k")
        request.addOption("-o", "$outputDir/$videoId.%(ext)s")
        request.addOption("--no-playlist")
        request.addOption("--newline")
        request.addOption("--no-warnings")
        return request
    }
}
