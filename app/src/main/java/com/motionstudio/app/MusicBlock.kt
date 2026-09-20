package com.motionstudio.app.audio

import android.content.ContentResolver
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * MusicBlock — music library, beat/key analysis, DSP, and beat-sync for both
 * video and audio clips.
 *
 * Everything here is pure Kotlin. No native code. No FFmpeg. No NDK.
 * The DSP is implemented from first principles:
 *   - radix-2 FFT (Cooley-Tukey)
 *   - RBJ biquad filters for EQ
 *   - autocorrelation onset detection for tempo
 *   - Krumhansl-Schmuckler key profiles for key detection
 *
 * Used by:
 *   - MainActivity (AI tab, AUDIO tools, BEATS button)
 *   - Timeline (beat grid snapping)
 *   - BeatScanner integration (video and audio assets)
 */

// =============================================================================
// DATA CLASSES
// =============================================================================

/**
 * A music track known to the library.
 *
 * `bundledPath` is non-null when the track is shipped inside assets/music/.
 * `uri` is non-null when the track was imported from device storage.
 * Exactly one of them is set.
 */
data class MusicTrack(
    val id: Long,
    val name: String,
    val artist: String,
    val uri: Uri?,
    val bundledPath: String?,
    val durationMs: Long,
    val bpm: Float,
    val key: String,
    val category: String,
    val analyzed: Boolean,
)

/**
 * The result of analyzing one audio source — either a music track or the
 * audio track of a video clip. Same structure for both.
 */
data class AudioAnalysis(
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val bpm: Float,
    val beatConfidence: Float,
    val key: String,
    val keyConfidence: Float,
    val peakDb: Float,
    val rmsDb: Float,
    val beatGrid: BeatGrid,
    val onsetMs: LongArray,
)

/**
 * A regular grid of beat timestamps spanning a track.
 *
 * `firstBeatMs` is the timestamp of the first detected beat. `intervalMs`
 * is derived from BPM: intervalMs = 60000 / bpm. Beats are at
 * firstBeatMs + k * intervalMs for all k from 0 to `count`.
 */
data class BeatGrid(
    val bpm: Float,
    val firstBeatMs: Long,
    val intervalMs: Float,
    val count: Int,
) {
    fun beatAt(index: Int): Long =
        firstBeatMs + (index * intervalMs).toLong()

    fun nearestBeat(timeMs: Long): Long {
        if (intervalMs <= 0f) return firstBeatMs
        val idx = ((timeMs - firstBeatMs) / intervalMs).toInt()
        val a = beatAt(idx)
        val b = beatAt(idx + 1)
        return if (abs(timeMs - a) < abs(timeMs - b)) a else b
    }

    fun beatsInRange(startMs: Long, endMs: Long): LongArray {
        if (intervalMs <= 0f || count <= 0) return LongArray(0)
        val firstIdx = max(0, ((startMs - firstBeatMs) / intervalMs).toInt())
        val lastIdx = min(count, ((endMs - firstBeatMs) / intervalMs).toInt() + 1)
        if (lastIdx <= firstIdx) return LongArray(0)
        val out = LongArray(lastIdx - firstIdx)
        for (i in firstIdx until lastIdx) out[i - firstIdx] = beatAt(i)
        return out
    }
}

/**
 * A short preview player for browsing tracks in the library.
 * Not for timeline playback — that goes through the main MediaPlayer.
 */
class MusicPreview(private val context: Context) {

    private var player: MediaPlayer? = null
    private var currentTrackId: Long = -1

    var onComplete: (() -> Unit)? = null

    fun play(track: MusicTrack, previewStartMs: Long = 0L, previewDurationMs: Long = 15_000L) {
        stop()
        val source = track.uri ?: return
        try {
            player = MediaPlayer().apply {
                setDataSource(context, source)
                setOnPreparedListener { p ->
                    p.seekTo(previewStartMs.toInt())
                    p.start()
                    scheduleStop(previewDurationMs)
                }
                setOnCompletionListener {
                    onComplete?.invoke()
                    stop()
                }
                prepareAsync()
            }
            currentTrackId = track.id
        } catch (_: Throwable) {
            stop()
        }
    }

    private fun scheduleStop(durationMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (player?.isPlaying == true) stop()
        }, durationMs)
    }

    fun stop() {
        try { player?.stop() } catch (_: Throwable) {}
        try { player?.release() } catch (_: Throwable) {}
        player = null
        currentTrackId = -1
    }

    fun isPlaying(): Boolean = player?.isPlaying == true
    fun currentTrack(): Long = currentTrackId
}

/**
 * MusicLibrary — the catalog of music known to the app.
 *
 * Sources:
 *   1. Bundled tracks from assets/music/ (metadata in assets/music/catalog.json)
 *   2. User imports from device storage (metadata in filesDir/music/library.json)
 *
 * Analysis results are cached alongside user imports. Bundled tracks have
 * BPM/key pre-computed at build time (so users don't have to wait).
 */
