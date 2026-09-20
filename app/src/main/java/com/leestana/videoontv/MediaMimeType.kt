package com.leestana.videoontv

object MediaMimeType {
    fun resolve(declaredType: String?, displayName: String?): String {
        if (!declaredType.isNullOrBlank() && declaredType != "application/octet-stream") return declaredType
        return when (displayName?.substringAfterLast('.', "")?.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }
    }
}
