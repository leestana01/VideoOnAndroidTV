package com.leestana.videoontv

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import fi.iki.elonen.NanoHTTPD
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
            "", "/" -> html()
            "/state" -> state()
            "/media" -> media(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }.apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }
    }

    private fun html(): Response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", RECEIVER_HTML)

    private fun state(): Response {
        val value = snapshots.current()
        val json = "{\"positionMs\":${value.positionMs},\"playing\":${value.playing},\"speed\":${value.speed}}"
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun media(session: IHTTPSession): Response {
        val uri = currentUri() ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No media selected")
        val size = querySize(uri)
        if (size <= 0) return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Media size unavailable")
        val range = HttpRange.parse(session.headers["range"], size)
        val start = range?.start ?: 0L
        val length = range?.length ?: size
        val stream = resolver.openInputStream(uri) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Cannot open media")
        skipFully(stream, start)
        val status = if (range == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT
        val response = newFixedLengthResponse(status, resolver.getType(uri) ?: "application/octet-stream", LimitedInputStream(stream, length), length)
        response.addHeader("Accept-Ranges", "bytes")
        if (range != null) response.addHeader("Content-Range", "bytes ${range.start}-${range.endInclusive}/$size")
        return response
    }

    private fun querySize(uri: Uri): Long {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            if (cursor?.moveToFirst() == true) cursor.getLong(0) else -1L
        } catch (_: Exception) {
            -1L
        } finally {
            cursor?.close()
        }
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
            :root{color-scheme:dark}body{font-family:system-ui;background:#080b10;color:#f7f8fa;display:grid;place-items:center;min-height:100vh;margin:0}.card{max-width:28rem;padding:2rem;background:#151922;border-radius:1.5rem;text-align:center}button{background:#66e3c4;color:#080b10;border:0;border-radius:999px;font-weight:700;padding:1rem 1.5rem;font-size:1rem}.muted{color:#a8b0c0}audio{width:100%;margin-top:1rem}</style></head>
            <body><main class="card"><h1>Phone audio</h1><p class="muted">Keep this page open and the phone on the same Wi-Fi network as the TV.</p><button id="start">Start synchronized audio</button><p id="status" class="muted">Waiting</p><audio id="media" playsinline preload="auto" src="media"></audio></main>
            <script>
            const m=document.querySelector('#media'),s=document.querySelector('#status'),b=document.querySelector('#start');let enabled=false;
            b.onclick=async()=>{enabled=true;m.muted=false;try{await m.play();await sync(true);b.textContent='Resync';s.textContent='Connected'}catch(e){enabled=false;s.textContent='Could not start audio. Check this browser can play the video, then tap again.'}};
            m.onerror=()=>{s.textContent='This phone cannot decode the selected audio or video format.'};
            async function sync(force=false){if(!enabled)return;try{const r=await fetch('state',{cache:'no-store'});if(!r.ok)throw new Error('state');const x=await r.json(),target=x.positionMs/1000,d=Math.abs(m.currentTime-target);m.playbackRate=x.speed||1;if(force||d>.65)m.currentTime=target;if(x.playing&&m.paused)await m.play();if(!x.playing&&!m.paused)m.pause();s.textContent=x.playing?'Synchronized':'Paused'}catch(e){s.textContent='Reconnecting…'}}setInterval(sync,500);
            </script></body></html>
        """.trimIndent()
    }
}
