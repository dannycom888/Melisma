package com.melisma.app.lyrics.provider

import com.melisma.app.lyrics.model.LyricsDocument
import com.melisma.app.lyrics.parse.LrcParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lyrics provider backed by the user's own `.lrc` files in a folder they pick
 * once in Settings.
 *
 * This is the only lyrics provider in this build: it never contacts the
 * network. When no local file matches the playing track it returns null, and
 * the app shows no lyrics.
 *
 * Matching is audio-anchored first: the playing track's own audio file is
 * found in the folder (its stem must contain the title and one of the
 * artists), and the `.lrc` with the identical stem is used — the pairing
 * rule Poweramp uses. When no audio file is found, or the audio file has no
 * same-stem `.lrc`, matching falls back to the `.lrc` file names:
 * `"Artist - Title.lrc"` is preferred, `"Title.lrc"` is the fallback; both
 * are normalized (case, spacing and punctuation are ignored). When several
 * files match, the one whose last lyric timestamp is closest to the track
 * duration wins. `.lrc` files are decoded as UTF-8 first and as GBK when
 * that fails. Parsing reuses this app's own [LrcParser], which already
 * handles `[offset:]` and enhanced-LRC word timings.
 *
 * Ported from the local-only AALyrics build's `LocalLyricsProvider`.
 */
class LocalFolderLyricsProvider(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** True once the user has picked a folder; drives [isConfigured]. */
    private val hasFolder: () -> Boolean = { true },
) : LyricsProvider {

    override val id = PROVIDER_ID
    override val displayName = "Local files"
    override val canBeWordSynced = true

    override val isConfigured: Boolean get() = hasFolder()

    override var noMatchReason: String? = null

    private val indexMutex = Mutex()

    @Volatile
    private var source: LocalFolderSource? = null

    @Volatile
    private var cachedIndex: LocalIndex? = null

    /** Attaches (or replaces) the folder source. Re-attaching invalidates the cached index. */
    fun attach(source: LocalFolderSource?) {
        this.source = source
        invalidate()
    }

    /** Drops the cached file index so the next search rescans the folder. */
    fun invalidate() {
        cachedIndex = null
    }

    /** Rebuilds the file index immediately (used by the Settings rescan action). */
    suspend fun refreshIndex() {
        invalidate()
        ensureIndex()
    }

    /** Number of `.lrc` files in the current index, or null when no index has been built yet. */
    suspend fun indexedFileCount(): Int? = ensureIndex()?.lrc?.size

    override suspend fun fetch(request: LyricsRequest): LyricsDocument? {
        noMatchReason = null
        val index = ensureIndex()
        if (index == null) {
            noMatchReason = "No lyrics folder picked yet"
            return null
        }
        val title = request.title
        val artists = (listOf(request.artist) + request.allArtists)
            .filter { it.isNotBlank() }
            .distinct()

        // 1. Audio-anchored: the playing track's own audio file, then the
        //    `.lrc` with the identical stem (the pairing rule Poweramp uses).
        val audioFile = LocalFolderMatcher.findAudioFile(title, artists, index.audio)
        if (audioFile != null) {
            val lrcEntry = LocalFolderMatcher.sameStemLrc(audioFile, index.lrc)
            if (lrcEntry != null) {
                parseFile(lrcEntry.file, request)?.let { return it }
            }
        }

        // 2. Fallback: metadata matching against `.lrc` file names.
        val matched = LocalFolderMatcher.match(title, artists, index.lrc)
        if (matched.isEmpty()) {
            noMatchReason = "No .lrc file in the folder matches this track"
            return null
        }

        val parsed = mutableListOf<Pair<LocalFolderMatcher.IndexedLrcFile, LyricsDocument>>()
        for (entry in matched.take(MAX_FILES_TO_PARSE)) {
            parseFile(entry.file, request)?.let { parsed += entry to it }
        }
        if (parsed.isEmpty()) {
            noMatchReason = "Matched .lrc files could not be read"
            return null
        }

        val winner = LocalFolderMatcher.pickWinner(
            title = title,
            artists = artists,
            durationMs = request.durationMs,
            parsed = parsed.map { (entry, document) ->
                entry to document.lines.maxOfOrNull { it.startMs }?.toLong()
            },
        ) ?: return null
        return parsed.first { it.first == winner }.second
    }

    /** The cached folder index: indexed `.lrc` files plus indexed audio files. */
    private data class LocalIndex(
        val lrc: List<LocalFolderMatcher.IndexedLrcFile>,
        val audio: List<LocalFolderMatcher.IndexedAudioFile>,
    )

    private suspend fun ensureIndex(): LocalIndex? {
        cachedIndex?.let { return it }
        val folderSource = source ?: return null
        return indexMutex.withLock {
            cachedIndex?.let { return it }
            val lrcFiles = withContext(ioDispatcher) { folderSource.listLrcFiles() }
            val audioFiles = withContext(ioDispatcher) { folderSource.listAudioFiles() }
            val index = LocalIndex(
                lrc = lrcFiles.map { file ->
                    LocalFolderMatcher.IndexedLrcFile(
                        file = file,
                        keys = LocalFolderMatcher.fileKeys(file.displayName),
                    )
                },
                audio = audioFiles.map { file ->
                    LocalFolderMatcher.IndexedAudioFile(
                        file = file,
                        normalizedStem = LocalFolderMatcher.fileStem(file.displayName),
                    )
                },
            )
            cachedIndex = index
            index
        }
    }

    private suspend fun parseFile(file: LocalLrcFile, request: LyricsRequest): LyricsDocument? {
        val bytes = withContext(ioDispatcher) { source?.readBytes(file) } ?: return null
        val text = LocalFolderMatcher.decodeBytes(bytes)
        // The app's own parser handles [offset:] and enhanced-LRC word
        // timings, so no extra shifting is needed here.
        LrcParser.toDocument(text, displayName, id, request.durationMs)?.let { return it }
        // Untimed file: show it as static lyrics rather than nothing, as the
        // import store does — but only when the file carries no time tags at
        // all, so a timed file that failed to parse is not shown raw.
        if (TIME_TAG.containsMatchIn(text)) return null
        return LrcParser.plainToDocument(text, displayName, id)
    }

    companion object {
        const val PROVIDER_ID = "local-folder"

        /** At most this many matching files are read and parsed per lookup. */
        private const val MAX_FILES_TO_PARSE = 8

        private val TIME_TAG = Regex("""\[\d{1,3}:\d{1,2}""")
    }
}