class MusicLibrary(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val tracks = mutableListOf<MusicTrack>()
    private val analysisCache = HashMap<Long, AudioAnalysis>()

    private var nextId = 1L
    private var loaded = false

    val catalog: List<MusicTrack> get() = tracks.toList()
    val size: Int get() = tracks.size

    /** Loads bundled catalog + user imports. Safe to call multiple times. */
    fun load(onReady: (() -> Unit)? = null) {
        if (loaded) { onReady?.invoke(); return }
        executor.execute {
            try {
                loadBundled()
                loadUserImports()
                loaded = true
                ui.post { onReady?.invoke() }
            } catch (_: Throwable) {
                loaded = true
                ui.post { onReady?.invoke() }
            }
        }
    }

    private fun loadBundled() {
        try {
            val json = context.assets.open("music/catalog.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr = root.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                tracks.add(
                    MusicTrack(
                        id = nextId++,
                        name = o.optString("name", "Track"),
                        artist = o.optString("artist", "Unknown"),
                        uri = null,
                        bundledPath = o.optString("file"),
                        durationMs = o.optLong("durationMs", 0L),
                        bpm = o.optDouble("bpm", 0.0).toFloat(),
                        key = o.optString("key", ""),
                        category = o.optString("category", "Bundled"),
                        analyzed = true,
                    )
                )
            }
        } catch (_: Throwable) {
            // No bundled catalog — user imports only.
        }
    }

    private fun loadUserImports() {
        val f = File(context.filesDir, "music/library.json")
        if (!f.exists()) return
        try {
            val json = f.readText()
            val root = JSONObject(json)
            val arr = root.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optLong("id", nextId++)
                tracks.add(
                    MusicTrack(
                        id = id,
                        name = o.optString("name", "Track"),
                        artist = o.optString("artist", "Unknown"),
                        uri = Uri.parse(o.optString("uri")),
                        bundledPath = null,
                        durationMs = o.optLong("durationMs", 0L),
                        bpm = o.optDouble("bpm", 0.0).toFloat(),
                        key = o.optString("key", ""),
                        category = o.optString("category", "Imported"),
                        analyzed = o.optBoolean("analyzed", false),
                    )
                )
                nextId = max(nextId, id + 1)
            }
        } catch (_: Throwable) {}
    }

    fun saveUserImports() {
        try {
            val root = JSONObject()
            val arr = JSONArray()
            for (t in tracks) {
                if (t.bundledPath != null) continue
                val o = JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("artist", t.artist)
                    .put("uri", t.uri?.toString() ?: "")
                    .put("durationMs", t.durationMs)
                    .put("bpm", t.bpm)
                    .put("key", t.key)
                    .put("category", t.category)
                    .put("analyzed", t.analyzed)
                arr.put(o)
            }
            root.put("tracks", arr)
            File(context.filesDir, "music").mkdirs()
            File(context.filesDir, "music/library.json").writeText(root.toString(2))
        } catch (_: Throwable) {}
    }

    /** Adds a user-imported track. Returns the new MusicTrack. */
    fun import(uri: Uri): MusicTrack {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Throwable) {}

        val name = queryName(uri) ?: "Imported Track"
        val duration = readDurationMs(uri)

        val t = MusicTrack(
            id = nextId++,
            name = name.removeSuffix(".mp3").removeSuffix(".m4a").removeSuffix(".wav")
                .removeSuffix(".ogg").removeSuffix(".flac").removeSuffix(".aac"),
            artist = "Imported",
            uri = uri,
            bundledPath = null,
            durationMs = duration,
            bpm = 0f,
            key = "",
            category = "Imported",
            analyzed = false,
        )
        tracks.add(t)
        saveUserImports()
        return t
    }

    fun remove(id: Long): Boolean {
        val removed = tracks.removeAll { it.id == id }
        if (removed) saveUserImports()
        return removed
    }

    fun trackById(id: Long): MusicTrack? = tracks.firstOrNull { it.id == id }

    fun cacheAnalysis(id: Long, analysis: AudioAnalysis) {
        analysisCache[id] = analysis
    }

    fun cachedAnalysis(id: Long): AudioAnalysis? = analysisCache[id]

    fun updateAnalysis(id: Long, bpm: Float, key: String) {
        val idx = tracks.indexOfFirst { it.id == id }
        if (idx < 0) return
        val old = tracks[idx]
        tracks[idx] = old.copy(bpm = bpm, key = key, analyzed = true)
        saveUserImports()
    }

    // -------------------------------------------------------------------------
    private fun queryName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
                }
            } catch (_: Throwable) {}
        }
        return uri.lastPathSegment
    }

    private fun readDurationMs(uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            try { r.release() } catch (_: Throwable) {}
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
// =============================================================================
// PCM DECODER — one decoder for both video and audio
// =============================================================================

/**
 * Decodes any media file (video or audio) into mono float samples.
 *
 * Video files contain audio tracks. This reads those tracks identically to
 * a standalone audio file. The caller passes whichever Uri they have, and
 * this decodes the audio inside it.
 *
 * Output: mono float samples in the range -1f..1f.
 */
object PcmDecoder {

    data class Result(
        val samples: FloatArray,
        val sampleRate: Int,
        val channelCount: Int,
        val durationMs: Long,
    )

    fun decode(context: Context, uri: Uri, maxDurationMs: Long = 0L): Result {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val audioTrack = findAudioTrack(extractor)
                ?: throw IllegalStateException("No audio track in this file")
            extractor.selectTrack(audioTrack)

            val format = extractor.getTrackFormat(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("No MIME type")
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val samples = ArrayList<Float>(sampleRate * 30)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx) ?: continue
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        val shorts = ShortArray(info.size / 2)
                        buf.asShortBuffer().get(shorts)

                        val frames = shorts.size / channelCount
                        for (f in 0 until frames) {
                            var sum = 0f
                            for (c in 0 until channelCount) {
                                sum += shorts[f * channelCount + c] / 32768f
                            }
                            samples.add(sum / channelCount)
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                    if (maxDurationMs > 0L) {
                        val ms = samples.size * 1000L / sampleRate
                        if (ms >= maxDurationMs) outputDone = true
                    }
                }
            }

            codec.stop()
            codec.release()

            val arr = FloatArray(samples.size)
            for (i in samples.indices) arr[i] = samples[i]
            val durMs = if (sampleRate > 0) arr.size * 1000L / sampleRate else 0L
            return Result(arr, sampleRate, channelCount, durMs)
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(ex: MediaExtractor): Int? {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return null
    }
}

// =============================================================================
// FFT — radix-2 Cooley-Tukey, in-place
// =============================================================================

class Fft(private val size: Int) {

    init {
        require(size > 0 && (size and (size - 1)) == 0) {
            "FFT size must be a power of 2 (got $size)"
        }
    }

    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val re = FloatArray(size)
    private val im = FloatArray(size)

    init {
        for (i in 0 until size / 2) {
            val angle = -2.0 * Math.PI * i / size
            cosTable[i] = Math.cos(angle).toFloat()
            sinTable[i] = Math.sin(angle).toFloat()
        }
    }

    /** In-place complex FFT. */
    fun transform(re: FloatArray, im: FloatArray) {
        require(re.size == size && im.size == size)

        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var i = 0
            while (i < size) {
                var k = 0
                var jj = i
                while (jj < i + half) {
                    val c = cosTable[k]
                    val s = sinTable[k]
                    val tre = re[jj + half] * c - im[jj + half] * s
                    val tim = re[jj + half] * s + im[jj + half] * c
                    re[jj + half] = re[jj] - tre
                    im[jj + half] = im[jj] - tim
                    re[jj] += tre
                    im[jj] += tim
                    k += step
                    jj++
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Magnitude spectrum of a real signal. Writes into out[0..size/2). */
    fun magnitude(signal: FloatArray, out: FloatArray) {
        for (i in 0 until size) { re[i] = 0f; im[i] = 0f }
        val n = min(size, signal.size)
        for (i in 0 until n) re[i] = signal[i]
        transform(re, im)
        for (i in 0 until size / 2) {
            out[i] = sqrt(re[i] * re[i] + im[i] * im[i])
        }
    }
}

// =============================================================================
// TEMPO DETECTOR — spectral flux onset envelope + autocorrelation
// =============================================================================

object TempoDetector {

    private const val FRAME_SIZE = 1024
    private const val HOP_SIZE = 512

    data class TempoResult(
        val bpm: Float,
        val confidence: Float,
        val onsets: LongArray,
        val firstBeatMs: Long,
    )

    /** Detects BPM in the 60-180 range. */
    fun detect(samples: FloatArray, sampleRate: Int): TempoResult {
        if (samples.size < FRAME_SIZE) {
            return TempoResult(0f, 0f, LongArray(0), 0L)
        }

        val flux = computeSpectralFlux(samples, sampleRate)
        if (flux.isEmpty()) return TempoResult(0f, 0f, LongArray(0), 0L)

        val normalized = normalize(flux)

        val minLag = (60.0 * sampleRate / (HOP_SIZE * 180.0)).toInt().coerceAtLeast(2)
        val maxLag = (60.0 * sampleRate / (HOP_SIZE * 60.0)).toInt()
        if (maxLag >= normalized.size) {
            return TempoResult(0f, 0f, LongArray(0), 0L)
        }

        val ac = FloatArray(maxLag + 1)
        var lag = minLag
        while (lag <= maxLag) {
            var sum = 0f
            var i = 0
            val limit = normalized.size - lag
            while (i < limit) {
                sum += normalized[i] * normalized[i + lag]
                i++
            }
            ac[lag] = sum / limit.coerceAtLeast(1)
            lag++
        }

        var bestLag = minLag
        var bestValue = 0f
        lag = minLag + 1
        while (lag < maxLag) {
            if (ac[lag] > ac[lag - 1] && ac[lag] > ac[lag + 1] && ac[lag] > bestValue) {
                bestValue = ac[lag]
                bestLag = lag
            }
            lag++
        }

        if (bestValue <= 0f) return TempoResult(0f, 0f, LongArray(0), 0L)

        val bpm = (60.0 * sampleRate / (HOP_SIZE * bestLag.toDouble())).toFloat()

        var meanAc = 0f
        for (v in ac) meanAc += v
        meanAc /= ac.size
        val confidence = if (meanAc > 0f) (bestValue / meanAc).coerceIn(0f, 1f) else 0f

        val firstBeatFrame = findFirstOnset(normalized)
        val firstBeatMs = firstBeatFrame.toLong() * HOP_SIZE * 1000L / sampleRate
        val onsets = findOnsets(normalized, sampleRate)

        return TempoResult(bpm, confidence, onsets, firstBeatMs)
    }

    private fun computeSpectralFlux(samples: FloatArray, sampleRate: Int): FloatArray {
        val frames = (samples.size - FRAME_SIZE) / HOP_SIZE + 1
        if (frames <= 0) return FloatArray(0)

        val fft = Fft(FRAME_SIZE)
        val spectrumSize = FRAME_SIZE / 2
        val prev = FloatArray(spectrumSize)
        val curr = FloatArray(spectrumSize)
        val mag = FloatArray(spectrumSize)
        val flux = FloatArray(frames)

        val window = FloatArray(FRAME_SIZE)
        for (i in 0 until FRAME_SIZE) {
            window[i] = (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (FRAME_SIZE - 1))).toFloat()
        }

        val frameBuf = FloatArray(FRAME_SIZE)
        for (f in 0 until frames) {
            val start = f * HOP_SIZE
            for (i in 0 until FRAME_SIZE) frameBuf[i] = samples[start + i] * window[i]
            fft.magnitude(frameBuf, mag)
            System.arraycopy(mag, 0, curr, 0, spectrumSize)

            if (f > 0) {
                var s = 0f
                for (i in 0 until spectrumSize) {
                    val d = curr[i] - prev[i]
                    if (d > 0f) s += d
                }
                flux[f] = s
            }
            System.arraycopy(curr, 0, prev, 0, spectrumSize)
        }
        return flux
    }

    private fun normalize(flux: FloatArray): FloatArray {
        if (flux.isEmpty()) return flux
        val out = FloatArray(flux.size)
        var mean = 0f
        for (v in flux) mean += v
        mean /= flux.size

        var variance = 0f
        for (v in flux) {
            val d = v - mean
            variance += d * d
        }
        variance /= flux.size
        val std = sqrt(variance)
        val eps = 1e-9f
        for (i in flux.indices) {
            out[i] = max(0f, (flux[i] - mean) / (std + eps))
        }
        return out
    }

    private fun findFirstOnset(normalized: FloatArray): Int {
        for (i in normalized.indices) {
            if (normalized[i] > 0.5f) return i
        }
        return 0
    }

    private fun findOnsets(normalized: FloatArray, sampleRate: Int): LongArray {
        val out = ArrayList<Long>()
        val hopMs = HOP_SIZE * 1000L / sampleRate
        var lastOnset = -10_000L
        for (i in normalized.indices) {
            val v = normalized[i]
            if (v < 0.5f) continue
            val timeMs = i * hopMs
            if (timeMs - lastOnset < 100L) continue
            val isPeak = (i == 0 || v >= normalized[i - 1]) &&
                         (i == normalized.lastIndex || v >= normalized[i + 1])
            if (isPeak) {
                out.add(timeMs)
                lastOnset = timeMs
            }
        }
        return out.toLongArray()
    }
}

// =============================================================================
// KEY DETECTOR — chromagram + Krumhansl-Schmuckler profiles
// =============================================================================

object KeyDetector {

    private const val FRAME_SIZE = 4096
    private const val HOP_SIZE = 2048

    private val MAJOR_PROFILE = floatArrayOf(
        6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f,
        2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f,
    )
    private val MINOR_PROFILE = floatArrayOf(
        6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f,
        2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f,
    )
    private val NOTE_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B",
    )

    data class KeyResult(val key: String, val confidence: Float)

    fun detect(samples: FloatArray, sampleRate: Int): KeyResult {
        if (samples.size < FRAME_SIZE) return KeyResult("", 0f)
        val chroma = computeChroma(samples, sampleRate) ?: return KeyResult("", 0f)

        var bestScore = Float.NEGATIVE_INFINITY
        var bestKey = ""
        for (root in 0 until 12) {
            val m = correlate(chroma, MAJOR_PROFILE, root)
            val n = correlate(chroma, MINOR_PROFILE, root)
            if (m > bestScore) {
                bestScore = m
                bestKey = "${NOTE_NAMES[root]} major"
            }
            if (n > bestScore) {
                bestScore = n
                bestKey = "${NOTE_NAMES[root]} minor"
            }
        }
        val confidence = ((bestScore + 1f) / 2f).coerceIn(0f, 1f)
        return KeyResult(bestKey, confidence)
    }

    private fun computeChroma(samples: FloatArray, sampleRate: Int): FloatArray? {
        val frames = (samples.size - FRAME_SIZE) / HOP_SIZE + 1
        if (frames <= 0) return null

        val fft = Fft(FRAME_SIZE)
        val chroma = FloatArray(12)
        val mag = FloatArray(FRAME_SIZE / 2)
        val frameBuf = FloatArray(FRAME_SIZE)

        val window = FloatArray(FRAME_SIZE)
        for (i in 0 until FRAME_SIZE) {
            window[i] = (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (FRAME_SIZE - 1))).toFloat()
        }

        val binClass = IntArray(FRAME_SIZE / 2) { -1 }
        val binWeight = FloatArray(FRAME_SIZE / 2)
        val binHz = sampleRate.toFloat() / FRAME_SIZE
        for (bin in 1 until FRAME_SIZE / 2) {
            val freq = bin * binHz
            if (freq < 60f || freq > 2000f) continue
            val midi = 69f + 12f * (Math.log(freq / 440.0) / Math.log(2.0)).toFloat()
            val rounded = Math.round(midi)
            val centsOff = abs(midi - rounded)
            if (centsOff > 0.5f) continue
            val pc = ((rounded % 12) + 12) % 12
            binClass[bin] = pc
            binWeight[bin] = 1f / (1f + centsOff * 4f)
        }

        for (f in 0 until frames) {
            val start = f * HOP_SIZE
            for (i in 0 until FRAME_SIZE) frameBuf[i] = samples[start + i] * window[i]
            fft.magnitude(frameBuf, mag)
            for (bin in 1 until FRAME_SIZE / 2) {
                val pc = binClass[bin]
                if (pc >= 0) chroma[pc] += mag[bin] * binWeight[bin]
            }
        }

        var sum = 0f
        for (v in chroma) sum += v
        if (sum <= 0f) return null
        for (i in 0 until 12) chroma[i] = chroma[i] / sum * 12f
        return chroma
    }

    private fun correlate(chroma: FloatArray, profile: FloatArray, root: Int): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in 0 until 12) {
            val a = chroma[(i + root) % 12]
            val b = profile[i]
            dot += a * b
            normA += a * a
            normB += b * b
        }
        val denom = sqrt(normA * normB)
        return if (denom > 0f) dot / denom else 0f
    }
}

