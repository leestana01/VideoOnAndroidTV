package com.leestana.videoontv

object MediaFileSupport {
    private val extensions = setOf(
        "3g2", "3gp", "aac", "ac3", "aiff", "avi", "eac3", "flac", "flv", "m2ts",
        "m4a", "m4v", "mka", "mkv", "mov", "mp2", "mp3", "mp4", "mpeg", "mpg",
        "oga", "ogg", "ogm", "ogv", "opus", "ts", "vob", "wav", "webm", "wma", "wmv",
    )

    fun isPlayable(name: String?, mimeType: String?): Boolean {
        if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) return true
        val extension = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
        return extension in extensions
    }
}
