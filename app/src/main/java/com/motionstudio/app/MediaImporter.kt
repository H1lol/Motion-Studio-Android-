package com.motionstudio.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.util.Locale

/**
 * MediaImporter — small utility for inspecting a picked media file.
 *
 * Called from MainActivity.importAsset() to fill in the MediaAsset fields
 * (name, size, duration, kind) after the user picks a URI via SAF.
 *
 * All methods are safe to call from a background thread. They never throw —
 * a failure returns a sensible default (0L for size/duration, UNKNOWN for kind).
 */
object MediaImporter {

    /**
     * Determines AssetKind by MIME first, then by file extension fallback.
     * MIME is authoritative when available, but SAF providers occasionally
     * return null or "application/octet-stream".
     */
    fun detectKind(context: Context, uri: Uri): AssetKind {
        val mime = safeMime(context, uri)
        if (mime != null) {
            when {
                mime.startsWith("video/") -> return AssetKind.VIDEO
                mime.startsWith("image/") -> return AssetKind.IMAGE
                mime.startsWith("audio/") -> return AssetKind.AUDIO
            }
        }
        val name = displayName(context, uri)?.lowercase(Locale.US) ?: return AssetKind.UNKNOWN
        val ext = name.substringAfterLast('.', "")
        return when (ext) {
            "mp4", "mov", "mkv", "webm", "avi", "3gp", "m4v", "flv", "ts" -> AssetKind.VIDEO
            "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif" -> AssetKind.IMAGE
            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "wma" -> AssetKind.AUDIO
            else -> AssetKind.UNKNOWN
        }
    }

    /**
     * Human-readable display name. Prefers the content provider's
     * OpenableColumns.DISPLAY_NAME; falls back to the URI's last path segment.
     */
    fun displayName(context: Context, uri: Uri): String? {
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

    /**
     * File size in bytes. Returns 0 if the provider doesn't expose it.
     */
    fun size(context: Context, uri: Uri): Long {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.SIZE),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
                }
            } catch (_: Throwable) {}
        }
        return 0L
    }

    /**
     * Media duration in milliseconds. Images return 5000 by convention
     * (a still image becomes a 5-second clip on the timeline).
     *
     * Uses MediaMetadataRetriever which can block; call from a background
     * thread. Failure returns 0.
     */
    fun durationMs(context: Context, uri: Uri): Long {
        val kind = detectKind(context, uri)
        if (kind == AssetKind.IMAGE) return 5000L

        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val dur = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            dur
        } catch (_: Throwable) {
            0L
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------
    private fun safeMime(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.getType(uri)?.lowercase(Locale.US)
                ?: MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(
                        uri.lastPathSegment?.substringAfterLast('.', "") ?: ""
                    )?.lowercase(Locale.US)
        } catch (_: Throwable) {
            null
        }
    }
}
