package com.leestana.videoontv

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel

/**
 * Routes SAF documents through an untransformed file descriptor so Media3 can
 * reopen and position the source without depending on a provider's stream skip implementation.
 */
@UnstableApi
class VelaDataSourceFactory(context: Context) : DataSource.Factory {
    private val appContext = context.applicationContext

    override fun createDataSource(): DataSource = RoutingDataSource(appContext)
}

@UnstableApi
private class RoutingDataSource(context: Context) : DataSource {
    private val defaultSource = DefaultDataSource.Factory(context).createDataSource()
    private val contentSource = SeekableContentDataSource(context)
    private var activeSource: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        defaultSource.addTransferListener(transferListener)
        contentSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(activeSource == null) { "Data source is already open" }
        if (dataSpec.uri.scheme == "content") {
            try {
                activeSource = contentSource
                return contentSource.open(dataSpec)
            } catch (_: IOException) {
                runCatching { contentSource.close() }
            }
        }
        activeSource = defaultSource
        return defaultSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(activeSource) { "Data source is not open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = activeSource?.uri

    override fun close() {
        val source = activeSource
        activeSource = null
        source?.close()
    }
}

@UnstableApi
private class SeekableContentDataSource(context: Context) : BaseDataSource(false) {
    private val resolver = context.contentResolver
    private var descriptor: AssetFileDescriptor? = null
    private var input: FileInputStream? = null
    private var opened = false
    private var uri: Uri? = null
    private var bytesRemaining = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val afd = resolver.openAssetFileDescriptor(dataSpec.uri, "r")
            ?: throw IOException("Could not open ${dataSpec.uri}")
        descriptor = afd
        try {
            val stream = FileInputStream(afd.fileDescriptor)
            input = stream
            val channel = stream.channel
            val sourceLength = resolveLength(afd, channel)
            if (sourceLength != C.LENGTH_UNSET.toLong() && dataSpec.position > sourceLength) {
                throw DataSourceException(
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                )
            }
            channel.position(afd.startOffset + dataSpec.position)
            bytesRemaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                sourceLength != C.LENGTH_UNSET.toLong() -> sourceLength - dataSpec.position
                else -> C.LENGTH_UNSET.toLong()
            }
            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (error: IOException) {
            runCatching { input?.close() }
            runCatching { descriptor?.close() }
            input = null
            descriptor = null
            uri = null
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val requested = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        val read = checkNotNull(input).read(buffer, offset, requested)
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            input?.close()
        } finally {
            input = null
            runCatching { descriptor?.close() }
            descriptor = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private fun resolveLength(afd: AssetFileDescriptor, channel: FileChannel): Long {
        if (afd.length >= 0) return afd.length
        val statSize = afd.parcelFileDescriptor.statSize
        if (statSize >= 0) return (statSize - afd.startOffset).coerceAtLeast(0)
        return runCatching { channel.size() - afd.startOffset }
            .getOrDefault(C.LENGTH_UNSET.toLong())
            .takeIf { it >= 0 } ?: C.LENGTH_UNSET.toLong()
    }
}
