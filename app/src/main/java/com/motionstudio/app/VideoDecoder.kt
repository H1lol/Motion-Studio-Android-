package com.motionstudio.app

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * VideoDecoder — decodes a video file into a SurfaceTexture.
 *
 * MediaCodec renders decoded frames directly into the Surface that backs
 * the SurfaceTexture owned by GlEffectRenderer. The renderer's shader
 * pipeline then reads those frames as a texture.
 *
 * This is the missing link between "import a video" and "see it in the
 * viewer". Without it, imports succeed but nothing plays.
 *
 * Threading:
 *   - The decode loop runs on its own thread.
 *   - open(), start(), pause(), resume(), seekTo(), stop() can be called
 *     from any thread.
 *   - onInfo callbacks fire on the main thread.
 *
 * Audio is intentionally not handled here. Video and audio decode on
 * separate paths; audio goes through MediaPlayer or a separate AudioDecoder.
 */
class VideoDecoder(
    private val context: Context,
    private val surfaceTexture: SurfaceTexture,
    private val onReady: (width: Int, height: Int, durationMs: Long) -> Unit,
    private val onError: (String) -> Unit = {},
    private val onComplete: () -> Unit = {},
) {

    companion object {
        private const val TAG = "VideoDecoder"
        private const val DEQUEUE_TIMEOUT_US = 10_000L
    }

    // -------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var durationMs: Long = 0L
        private set
    var isPlaying: Boolean = false
        private set

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------
    private val ui = Handler(Looper.getMainLooper())

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var trackIndex: Int = -1
    private var frameRate: Float = 30f

    private var decodeThread: Thread? = null
    private val lock = Object()

    @Volatile private var shouldStop = false
    @Volatile private var shouldPause = false
    @Volatile private var seekRequestUs: Long = -1L

    @Volatile private var codecStarted = false

    private var uri: Uri? = null

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Opens the video. Reads track metadata but does not start decoding.
     * After this returns, width, height, and durationMs are populated.
     */
    fun open(uri: Uri) {
        this.uri = uri
        try {
            val ex = MediaExtractor()
            ex.setDataSource(context, uri, null)

            val track = findVideoTrack(ex)
            if (track < 0) {
                fail("No video track in this file")
                return
            }
            trackIndex = track
            ex.selectTrack(track)

            val format = ex.getTrackFormat(track)
            width = format.getInteger(MediaFormat.KEY_WIDTH)
            height = format.getInteger(MediaFormat.KEY_HEIGHT)
            durationMs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) / 1000L else 0L
            frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE))
                format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() else 30f

            extractor = ex

            ui.post { onReady(width, height, durationMs) }
            Log.i(TAG, "Opened ${width}x${height} @ ${frameRate}fps, ${durationMs}ms")
        } catch (t: Throwable) {
            fail("open failed: ${t.message}")
        }
    }

    /**
     * Starts decoding and playback. Safe to call repeatedly — it's a no-op
     * if the decoder is already running.
     */
    fun start() {
        if (codecStarted) {
            resume()
            return
        }
        if (extractor == null) {
            fail("start() called before open()")
            return
        }

        try {
            val ex = extractor ?: return
            val format = ex.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("no MIME type")

            inputSurface = Surface(surfaceTexture)

            val c = MediaCodec.createDecoderByType(mime)
            c.configure(format, inputSurface, null, 0)
            c.start()
            codec = c
            codecStarted = true
            shouldStop = false
            shouldPause = false

            decodeThread = Thread { decodeLoop() }.apply {
                name = "VideoDecoderLoop"
                priority = Thread.NORM_PRIORITY + 1
                start()
            }

            isPlaying = true
        } catch (t: Throwable) {
            fail("start failed: ${t.message}")
        }
    }

    fun pause() {
        shouldPause = true
        isPlaying = false
    }

    fun resume() {
        if (!codecStarted) { start(); return }
        shouldPause = false
        isPlaying = true
        synchronized(lock) { lock.notifyAll() }
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else resume()
    }

    /**
     * Requests a seek to the given timestamp. The decode loop picks it up
     * on the next iteration. Non-blocking.
     */
    fun seekTo(timeMs: Long) {
        seekRequestUs = timeMs.coerceAtLeast(0L) * 1000L
        synchronized(lock) { lock.notifyAll() }
    }

    /**
     * Stops the decoder, releases the codec, and closes the extractor.
     * Call from onDestroy or when swapping to a different clip.
     */
    fun release() {
        shouldStop = true
        synchronized(lock) { lock.notifyAll() }

        try {
            decodeThread?.join(500)
        } catch (_: Throwable) {}
        decodeThread = null

        try { codec?.stop() } catch (_: Throwable) {}
        try { codec?.release() } catch (_: Throwable) {}
        codec = null
        codecStarted = false

        try { inputSurface?.release() } catch (_: Throwable) {}
        inputSurface = null

        try { extractor?.release() } catch (_: Throwable) {}
        extractor = null
        trackIndex = -1
    }

    // =========================================================================
    // The decode loop
    // =========================================================================

    private fun decodeLoop() {
        val ex = extractor ?: return
        val c = codec ?: return

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!shouldStop && !outputDone) {

                // Handle pause
                if (shouldPause) {
                    synchronized(lock) {
                        while (shouldPause && !shouldStop) {
                            try { lock.wait(200) } catch (_: InterruptedException) {}
                        }
                    }
                    continue
                }

                // Handle pending seek
                val seekUs = seekRequestUs
                if (seekUs >= 0L) {
                    seekRequestUs = -1L
                    try {
                        ex.seekTo(seekUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        c.flush()
                        inputDone = false
                        outputDone = false
                    } catch (t: Throwable) {
                        Log.w(TAG, "seek failed: ${t.message}")
                    }
                    continue
                }

                // Feed the decoder
                if (!inputDone) {
                    val inIdx = c.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = c.getInputBuffer(inIdx) ?: continue
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            c.queueInputBuffer(
                                inIdx, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val pts = ex.sampleTime
                            c.queueInputBuffer(inIdx, 0, size, pts, 0)
                            ex.advance()
                        }
                    }
                }

                // Drain the decoder
                val outIdx = c.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        val render = info.size > 0 &&
                                (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0

                        // Timestamps are in microseconds; the presentation time
                        // is handled by the codec when we releaseOutputBuffer
                        // with render=true. The SurfaceTexture receives the
                        // frame and the renderer picks it up.
                        c.releaseOutputBuffer(outIdx, render)

                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = c.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_WIDTH))
                            width = newFormat.getInteger(MediaFormat.KEY_WIDTH)
                        if (newFormat.containsKey(MediaFormat.KEY_HEIGHT))
                            height = newFormat.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No frame ready — loop continues
                    }
                }
            }
        } catch (t: Throwable) {
            if (!shouldStop) fail("decode loop error: ${t.message}")
        }

        if (outputDone && !shouldStop) {
            ui.post { onComplete() }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun findVideoTrack(ex: MediaExtractor): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return -1
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        ui.post { onError(message) }
    }
}
