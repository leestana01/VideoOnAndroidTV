package com.leestana.videoontv

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

class AudioRelayServer(
    private val resolver: ContentResolver,
    private val currentUri: () -> Uri?,
    private val snapshots: PlaybackSnapshotCache,
) : NanoHTTPD(PORT) {
    private val token = ByteArray(12).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }

    val receiverUrl: String?
        get() = localIpv4()?.let { "http://$it:$PORT/t/$token/" }

    override fun serve(session: IHTTPSession): Response {
        val prefix = "/t/$token"
        if (!session.uri.startsWith(prefix)) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        return when (session.uri.removePrefix(prefix)) {
            "", "/" -> html(prefix)
            "/state" -> state()
            "/media" -> media(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }.apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }
    }

    private fun html(prefix: String): Response = newFixedLengthResponse(
        Response.Status.OK,
        "text/html; charset=utf-8",
        RECEIVER_HTML.replace("__BASE__", prefix),
    )

    private fun state(): Response {
        val value = snapshots.current()
        val json = "{\"positionMs\":${value.positionMs},\"playing\":${value.playing},\"speed\":${value.speed}}"
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun media(session: IHTTPSession): Response {
        val uri = currentUri() ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No media selected")
        val metadata = queryMetadata(uri)
        if (metadata.size <= 0) return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Media size unavailable")
        val range = HttpRange.parse(session.headers["range"], metadata.size)
        val start = range?.start ?: 0L
        val length = range?.length ?: metadata.size
        val status = if (range == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT
        val mimeType = MediaMimeType.resolve(resolver.getType(uri), metadata.displayName)
        val response = if (session.method == NanoHTTPD.Method.HEAD) {
            newFixedLengthResponse(status, mimeType, ByteArrayInputStream(ByteArray(0)), length)
        } else {
            val stream = resolver.openInputStream(uri)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Cannot open media")
            skipFully(stream, start)
            newFixedLengthResponse(status, mimeType, LimitedInputStream(stream, length), length)
        }
        response.addHeader("Accept-Ranges", "bytes")
        if (range != null) response.addHeader("Content-Range", "bytes ${range.start}-${range.endInclusive}/${metadata.size}")
        return response
    }

    private fun queryMetadata(uri: Uri): MediaMetadata {
        var cursor: Cursor? = null
        var size = -1L
        var displayName: String? = null
        try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        if (size <= 0) {
            size = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.length.takeIf { it > 0 } ?: descriptor.parcelFileDescriptor.statSize
                } ?: -1L
            }.getOrDefault(-1L)
        }
        return MediaMetadata(size, displayName ?: uri.lastPathSegment)
    }

    private fun skipFully(stream: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) remaining -= skipped else if (stream.read() == -1) break else remaining--
        }
    }

    private class LimitedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = super.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count > 0) remaining -= count
            return count
        }
    }

    private data class MediaMetadata(val size: Long, val displayName: String?)

    companion object {
        private const val PORT = 8088

        fun localIpv4(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }

        private val RECEIVER_HTML = """
            <!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Vela Phone Audio</title><style>
            :root{color-scheme:dark}body{font-family:system-ui;background:#080b10;color:#f7f8fa;display:grid;place-items:center;min-height:100vh;margin:0}.card{max-width:28rem;padding:2rem;background:#151922;border-radius:1.5rem;text-align:center}button{background:#66e3c4;color:#080b10;border:0;border-radius:999px;font-weight:700;padding:1rem 1.5rem;font-size:1rem}.muted{color:#a8b0c0}video{width:100%;max-height:9rem;margin-top:1rem;background:#080b10}</style></head>
            <body><main class="card"><h1>Phone audio</h1><p class="muted">Keep this page open and the phone on the same Wi-Fi network as the TV.</p><button id="start">Start synchronized audio</button><p id="status" class="muted">Preparing media…</p><video id="media" playsinline preload="metadata" controls></video></main>
            <script>
            const base='__BASE__',m=document.querySelector('#media'),s=document.querySelector('#status'),b=document.querySelector('#start');let enabled=false,latest=null;
            m.src=base+'/media';m.load();
            m.onloadedmetadata=()=>{s.textContent='Ready. Tap the button to start audio.'};
            m.onerror=()=>{const code=m.error?m.error.code:'unknown';s.textContent='Media error '+code+': this phone cannot decode the selected track or the TV could not read it.'};
            b.onclick=async()=>{m.muted=false;s.textContent='Starting…';try{if(latest&&Number.isFinite(m.duration)){m.currentTime=Math.min(latest.positionMs/1000,Math.max(0,m.duration-.05))}enabled=true;await m.play();applyState(true);b.textContent='Resync'}catch(e){enabled=false;s.textContent='Start failed ('+(e.name||'unknown')+'). Reload this page, then tap again. If the error is NotSupportedError, the phone cannot decode this track.'}};
            function applyState(force=false){if(!enabled||!latest)return;const target=latest.positionMs/1000,d=Math.abs(m.currentTime-target);m.playbackRate=latest.speed||1;if(force||d>.65)m.currentTime=target;if(latest.playing&&m.paused)m.play().catch(e=>{s.textContent='Playback blocked ('+(e.name||'unknown')+'). Tap Resync.'});if(!latest.playing&&!m.paused)m.pause();s.textContent=latest.playing?'Synchronized':'Paused'}
            async function refresh(){try{const r=await fetch(base+'/state',{cache:'no-store'});if(!r.ok)throw new Error('state '+r.status);latest=await r.json();applyState()}catch(e){if(enabled)s.textContent='Reconnecting…'}}refresh();setInterval(refresh,500);
            </script></body></html>
        """.trimIndent()
    }
}
