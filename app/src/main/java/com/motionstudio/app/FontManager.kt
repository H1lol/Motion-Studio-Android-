package com.motionstudio.app

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors

/**
 * FontManager — the font catalog.
 *
 * Two sources:
 *   1. Bundled fonts from assets/fonts/ — shipped inside the APK
 *   2. User imports copied to filesDir/fonts/ — persist across sessions
 *
 * The bundled catalog is discovered by scanning assets/fonts/ at startup.
 * Each .ttf / .otf file becomes a FontEntry. There is no metadata file —
 * the filename IS the display name ("Inter-Bold.ttf" → "Inter Bold").
 *
 * User imports go through SAF (ACTION_OPEN_DOCUMENT), get copied into
 * private storage, and are remembered across launches via a simple
 * newline-separated index file.
 *
 * All disk I/O happens on a background thread. Callbacks fire on the
 * main thread.
 */
class FontManager(private val context: Context) {

    // -------------------------------------------------------------------------
    // Data
    // -------------------------------------------------------------------------
    data class FontEntry(
        val id: Long,
        val name: String,
        val path: String,
        val isBundled: Boolean,
        val typeface: Typeface,
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private val bundled = mutableListOf<FontEntry>()
    private val imported = mutableListOf<FontEntry>()
    private var nextId = 1L
    private var loaded = false

    /** All fonts, bundled first, then imported. */
    val all: List<FontEntry>
        get() = bundled + imported

    val size: Int get() = bundled.size + imported.size

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Scans bundled assets and reloads imported fonts from disk.
     * Safe to call multiple times — the second call is a no-op.
     */
    fun load(onReady: (() -> Unit)? = null) {
        if (loaded) {
            onReady?.let { ui.post(it) }
            return
        }
        executor.execute {
            try {
                scanBundled()
                loadImportedIndex()
                loaded = true
            } catch (_: Throwable) {
                loaded = true
            }
            onReady?.let { ui.post(it) }
        }
    }

    private fun scanBundled() {
        bundled.clear()
        try {
            val names = context.assets.list("fonts") ?: return
            for (fileName in names.sorted()) {
                if (!isFontFile(fileName)) continue
                try {
                    val typeface = Typeface.createFromAsset(context.assets, "fonts/$fileName")
                    bundled.add(
                        FontEntry(
                            id = nextId++,
                            name = prettify(fileName),
                            path = "fonts/$fileName",
                            isBundled = true,
                            typeface = typeface,
                        )
                    )
                } catch (_: Throwable) {
                    // Corrupt or unsupported font — skip
                }
            }
        } catch (_: Throwable) {
            // No assets/fonts/ folder — user imports only
        }
    }

    private fun loadImportedIndex() {
        imported.clear()
        val indexFile = File(context.filesDir, "fonts/index.txt")
        if (!indexFile.exists()) return
        val fontDir = File(context.filesDir, "fonts")
        if (!fontDir.exists()) return

        try {
            indexFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { fileName ->
                    val f = File(fontDir, fileName)
                    if (!f.exists()) return@forEach
                    try {
                        val tf = Typeface.createFromFile(f)
                        imported.add(
                            FontEntry(
                                id = nextId++,
                                name = prettify(fileName),
                                path = f.absolutePath,
                                isBundled = false,
                                typeface = tf,
                            )
                        )
                    } catch (_: Throwable) {
                        // Skip broken file
                    }
                }
        } catch (_: Throwable) {}
    }

    // -------------------------------------------------------------------------
    // Importing
    // -------------------------------------------------------------------------

    /**
     * Imports a .ttf / .otf file from SAF. Copies it into private storage,
     * adds it to the catalog, and calls `onDone` on the main thread.
     *
     * Called from MainActivity.onActivityResult when REQ_FONT_IMPORT fires.
     */
    fun import(uri: Uri, onDone: () -> Unit) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Throwable) {}

        val displayName = queryDisplayName(uri) ?: "Custom_${System.currentTimeMillis()}.ttf"
        val safeName = sanitize(displayName)

        executor.execute {
            try {
                val fontDir = File(context.filesDir, "fonts").apply { mkdirs() }
                val outFile = File(fontDir, safeName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot read font")

                val typeface = Typeface.createFromFile(outFile)

                ui.post {
                    imported.add(
                        FontEntry(
                            id = nextId++,
                            name = prettify(safeName),
                            path = outFile.absolutePath,
                            isBundled = false,
                            typeface = typeface,
                        )
                    )
                    saveImportedIndex()
                    onDone()
                }
            } catch (_: Throwable) {
                ui.post { onDone() }
            }
        }
    }

    /** Deletes a user-imported font. Bundled fonts cannot be deleted. */
    fun remove(entry: FontEntry): Boolean {
        if (entry.isBundled) return false
        val file = File(entry.path)
        val removed = imported.removeAll { it.id == entry.id }
        if (removed) {
            try { file.delete() } catch (_: Throwable) {}
            saveImportedIndex()
        }
        return removed
    }

    private fun saveImportedIndex() {
        try {
            val dir = File(context.filesDir, "fonts").apply { mkdirs() }
            val indexFile = File(dir, "index.txt")
            indexFile.writeText(
                imported.joinToString("\n") { File(it.path).name }
            )
        } catch (_: Throwable) {}
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    fun byId(id: Long): FontEntry? = all.firstOrNull { it.id == id }

    fun byName(name: String): FontEntry? =
        all.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** First matching font or DEFAULT_BOLD if nothing matches. */
    fun typefaceFor(name: String?): Typeface {
        if (name == null) return Typeface.DEFAULT_BOLD
        return byName(name)?.typeface ?: Typeface.DEFAULT_BOLD
    }

    /**
     * Returns all fonts whose name contains the query (case-insensitive).
     * Empty query returns the full catalog.
     */
    fun search(query: String): List<FontEntry> {
        if (query.isBlank()) return all
        val q = query.lowercase(Locale.US)
        return all.filter { it.name.lowercase(Locale.US).contains(q) }
    }

    /**
     * Bundled-only list. Useful for pickers that filter out user imports.
     */
    fun bundled(): List<FontEntry> = bundled.toList()

    /**
     * Imported-only list.
     */
    fun imported(): List<FontEntry> = imported.toList()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun isFontFile(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.endsWith(".ttf") || lower.endsWith(".otf")
    }

    /**
     * "Inter-Bold.ttf" → "Inter Bold"
     * "roboto_mono.otf" → "Roboto Mono"
     * "myCustomFont-v2.ttf" → "My Custom Font V2"
     */
    private fun prettify(fileName: String): String {
        val base = fileName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercaseChar() }
            }
        return if (base.isBlank()) fileName else base
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .let { if (it.length > 80) it.take(80) else it }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == android.content.ContentResolver.SCHEME_CONTENT) {
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

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    fun shutdown() {
        executor.shutdownNow()
    }
}