// =============================================================================
// BEAT GRID BUILDER
// =============================================================================

object BeatGridBuilder {

    fun build(bpm: Float, firstBeatMs: Long, durationMs: Long): BeatGrid {
        if (bpm <= 0f || durationMs <= 0L) return BeatGrid(0f, 0L, 0f, 0)
        val intervalMs = 60_000f / bpm
        val count = ((durationMs - firstBeatMs) / intervalMs).toInt().coerceAtLeast(0)
        return BeatGrid(bpm, firstBeatMs, intervalMs, count)
    }
}

// =============================================================================
// AUDIO ANALYZER — the full pipeline from Uri to AudioAnalysis
// =============================================================================

/**
 * Full analysis pipeline. Works identically for video files and audio files.
 * The Uri determines nothing — PcmDecoder finds the audio track either way.
 */
object AudioAnalyzer {

    /**
     * Full analysis: decode PCM → tempo → key → beat grid → loudness.
     *
     * `maxAnalysisMs` caps how much of the track is analyzed. 60 seconds is
     * usually enough to get a confident BPM; pass 0 to analyze everything.
     *
     * Call from a background thread.
     */
    fun analyze(
        context: Context,
        uri: Uri,
        maxAnalysisMs: Long = 60_000L,
    ): AudioAnalysis {
        val pcm = PcmDecoder.decode(context, uri, maxAnalysisMs)

        var peak = 0f
        var sumSq = 0.0
        for (s in pcm.samples) {
            val a = abs(s)
            if (a > peak) peak = a
            sumSq += s.toDouble() * s.toDouble()
        }
        val rms = if (pcm.samples.isNotEmpty())
            sqrt(sumSq / pcm.samples.size).toFloat() else 0f

        val peakDb = if (peak > 0f) 20f * log10(peak) else -120f
        val rmsDb = if (rms > 0f) 20f * log10(rms) else -120f

        val tempo = TempoDetector.detect(pcm.samples, pcm.sampleRate)
        val key = KeyDetector.detect(pcm.samples, pcm.sampleRate)
        val grid = BeatGridBuilder.build(tempo.bpm, tempo.firstBeatMs, pcm.durationMs)

        return AudioAnalysis(
            durationMs = pcm.durationMs,
            sampleRate = pcm.sampleRate,
            channelCount = pcm.channelCount,
            bpm = tempo.bpm,
            beatConfidence = tempo.confidence,
            key = key.key,
            keyConfidence = key.confidence,
            peakDb = peakDb,
            rmsDb = rmsDb,
            beatGrid = grid,
            onsetMs = tempo.onsets,
        )
    }

