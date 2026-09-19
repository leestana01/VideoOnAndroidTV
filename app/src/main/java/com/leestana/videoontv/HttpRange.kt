package com.leestana.videoontv

data class HttpRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1

    companion object {
        fun parse(header: String?, size: Long): HttpRange? {
            if (header.isNullOrBlank() || size <= 0 || !header.startsWith("bytes=")) return null
            val value = header.removePrefix("bytes=").substringBefore(',').trim()
            val parts = value.split('-', limit = 2)
            if (parts.size != 2) return null
            return when {
                parts[0].isBlank() -> {
                    val suffix = parts[1].toLongOrNull()?.coerceAtMost(size) ?: return null
                    if (suffix <= 0) null else HttpRange(size - suffix, size - 1)
                }
                else -> {
                    val start = parts[0].toLongOrNull() ?: return null
                    val end = parts[1].toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
                    if (start < 0 || start >= size || end < start) null else HttpRange(start, end)
                }
            }
        }
    }
}

