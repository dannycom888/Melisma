package com.melisma.app.lyrics.provider

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One `.lrc` file found under the configured local lyrics folder. */
data class LocalLrcFile(
    /** String form of the file's content URI; kept as a String so the provider stays JVM-testable. */
    val uriString: String,
    val displayName: String,
)

/** One audio file found under the configured local lyrics folder. */
data class LocalAudioFile(
    /** String form of the file's content URI; kept as a String so the provider stays JVM-testable. */
    val uriString: String,
    val displayName: String,
)

/** Audio file extensions indexed alongside `.lrc` files for audio-anchored matching. */
private val AUDIO_EXTENSIONS = setOf(
    ".mp3", ".flac", ".wav", ".aac", ".m4a", ".m4b", ".ogg", ".oga", ".opus",
    ".wma", ".ape", ".aiff", ".aif", ".aifc", ".dff", ".dsf", ".mka", ".tak",
    ".tta", ".wv", ".mpc", ".mp2", ".3gp",
)

/**
 * Access to the user's chosen local lyrics folder.
 *
 * The provider only depends on this interface; the Android implementation
 * below reads through the Storage Access Framework, so the folder only needs
 * to have been picked once and the grant persisted.
 *
 * Ported from the local-only AALyrics build's `LocalLyricsFolderSource`.
 */
interface LocalFolderSource {
    /** All `.lrc` files under the configured folder, found recursively. Empty when no folder is set. */
    suspend fun listLrcFiles(): List<LocalLrcFile>

    /** All audio files under the configured folder, found recursively. Empty when no folder is set. */
    suspend fun listAudioFiles(): List<LocalAudioFile> = emptyList()

    /**
     * One walk collecting `.lrc` files and audio files together.
     *
     * The default walks twice (once per list); the SAF implementation walks
     * once. An index build does two full folder traversals otherwise, which
     * is what makes a big folder outrun the provider's lookup timeout.
     */
    suspend fun listAll(): Pair<List<LocalLrcFile>, List<LocalAudioFile>> =
        listLrcFiles() to listAudioFiles()

    /** Reads one file's raw bytes, or null when it cannot be opened. */
    suspend fun readBytes(file: LocalLrcFile): ByteArray?
}

/**
 * SAF-backed [LocalFolderSource].
 *
 * @param folderUri supplies the currently configured folder's tree URI, or
 *   null when the user has not picked one yet.
 */
class DocumentFileLocalFolderSource(
    context: Context,
    private val folderUri: () -> Uri?,
) : LocalFolderSource {
    private val appContext = context.applicationContext

    override suspend fun listLrcFiles(): List<LocalLrcFile> = withContext(Dispatchers.IO) {
        listAll().first
    }

    override suspend fun listAudioFiles(): List<LocalAudioFile> = withContext(Dispatchers.IO) {
        listAll().second
    }

    /** One recursive walk collecting `.lrc` files and audio files together. */
    override suspend fun listAll(): Pair<List<LocalLrcFile>, List<LocalAudioFile>> =
        withContext(Dispatchers.IO) {
            collectFiles()
        }

    /** One recursive walk collecting `.lrc` files and audio files together. */
    private fun collectFiles(): Pair<List<LocalLrcFile>, List<LocalAudioFile>> {
        val rootUri = folderUri() ?: return emptyList<LocalLrcFile>() to emptyList()
        val root = DocumentFile.fromTreeUri(appContext, rootUri)
            ?: return emptyList<LocalLrcFile>() to emptyList()
        val lrc = mutableListOf<LocalLrcFile>()
        val audio = mutableListOf<LocalAudioFile>()
        collectFiles(root, lrc, audio)
        return lrc to audio
    }

    private fun collectFiles(
        directory: DocumentFile,
        lrcOut: MutableList<LocalLrcFile>,
        audioOut: MutableList<LocalAudioFile>,
    ) {
        for (child in directory.listFiles()) {
            when {
                child.isDirectory -> collectFiles(child, lrcOut, audioOut)
                child.isFile -> {
                    val name = child.name.orEmpty()
                    when {
                        name.endsWith(".lrc", ignoreCase = true) -> {
                            lrcOut += LocalLrcFile(
                                uriString = child.uri.toString(),
                                displayName = name,
                            )
                        }
                        AUDIO_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } -> {
                            audioOut += LocalAudioFile(
                                uriString = child.uri.toString(),
                                displayName = name,
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun readBytes(file: LocalLrcFile): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver
                .openInputStream(Uri.parse(file.uriString))
                ?.use { input -> input.readBytes() }
        }.getOrNull()
    }
}