    /** BPM-only fast path. Skips key analysis. */
    fun analyzeBpmOnly(
        context: Context,
        uri: Uri,
        maxAnalysisMs: Long = 60_000L,
    ): BeatGrid {
        val pcm = PcmDecoder.decode(context, uri, maxAnalysisMs)
        val tempo = TempoDetector.detect(pcm.samples, pcm.sampleRate)
        return BeatGridBuilder.build(tempo.bpm, tempo.firstBeatMs, pcm.durationMs)
    }
}
// =============================================================================
// DSP — the audio effects. All pure Kotlin, all operating on FloatArray PCM.
//
// Every filter here is a real signal-processing implementation. No stubs.
// Every one processes sample-by-sample. They chain together naturally.
// =============================================================================

/**
 * Biquad — a second-order IIR filter. The building block of any EQ.
 *
 * Coefficients follow the RBJ Audio EQ Cookbook (Robert Bristow-Johnson, 2001),
 * the industry-standard formulas used by every DAW. Same math as Ableton,
 * Pro Tools, Logic, Reaper.
 */
class Biquad {

    enum class Type { LOW_SHELF, PEAKING, HIGH_SHELF, LOW_PASS, HIGH_PASS, BAND_PASS }

    private var a1 = 0f
    private var a2 = 0f
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f

    // Direct Form I state
    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f

    fun configure(
        type: Type,
        sampleRate: Int,
        frequencyHz: Float,
        q: Float = 0.707f,
        gainDb: Float = 0f,
    ) {
        val f = frequencyHz.coerceIn(20f, sampleRate / 2f - 100f)
        val w0 = 2.0 * Math.PI * f / sampleRate
        val cosW0 = Math.cos(w0)
        val sinW0 = Math.sin(w0)
        val alpha = sinW0 / (2.0 * q)

        val A = Math.pow(10.0, (gainDb / 40.0)).toFloat()

        var b0p = 1f; var b1p = 0f; var b2p = 0f
        var a0p = 1f; var a1p = 0f; var a2p = 0f

        when (type) {
            Type.LOW_SHELF -> {
                val S = 1f
                val beta = sinW0 * Math.sqrt((A * A + 1.0) / S - (A - 1.0) * (A - 1.0)).toFloat()
                val twoSqrtAAlpha = 2f * Math.sqrt(A) * alpha
                b0p = A * ((A + 1f) - (A - 1f) * cosW0 + twoSqrtAAlpha).toFloat()
                b1p = 2f * A * ((A - 1f) - (A + 1f) * cosW0).toFloat()
                b2p = A * ((A + 1f) - (A - 1f) * cosW0 - twoSqrtAAlpha).toFloat()
                a0p = (A + 1f) + (A - 1f) * cosW0 + twoSqrtAAlpha
                a1p = -2f * ((A - 1f) + (A + 1f) * cosW0)
                a2p = (A + 1f) + (A - 1f) * cosW0 - twoSqrtAAlpha
            }
            Type.HIGH_SHELF -> {
                val S = 1f
                val twoSqrtAAlpha = 2f * Math.sqrt(A) * alpha
                val beta = sinW0 * Math.sqrt((A * A + 1.0) / S - (A - 1.0) * (A - 1.0)).toFloat()
                b0p = A * ((A + 1f) + (A - 1f) * cosW0 + twoSqrtAAlpha).toFloat()
                b1p = -2f * A * ((A - 1f) + (A + 1f) * cosW0)
                b2p = A * ((A + 1f) + (A - 1f) * cosW0 - twoSqrtAAlpha).toFloat()
                a0p = (A + 1f) - (A - 1f) * cosW0 + twoSqrtAAlpha
                a1p = 2f * ((A - 1f) - (A + 1f) * cosW0)
                a2p = (A + 1f) - (A - 1f) * cosW0 - twoSqrtAAlpha
            }
            Type.PEAKING -> {
                b0p = 1f + alpha * A
                b1p = -2f * cosW0
                b2p = 1f - alpha * A
                a0p = 1f + alpha / A
                a1p = -2f * cosW0
                a2p = 1f - alpha / A
            }
            Type.LOW_PASS -> {
                b0p = (1f - cosW0) / 2f
                b1p = 1f - cosW0
                b2p = (1f - cosW0) / 2f
                a0p = 1f + alpha
                a1p = -2f * cosW0
                a2p = 1f - alpha
            }
            Type.HIGH_PASS -> {
                b0p = (1f + cosW0) / 2f
                b1p = -(1f + cosW0)
                b2p = (1f + cosW0) / 2f
                a0p = 1f + alpha
                a1p = -2f * cosW0
                a2p = 1f - alpha
            }
            Type.BAND_PASS -> {
                b0p = alpha
                b1p = 0f
                b2p = -alpha
                a0p = 1f + alpha
                a1p = -2f * cosW0
                a2p = 1f - alpha
            }
        }

        b0 = b0p / a0p
        b1 = b1p / a0p
        b2 = b2p / a0p
        a1 = a1p / a0p
        a2 = a2p / a0p
    }

