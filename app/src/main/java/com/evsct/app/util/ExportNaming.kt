package com.evsct.app.util

import com.evsct.app.BuildConfig
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Names for the files the app hands to the user — backup zips and CSV
 * exports — across all four producers: the two SAF `CreateDocument` pickers
 * in Settings and the two `prepareShareFile` paths behind the share sheet.
 * They each used to build their own timestamp, and had already drifted (the
 * CSV picker was emitting raw epoch millis while the other three used a
 * readable stamp).
 *
 * Shape: `evsct-backup-2026-07-31-1432-b247-a1b2c3d.zip`
 *   prefix    – what the file is
 *   timestamp – stays immediately after the prefix so a file manager sorting
 *               by name also sorts chronologically; version tokens must
 *               trail it or that ordering breaks
 *   build tag – which build wrote it, so a support report months later
 *               ("restore fails on this zip") identifies its producer
 *
 * The filename is only ever a hint: SAF treats a `CreateDocument` name as a
 * suggestion the user can edit, Android de-dupes collisions into
 * `foo (1).zip`, and share targets rewrite names freely. The durable copy of
 * the same provenance is written inside `backup.json` (see
 * `BackupIo.buildBackupJson`). A CSV has nowhere to put metadata without
 * breaking every consumer, so there the filename is the only carrier.
 */
object ExportNaming {

    const val BACKUP_PREFIX = "evsct-backup"
    const val CSV_PREFIX = "evsct-export"

    /** Shared and thread-safe, unlike the four `SimpleDateFormat` instances
     *  this replaced. The zone is resolved per call rather than frozen here,
     *  so a device that changes timezone mid-process still stamps local time
     *  — same reasoning as [com.evsct.app.data.csv.CsvFormat]. Locale.US
     *  pins digit shapes so the name stays ASCII under non-Latin numbering. */
    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.US)

    /** Anything outside this set collapses to '-' before reaching a filename.
     *  Both inputs are already tame — a decimal versionCode and a hex sha
     *  with an optional "-dirty" suffix — so this exists to guarantee a
     *  future BuildConfig field can't smuggle a path separator into a name
     *  the app then writes to disk. */
    private val UNSAFE = Regex("[^A-Za-z0-9]+")

    fun timestamp(at: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String =
        TIMESTAMP.format(LocalDateTime.ofInstant(at, zone))

    /**
     * Version tokens for the running build: `b247-a1b2c3d`, or
     * `b247-a1b2c3d-dirty` when the build carried uncommitted changes —
     * worth keeping, since it says the artifact isn't reproducible from any
     * commit.
     *
     * `versionCode` is the git commit count (see app/build.gradle.kts), so it
     * is the token that actually varies build to build; `versionName` is
     * deliberately not used here because it is pinned at "0.1.0" and would
     * be pure noise in every filename. It is still recorded inside
     * backup.json, where space is free and a future bump makes it useful.
     *
     * Degrades to the versionCode alone when the build had no git available
     * and GIT_SHA is the literal "unknown".
     */
    fun buildTag(
        versionCode: Int = BuildConfig.VERSION_CODE,
        gitSha: String = BuildConfig.GIT_SHA,
    ): String {
        val sha = gitSha.trim().replace(UNSAFE, "-").trim('-')
        return if (sha.isBlank() || sha.startsWith("unknown")) "b$versionCode" else "b$versionCode-$sha"
    }

    /** Full export name: prefix, timestamp, build tag, extension. [ext] is
     *  given without a leading dot ("zip", "csv"). */
    fun fileName(
        prefix: String,
        ext: String,
        at: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        buildTag: String = buildTag(),
    ): String = "$prefix-${timestamp(at, zone)}-$buildTag.$ext"

    fun backupFileName(): String = fileName(BACKUP_PREFIX, "zip")

    fun csvFileName(): String = fileName(CSV_PREFIX, "csv")
}