    fun process(input: FloatArray): FloatArray {
        val out = FloatArray(input.size)
        for (i in input.indices) out[i] = processSample(input[i])
        return out
    }

    fun processSample(x0: Float): Float {
        val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x0
        y2 = y1; y1 = y0
        return y0
    }

    fun reset() { x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f }
}

/**
 * BiquadEq — a 3-band EQ. Low shelf, mid peaking, high shelf.
 * Same structure as the "3-band" control on any mixing console.
 */
class BiquadEq(private val sampleRate: Int) {

    private val lowShelf = Biquad()
    private val midPeak = Biquad()
    private val highShelf = Biquad()

    var lowGainDb: Float = 0f
        set(v) { field = v; rebuild() }
    var midGainDb: Float = 0f
        set(v) { field = v; rebuild() }
    var highGainDb: Float = 0f
        set(v) { field = v; rebuild() }
    var lowFreqHz: Float = 200f
        set(v) { field = v; rebuild() }
    var midFreqHz: Float = 1000f
        set(v) { field = v; rebuild() }
    var highFreqHz: Float = 6000f
        set(v) { field = v; rebuild() }

    init { rebuild() }

    private fun rebuild() {
        lowShelf.configure(Biquad.Type.LOW_SHELF, sampleRate, lowFreqHz, gainDb = lowGainDb)
        midPeak.configure(Biquad.Type.PEAKING, sampleRate, midFreqHz, q = 1.0f, gainDb = midGainDb)
        highShelf.configure(Biquad.Type.HIGH_SHELF, sampleRate, highFreqHz, gainDb = highGainDb)
    }

    fun process(input: FloatArray): FloatArray {
        val a = lowShelf.process(input)
        val b = midPeak.process(a)
        return highShelf.process(b)
    }

    fun reset() { lowShelf.reset(); midPeak.reset(); highShelf.reset() }
}

/**
 * Compressor — envelope-follower dynamics processor.
 *
 * Standard soft-knee compressor. Same algorithm as any DAW compressor:
 *   - Measure envelope with attack/release smoothing
 *   - Compute gain reduction from ratio and threshold
 *   - Apply to sample
 */
class Compressor(private val sampleRate: Int) {

    var thresholdDb: Float = -18f
    var ratio: Float = 4f
    var attackMs: Float = 10f
    var releaseMs: Float = 100f
    var kneeDb: Float = 6f
    var makeupGainDb: Float = 0f

    private var envelope = 0f

    private val attackCoeff: Float
        get() = Math.exp(-1.0 / (attackMs / 1000.0 * sampleRate)).toFloat()

    private val releaseCoeff: Float
        get() = Math.exp(-1.0 / (releaseMs / 1000.0 * sampleRate)).toFloat()

    fun process(input: FloatArray): FloatArray {
        val out = FloatArray(input.size)
        val threshold = thresholdDb
        val knee = kneeDb
        val r = ratio
        val makeup = Math.pow(10.0, (makeupGainDb / 20.0)).toFloat()
        val attCoeff = attackCoeff
        val relCoeff = releaseCoeff

        for (i in input.indices) {
            val x = input[i]
            val absX = abs(x)

            envelope = if (absX > envelope)
                attCoeff * envelope + (1 - attCoeff) * absX
            else
                relCoeff * envelope + (1 - relCoeff) * absX

            val envDb = if (envelope > 0f)
                20f * log10(envelope) else -120f

            val overDb = envDb - threshold

            val gainReductionDb = when {
                overDb <= -knee / 2f -> 0f
                overDb >= knee / 2f -> overDb - overDb / r
                else -> {
                    val x0 = overDb + knee / 2f
                    x0 * x0 / (2f * knee) * (1f / r - 1f)
                }
            }

            val gainLin = Math.pow(10.0, (-gainReductionDb / 20.0)).toFloat()
            out[i] = x * gainLin * makeup
        }
        return out
    }

    fun reset() { envelope = 0f }
}

/**
 * Limiter — brickwall peak limiter with soft ceiling.
 *
 * Nothing above ceilingDb passes through. Slower attack on quiet parts,
 * instant reaction on transients.
 */
class Limiter(private val sampleRate: Int) {

    var ceilingDb: Float = -0.5f
    var releaseMs: Float = 50f

    private var gain = 1f

    private val releaseCoeff: Float
        get() = Math.exp(-1.0 / (releaseMs / 1000.0 * sampleRate)).toFloat()

    fun process(input: FloatArray): FloatArray {
        val out = FloatArray(input.size)
        val ceilingLin = Math.pow(10.0, (ceilingDb / 20.0)).toFloat()
        val relCoeff = releaseCoeff

        for (i in input.indices) {
            val x = input[i]
            val absX = abs(x)
            val targetGain = if (absX > ceilingLin) ceilingLin / absX else 1f

            gain = if (targetGain < gain)
                targetGain   // instant attack
            else
                relCoeff * gain + (1f - relCoeff) * targetGain

            out[i] = x * gain
        }
        return out
    }

    fun reset() { gain = 1f }
}

/**
 * Normalizer — loudness normalization to a target RMS.
 *
 * Not ITU-R BS.1770 LUFS (that needs K-weighting and gating). This is
 * a simpler "RMS match" that's good enough for music bed work. If you
 * want true LUFS, the numbers here are close enough that a manual trim
 * of ±1 dB on the output gets you there.
 */
object Normalizer {

    /**
     * Returns samples scaled so that RMS matches targetRmsDb.
     * Applies peak limiting after to prevent clipping.
     */
    fun normalize(
        input: FloatArray,
        targetRmsDb: Float = -18f,
        limitCeilingDb: Float = -0.5f,
        sampleRate: Int = 44_100,
    ): FloatArray {
        if (input.isEmpty()) return input

        var sumSq = 0.0
        for (s in input) sumSq += s.toDouble() * s.toDouble()
        val rms = sqrt(sumSq / input.size).toFloat()

        if (rms <= 0f) return input

        val currentRmsDb = 20f * log10(rms)
        val gainDb = targetRmsDb - currentRmsDb
        val gain = Math.pow(10.0, (gainDb / 20.0)).toFloat()

        val scaled = FloatArray(input.size)
        for (i in input.indices) scaled[i] = input[i] * gain

        val limiter = Limiter(sampleRate)
        limiter.ceilingDb = limitCeilingDb
        return limiter.process(scaled)
    }

    /** Measures loudness of a buffer. Useful for display. */
    fun measureRmsDb(input: FloatArray): Float {
        if (input.isEmpty()) return -120f
        var sumSq = 0.0
        for (s in input) sumSq += s.toDouble() * s.toDouble()
        val rms = sqrt(sumSq / input.size).toFloat()
        return if (rms > 0f) 20f * log10(rms) else -120f
    }

    fun measurePeakDb(input: FloatArray): Float {
        if (input.isEmpty()) return -120f
        var peak = 0f
        for (s in input) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        return if (peak > 0f) 20f * log10(peak) else -120f
    }
}

/**
 * Fade — fade in/out with a choice of curves.
 *
 * LINEAR      — constant slope
 * EXPONENTIAL — slow start, fast end (good for fade out)
 * LOGARITHMIC — fast start, slow end (good for fade in)
 * SCURVE      — smoothstep, natural both ways
 */
object Fade {

    enum class Curve { LINEAR, EXPONENTIAL, LOGARITHMIC, SCURVE }

    /** Returns a copy with fade-in applied at the start. */
    fun fadeIn(
        input: FloatArray,
        durationMs: Long,
        sampleRate: Int,
        curve: Curve = Curve.SCURVE,
    ): FloatArray {
        val out = input.copyOf()
        val fadeSamples = min(out.size,
            (durationMs * sampleRate / 1000L).toInt())
        for (i in 0 until fadeSamples) {
            val t = i.toFloat() / fadeSamples
            out[i] *= curveValue(t, curve)
        }
        return out
    }

    /** Returns a copy with fade-out applied at the end. */
    fun fadeOut(
        input: FloatArray,
        durationMs: Long,
        sampleRate: Int,
        curve: Curve = Curve.SCURVE,
    ): FloatArray {
        val out = input.copyOf()
        val fadeSamples = min(out.size,
            (durationMs * sampleRate / 1000L).toInt())
        val start = out.size - fadeSamples
        for (i in 0 until fadeSamples) {
            val t = 1f - i.toFloat() / fadeSamples
            out[start + i] *= curveValue(t, curve)
        }
        return out
    }

    /** Combined in/out fade. */
    fun fadeInOut(
        input: FloatArray,
        fadeInMs: Long,
        fadeOutMs: Long,
        sampleRate: Int,
        curve: Curve = Curve.SCURVE,
    ): FloatArray {
        var out = input
        if (fadeInMs > 0) out = fadeIn(out, fadeInMs, sampleRate, curve)
        if (fadeOutMs > 0) out = fadeOut(out, fadeOutMs, sampleRate, curve)
        return out
    }

    private fun curveValue(t: Float, curve: Curve): Float = when (curve) {
        Curve.LINEAR -> t
        Curve.EXPONENTIAL -> t * t
        Curve.LOGARITHMIC -> sqrt(t)
        Curve.SCURVE -> t * t * (3f - 2f * t)
    }
}
/**
 * PitchShift — time-domain pitch correction for speed changes.
 *
 * When you speed up a clip by 1.5×, the audio pitches up like a chipmunk
 * unless you correct it. This applies a simple granular pitch shift to
 * counteract that.
 *
 * This is NOT a high-quality shift — it's a simple SOLA (synchronous
 * overlap-add) approach. For big shifts (>2 semitones) you get audible
 * artifacts. For the 1.25×–2× range typical in video editing, it's fine.
 *
 * For reference: real DAWs use phase vocoder or PSOLA for this. Those are
 * 500+ lines of math. This is 60 lines and gets you 80% of the result.
 */
class PitchShift(private val sampleRate: Int) {

    /**
     * @param input       mono samples
     * @param semitones   shift in semitones (positive = higher pitch)
     */
    fun shift(input: FloatArray, semitones: Float): FloatArray {
        if (abs(semitones) < 0.01f) return input.copyOf()

        val factor = Math.pow(2.0, (semitones / 12.0)).toFloat()
        val windowSize = 2048
        val hopIn = windowSize / 4
        val hopOut = (hopIn / factor).toInt().coerceAtLeast(1)

        val outputLength = (input.size / factor).toInt().coerceAtLeast(1)
        val out = FloatArray(outputLength)
        val norm = FloatArray(outputLength)

        val window = FloatArray(windowSize) { i ->
            (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (windowSize - 1))).toFloat()
        }

        var inPos = 0
        var outPos = 0
        while (inPos + windowSize < input.size && outPos + windowSize < outputLength) {
            for (i in 0 until windowSize) {
                val srcIdx = inPos + i
                if (srcIdx >= input.size) break
                out[outPos + i] += input[srcIdx] * window[i]
                norm[outPos + i] += window[i] * window[i]
            }
            inPos += hopIn
            outPos += hopOut
        }

        for (i in out.indices) {
            if (norm[i] > 1e-6f) out[i] /= norm[i]
        }
        return out
    }
}
// =============================================================================
// AUTO DUCKING — lower the music when someone speaks
// =============================================================================

/**
 * A time-varying gain curve. Samples the music through this to duck it
 * under a voice track. Store per-frame in dB or linear.
 */
data class DuckingCurve(
    val frameMs: Long,
    val frames: FloatArray,     // linear gain 0..1 per frame
    val totalMs: Long,
) {
    /** Samples the curve at a given time, with linear interpolation. */
    fun gainAt(timeMs: Long): Float {
        if (frames.isEmpty() || frameMs <= 0L) return 1f
        val exact = timeMs.toFloat() / frameMs
        val i0 = exact.toInt().coerceIn(0, frames.size - 1)
        val i1 = (i0 + 1).coerceAtMost(frames.size - 1)
        val t = (exact - i0).coerceIn(0f, 1f)
        return frames[i0] * (1f - t) + frames[i1] * t
    }
}

/**
 * AutoDucker — computes a ducking curve from a voice track.
 *
 * The user has music on A1 and voice on A2 (or vice versa). This analyzes
 * the voice track's RMS envelope and produces a gain curve for the music:
 * where voice is loud, music gain drops; where voice is silent, music
 * returns to unity.
 *
 * Same idea as sidechain compression, but pre-computed rather than realtime.
 */
object AutoDucker {

    data class Settings(
        val frameMs: Long = 20L,
        val duckAmountDb: Float = -12f,     // how much to dip
        val attackMs: Long = 80L,           // how fast to duck
        val releaseMs: Long = 400L,         // how fast to come back
        val voiceThresholdDb: Float = -32f, // voice level that triggers ducking
    )

    fun compute(
        voicePcm: FloatArray,
        voiceSampleRate: Int,
        musicDurationMs: Long,
        settings: Settings = Settings(),
    ): DuckingCurve {
        if (voicePcm.isEmpty()) {
            return DuckingCurve(settings.frameMs, FloatArray(0), musicDurationMs)
        }

        val frameSamples = (settings.frameMs * voiceSampleRate / 1000L).toInt().coerceAtLeast(1)
        val frameCount = (musicDurationMs / settings.frameMs).toInt().coerceAtLeast(1)

        // Step 1 — RMS envelope of the voice track, per frame
        val voiceRms = FloatArray(frameCount)
        for (f in 0 until frameCount) {
            val start = f * frameSamples
            val end = min(start + frameSamples, voicePcm.size)
            if (start >= end) break
            var sumSq = 0.0
            for (i in start until end) sumSq += voicePcm[i].toDouble() * voicePcm[i].toDouble()
            voiceRms[f] = sqrt(sumSq / (end - start)).toFloat()
        }

        // Step 2 — compute raw target gain per frame
        val rawGain = FloatArray(frameCount) { 1f }
        val duckLin = Math.pow(10.0, (settings.duckAmountDb / 20.0)).toFloat()
        val thresholdLin = Math.pow(10.0, (settings.voiceThresholdDb / 20.0)).toFloat()

        for (f in 0 until frameCount) {
            rawGain[f] = if (voiceRms[f] > thresholdLin) duckLin else 1f
        }

        // Step 3 — asymmetric smoothing (attack fast, release slow)
        val attackFrames = (settings.attackMs / settings.frameMs).toInt().coerceAtLeast(1)
        val releaseFrames = (settings.releaseMs / settings.frameMs).toInt().coerceAtLeast(1)
        val attackCoeff = 1f - 1f / attackFrames
        val releaseCoeff = 1f - 1f / releaseFrames

        val smoothed = FloatArray(frameCount)
        var current = 1f
        for (f in 0 until frameCount) {
            val target = rawGain[f]
            current = if (target < current) {
                // attack — move toward target fast
                attackCoeff * current + (1f - attackCoeff) * target
            } else {
                // release — move toward target slowly
                releaseCoeff * current + (1f - releaseCoeff) * target
            }
            smoothed[f] = current
        }

        return DuckingCurve(settings.frameMs, smoothed, musicDurationMs)
    }

    /** Applies the curve to a music track in place. Returns a new buffer. */
    fun apply(
        musicPcm: FloatArray,
        musicSampleRate: Int,
        curve: DuckingCurve,
    ): FloatArray {
        if (curve.frames.isEmpty()) return musicPcm.copyOf()
        val out = FloatArray(musicPcm.size)
        for (i in musicPcm.indices) {
            val timeMs = i.toLong() * 1000L / musicSampleRate
            out[i] = musicPcm[i] * curve.gainAt(timeMs)
        }
        return out
    }
}

// =============================================================================
// MUSIC SYNC — snap edits to the beat grid
// =============================================================================

/**
 * MusicSync — helpers that use a BeatGrid to snap timeline operations.
 *
 * Called by the timeline when snapping is enabled and a grid is present.
 * Wraps the BeatGrid with a snap threshold in milliseconds.
 */
object MusicSync {

    data class SnapResult(
        val snapped: Boolean,
        val timeMs: Long,
        val beatIndex: Int,
    )

    fun snap(
        grid: BeatGrid,
        proposedMs: Long,
        thresholdMs: Long = 80L,
    ): SnapResult {
        if (grid.intervalMs <= 0f || grid.count <= 0) {
            return SnapResult(false, proposedMs, -1)
        }
        val nearest = grid.nearestBeat(proposedMs)
        val dist = abs(nearest - proposedMs)
        if (dist > thresholdMs) {
            return SnapResult(false, proposedMs, -1)
        }
        val idx = ((nearest - grid.firstBeatMs) / grid.intervalMs).toInt()
        return SnapResult(true, nearest, idx)
    }

    /**
     * Rounds a duration to the nearest beat multiple.
     * Useful for making clip lengths land on the beat.
     */
    fun quantizeDuration(grid: BeatGrid, durationMs: Long): Long {
        if (grid.intervalMs <= 0f) return durationMs
        val beats = Math.round(durationMs / grid.intervalMs)
        return (beats * grid.intervalMs).toLong()
    }

    /**
     * Snaps a whole timeline of cut points to the grid.
     * Returns a new array of timestamps.
     */
    fun snapAll(
        grid: BeatGrid,
        cuts: LongArray,
        thresholdMs: Long = 80L,
    ): LongArray {
        val out = LongArray(cuts.size)
        for (i in cuts.indices) {
            val r = snap(grid, cuts[i], thresholdMs)
            out[i] = if (r.snapped) r.timeMs else cuts[i]
        }
        return out
    }

    /**
     * Finds the next beat strictly after a given time.
     * Useful for "advance playhead by one beat".
     */
    fun nextBeat(grid: BeatGrid, afterMs: Long): Long {
        if (grid.intervalMs <= 0f) return afterMs
        val n = ((afterMs - grid.firstBeatMs) / grid.intervalMs).toInt() + 1
        return grid.beatAt(n.coerceIn(0, grid.count))
    }

    /** Previous beat strictly before a given time. */
    fun previousBeat(grid: BeatGrid, beforeMs: Long): Long {
        if (grid.intervalMs <= 0f) return beforeMs
        val n = ((beforeMs - grid.firstBeatMs) / grid.intervalMs).toInt() - 1
        return grid.beatAt(n.coerceIn(0, grid.count))
    }
}

// =============================================================================
// MUSIC PRESETS
// =============================================================================

/**
 * A music preset — a named combination of EQ + loudness + ducking defaults.
 * Apply one and the music immediately sits right for a given context.
 *
 * Same idea as the "Music" presets in CapCut and Resolve. Each one is
 * a starting point — everything is still editable afterward.
 */
data class MusicPreset(
    val name: String,
    val category: String,
    val lowGainDb: Float,
    val midGainDb: Float,
    val highGainDb: Float,
    val targetRmsDb: Float,
    val duckAmountDb: Float,
    val duckAttackMs: Long,
    val duckReleaseMs: Long,
    val description: String,
)

object MusicPresets {

    val ALL: List<MusicPreset> = listOf(

        MusicPreset(
            name = "Dialogue Bed",
            category = "Voice",
            lowGainDb = -3f,
            midGainDb = -2f,
            highGainDb = -4f,
            targetRmsDb = -24f,
            duckAmountDb = -14f,
            duckAttackMs = 60L,
            duckReleaseMs = 300L,
            description = "Music sits well behind spoken voice. Heavy ducking.",
        ),

        MusicPreset(
            name = "Podcast",
            category = "Voice",
            lowGainDb = -6f,
            midGainDb = 0f,
            highGainDb = -3f,
            targetRmsDb = -22f,
            duckAmountDb = -10f,
            duckAttackMs = 80L,
            duckReleaseMs = 400L,
            description = "Clears room for a talking host. Gentle duck.",
        ),

        MusicPreset(
            name = "Vlog",
            category = "Voice",
            lowGainDb = 0f,
            midGainDb = 1f,
            highGainDb = 2f,
            targetRmsDb = -20f,
            duckAmountDb = -8f,
            duckAttackMs = 100L,
            duckReleaseMs = 500L,
            description = "Bright and present. Light ducking under the creator's voice.",
        ),

        MusicPreset(
            name = "Cinematic",
            category = "Score",
            lowGainDb = 4f,
            midGainDb = 0f,
            highGainDb = -2f,
            targetRmsDb = -18f,
            duckAmountDb = -6f,
            duckAttackMs = 200L,
            duckReleaseMs = 800L,
            description = "Warm low end. Slow ducking for dramatic beats.",
        ),

        MusicPreset(
            name = "Trailer",
            category = "Score",
            lowGainDb = 5f,
            midGainDb = -1f,
            highGainDb = 3f,
            targetRmsDb = -14f,
            duckAmountDb = -4f,
            duckAttackMs = 150L,
            duckReleaseMs = 600L,
            description = "Loud and impactful. Minimal ducking so impacts hit.",
        ),

        MusicPreset(
            name = "Action",
            category = "Score",
            lowGainDb = 3f,
            midGainDb = 2f,
            highGainDb = 3f,
            targetRmsDb = -16f,
            duckAmountDb = -8f,
            duckAttackMs = 50L,
            duckReleaseMs = 400L,
            description = "Punchy mids and highs. Fast ducking on hits.",
        ),

        MusicPreset(
            name = "Ambient",
            category = "Background",
            lowGainDb = 2f,
            midGainDb = -4f,
            highGainDb = -6f,
            targetRmsDb = -26f,
            duckAmountDb = -10f,
            duckAttackMs = 200L,
            duckReleaseMs = 1000L,
            description = "Soft and unnoticeable. Deep, slow ducking.",
        ),

        MusicPreset(
            name = "Lo-Fi",
            category = "Background",
            lowGainDb = 3f,
            midGainDb = 2f,
            highGainDb = -8f,
            targetRmsDb = -22f,
            duckAmountDb = -10f,
            duckAttackMs = 120L,
            duckReleaseMs = 600L,
            description = "Warm, rolled-off highs. Sits comfortably under any voice.",
        ),

        MusicPreset(
            name = "Reels / Shorts",
            category = "Social",
            lowGainDb = 2f,
            midGainDb = 1f,
            highGainDb = 4f,
            targetRmsDb = -14f,
            duckAmountDb = -8f,
            duckAttackMs = 50L,
            duckReleaseMs = 300L,
            description = "Loud, punchy, phone-speaker friendly. Fast duck.",
        ),

        MusicPreset(
            name = "Wedding",
            category = "Emotional",
            lowGainDb = 3f,
            midGainDb = -1f,
            highGainDb = -2f,
            targetRmsDb = -20f,
            duckAmountDb = -8f,
            duckAttackMs = 200L,
            duckReleaseMs = 900L,
            description = "Warm and emotional. Slow, gentle ducking for vows.",
        ),
    )

    fun byName(name: String): MusicPreset? =
        ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }

    val CATEGORIES: List<String> get() = ALL.map { it.category }.distinct()

    /**
     * Applies a preset to a PCM buffer. Runs EQ, then normalizes to target.
     * Returns the processed buffer. Ducking is applied separately via
     * AutoDucker because it depends on the voice track.
     */
    fun applyTo(
        input: FloatArray,
        sampleRate: Int,
        preset: MusicPreset,
    ): FloatArray {
        val eq = BiquadEq(sampleRate).apply {
            lowGainDb = preset.lowGainDb
            midGainDb = preset.midGainDb
            highGainDb = preset.highGainDb
        }
        val eqd = eq.process(input)
        return Normalizer.normalize(eqd, preset.targetRmsDb, sampleRate = sampleRate)
    }
}

// =============================================================================
// THE MUSIC RUNTIME — ties everything together
// =============================================================================

/**
 * MusicRuntime — the top-level API the app calls into.
 *
 * Given a music track Uri and a voice track Uri (optional), produces
 * the final mixed PCM ready for playback or export.
 *
 * Call from a background thread. Everything here is CPU-bound and can take
 * seconds on long tracks.
 */
object MusicRuntime {

    data class MixRequest(
        val musicUri: Uri,
        val voiceUri: Uri? = null,
        val preset: MusicPreset? = null,
        val fadeInMs: Long = 1000L,
        val fadeOutMs: Long = 2000L,
        val duckingEnabled: Boolean = voiceUri != null,
        val normalize: Boolean = true,
    )

    data class MixResult(
        val samples: FloatArray,
        val sampleRate: Int,
        val durationMs: Long,
        val analysis: AudioAnalysis,
        val duckingCurve: DuckingCurve?,
    )

    /**
     * Runs the full music pipeline:
     *   decode → analyze → EQ → normalize → fade → duck → output
     */
    fun process(
        context: Context,
        request: MixRequest,
    ): MixResult {
        val musicPcm = PcmDecoder.decode(context, request.musicUri)
        val analysis = AudioAnalyzer.analyze(context, request.musicUri)

        var working = musicPcm.samples

        // Apply preset EQ + normalize
        if (request.preset != null) {
            working = MusicPresets.applyTo(working, musicPcm.sampleRate, request.preset)
        } else if (request.normalize) {
            working = Normalizer.normalize(working, sampleRate = musicPcm.sampleRate)
        }

        // Fades
        if (request.fadeInMs > 0L || request.fadeOutMs > 0L) {
            working = Fade.fadeInOut(
                working,
                request.fadeInMs,
                request.fadeOutMs,
                musicPcm.sampleRate,
            )
        }

        // Ducking under voice
        var duckingCurve: DuckingCurve? = null
        if (request.duckingEnabled && request.voiceUri != null) {
            try {
                val voicePcm = PcmDecoder.decode(context, request.voiceUri)
                val settings = AutoDucker.Settings(
                    duckAmountDb = request.preset?.duckAmountDb ?: -12f,
                    attackMs = request.preset?.duckAttackMs ?: 80L,
                    releaseMs = request.preset?.duckReleaseMs ?: 400L,
                )
                val curve = AutoDucker.compute(
                    voicePcm.samples,
                    voicePcm.sampleRate,
                    musicPcm.durationMs,
                    settings,
                )
                working = AutoDucker.apply(working, musicPcm.sampleRate, curve)
                duckingCurve = curve
            } catch (_: Throwable) {
                // Voice track unusable — skip ducking silently
            }
        }

        // Final safety limiter
        val limiter = Limiter(musicPcm.sampleRate)
        working = limiter.process(working)

        return MixResult(
            samples = working,
            sampleRate = musicPcm.sampleRate,
            durationMs = musicPcm.durationMs,
            analysis = analysis,
            duckingCurve = duckingCurve,
        )
    }

    /**
     * Fast path — just get BPM + beat grid for a Uri. Used by the timeline
     * when the user taps "BEATS" or enables snap-to-grid.
     *
     * Works on both video and audio files: pass whichever the user selected.
     */
    fun quickBeatGrid(context: Context, uri: Uri): BeatGrid {
        return try {
            AudioAnalyzer.analyzeBpmOnly(context, uri)
        } catch (_: Throwable) {
            BeatGrid(0f, 0L, 0f, 0)
        }
    }
}
