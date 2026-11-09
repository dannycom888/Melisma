package com.melisma.app.lyrics.provider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.melisma.app.lyrics.model.LineRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.Charset

/**
 * Ported from the local-only AALyrics build's `LocalLyricsMatcherTest`,
 * adapted to this app's [LocalFolderMatcher] / [LocalFolderLyricsProvider]
 * API (title + artist list instead of a Track, `fetch` returning a
 * [com.melisma.app.lyrics.model.LyricsDocument] instead of candidates).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalFolderMatcherTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun indexed(vararg names: String): List<LocalFolderMatcher.IndexedLrcFile> {
        return names.map { name ->
            val file = LocalLrcFile(uriString = "content://test/$name", displayName = name)
            LocalFolderMatcher.IndexedLrcFile(file, LocalFolderMatcher.fileKeys(name))
        }
    }

    private fun audio(vararg names: String): List<LocalFolderMatcher.IndexedAudioFile> {
        return names.map { name ->
            val file = LocalAudioFile(uriString = "content://test/audio/$name", displayName = name)
            LocalFolderMatcher.IndexedAudioFile(file, LocalFolderMatcher.fileStem(name))
        }
    }

    private fun request(title: String, artist: String, durationMs: Long = 0L) =
        LyricsRequest(title = title, artist = artist, album = "", durationMs = durationMs)

    private fun leadTexts(document: com.melisma.app.lyrics.model.LyricsDocument?) =
        document?.lines?.filter { it.role == LineRole.LEAD }?.map { it.text }.orEmpty()

    @Test
    fun `audio file anchor finds the same-stem lrc for a duet`() {
        val audioFiles = audio("庄心妍_祁隆 - 一万个舍不得.mp3")
        val lrcFiles = indexed("庄心妍_祁隆 - 一万个舍不得.lrc")

        val found = LocalFolderMatcher.findAudioFile("一万个舍不得", listOf("庄心妍"), audioFiles)

        assertEquals("庄心妍_祁隆 - 一万个舍不得.mp3", found?.file?.displayName)
        assertEquals(
            "庄心妍_祁隆 - 一万个舍不得.lrc",
            LocalFolderMatcher.sameStemLrc(found!!, lrcFiles)?.file?.displayName,
        )
    }

    @Test
    fun `audio anchor prefers the file with the least extra text`() {
        val audioFiles = audio("周杰伦 - 爱情转移.mp3", "周杰伦 - 爱.mp3")

        val found = LocalFolderMatcher.findAudioFile("爱", listOf("周杰伦"), audioFiles)

        assertEquals("周杰伦 - 爱.mp3", found?.file?.displayName)
    }

    @Test
    fun `audio anchor falls back to the title alone when the track reports no artist`() {
        val audioFiles = audio("庄心妍_祁隆 - 一万个舍不得.mp3")

        val found = LocalFolderMatcher.findAudioFile("一万个舍不得", emptyList(), audioFiles)

        assertEquals("庄心妍_祁隆 - 一万个舍不得.mp3", found?.file?.displayName)
    }

    @Test
    fun `audio anchor still finds nothing when the title is unknown`() {
        val audioFiles = audio("庄心妍_祁隆 - 一万个舍不得.mp3")

        assertNull(LocalFolderMatcher.findAudioFile("一千年以后", emptyList(), audioFiles))
    }

    @Test
    fun `artist-title file matches on its title part when the track reports no artist`() {
        val files = indexed("林俊杰 - 一千年以后.lrc", "其他歌.lrc")

        val matched = LocalFolderMatcher.match("一千年以后", emptyList(), files)

        assertEquals(listOf("林俊杰 - 一千年以后.lrc"), matched.map { it.file.displayName })
    }

    @Test
    fun `pickWinner prefers a title-only file over a title-part fallback file`() {
        val files = indexed("林俊杰 - 一千年以后.lrc", "一千年以后.lrc")
        val parsed = listOf(
            files[0] to 260_000L,
            files[1] to 200_500L,
        )

        val winner = LocalFolderMatcher.pickWinner("一千年以后", emptyList(), 200_000L, parsed)

        assertEquals("一千年以后.lrc", winner?.file?.displayName)
    }

    @Test
    fun `artist-title file matches track`() {
        val files = indexed("周杰伦 - 晴天.lrc", "其他歌.lrc")

        val matched = LocalFolderMatcher.match("晴天", listOf("周杰伦"), files)

        assertEquals(listOf("周杰伦 - 晴天.lrc"), matched.map { it.file.displayName })
    }

    @Test
    fun `title-only file matches when no artist file exists`() {
        val files = indexed("晴天.lrc")

        val matched = LocalFolderMatcher.match("晴天", listOf("周杰伦"), files)

        assertEquals(listOf("晴天.lrc"), matched.map { it.file.displayName })
    }

    @Test
    fun `artist-title file is preferred over title-only file`() {
        val files = indexed("晴天.lrc", "周杰伦 - 晴天.lrc")

        val matched = LocalFolderMatcher.match("晴天", listOf("周杰伦"), files)

        assertEquals(
            listOf("周杰伦 - 晴天.lrc", "晴天.lrc"),
            matched.map { it.file.displayName },
        )
    }

    @Test
    fun `normalization ignores case spacing and punctuation`() {
        val files = indexed("jay chou-sunny day.lrc", "JAY CHOU - SUNNY DAY.lrc")

        val matched = LocalFolderMatcher.match("Sunny Day", listOf("Jay Chou"), files)

        assertEquals(2, matched.size)
    }

    @Test
    fun `full-width dash and space fold to half-width`() {
        val files = indexed("周杰伦　－　晴天.lrc")

        val matched = LocalFolderMatcher.match("晴天", listOf("周杰伦"), files)

        assertEquals(listOf("周杰伦　－　晴天.lrc"), matched.map { it.file.displayName })
    }

    @Test
    fun `bracketed version suffix still matches the plain title`() {
        val files = indexed("晴天 (Live).lrc", "周杰伦 - 晴天 [Remaster].lrc")

        val matched = LocalFolderMatcher.match("晴天", listOf("周杰伦"), files)

        assertEquals(2, matched.size)
    }

    @Test
    fun `underscore duet file matches single-artist track`() {
        val files = indexed("庄心妍_祁隆 - 一万个舍不得.lrc", "一万个舍不得.lrc")

        val matched = LocalFolderMatcher.match("一万个舍不得", listOf("庄心妍"), files)

        assertEquals(
            listOf("庄心妍_祁隆 - 一万个舍不得.lrc", "一万个舍不得.lrc"),
            matched.map { it.file.displayName },
        )
    }

    @Test
    fun `ampersand and ideographic comma duets match single-artist track`() {
        val files = indexed("周杰伦 & 蔡依林 - 晴天.lrc", "周杰伦、蔡依林 - 晴天.lrc")

        val matched = LocalFolderMatcher.match("晴天", listOf("蔡依林"), files)

        assertEquals(2, matched.size)
    }

    @Test
    fun `metadata artist with underscore matches single-artist file`() {
        val files = indexed("庄心妍 - 一万个舍不得.lrc")

        val matched = LocalFolderMatcher.match("一万个舍不得", listOf("庄心妍_祁隆"), files)

        assertEquals(listOf("庄心妍 - 一万个舍不得.lrc"), matched.map { it.file.displayName })
    }

    @Test
    fun `feat splits duet and ft inside a word does not split`() {
        val duetFiles = indexed("周杰伦 feat. 蔡依林 - 晴天.lrc")

        assertEquals(1, LocalFolderMatcher.match("晴天", listOf("蔡依林"), duetFiles).size)

        val swiftFiles = indexed("Taylor Swift - Love Story.lrc")

        assertEquals(
            listOf("Taylor Swift - Love Story.lrc"),
            LocalFolderMatcher.match("Love Story", listOf("Taylor Swift"), swiftFiles)
                .map { it.file.displayName },
        )
    }

    @Test
    fun `file naming another artist does not match on title alone`() {
        val files = indexed("祁隆 - 一万个舍不得.lrc")

        val matched = LocalFolderMatcher.match("一万个舍不得", listOf("庄心妍"), files)

        assertTrue(matched.isEmpty())
    }

    @Test
    fun `duration tie-break picks the file ending closest to the track duration`() {
        val files = indexed("晴天.lrc", "晴天 (Live).lrc")
        val parsed = listOf(
            files[0] to 200_000L, // ends 69s early
            files[1] to 268_500L, // ends 0.5s early
        )

        val winner = LocalFolderMatcher.pickWinner("晴天", listOf("周杰伦"), 269_000L, parsed)

        assertEquals("晴天 (Live).lrc", winner?.file?.displayName)
    }

    @Test
    fun `duration tie-break does not override the artist-title tier`() {
        val files = indexed("周杰伦 - 晴天.lrc", "晴天.lrc")
        val parsed = listOf(
            files[0] to 260_000L, // artist file, ends 60s late
            files[1] to 200_500L, // title-only file, ends almost exactly on time
        )

        val winner = LocalFolderMatcher.pickWinner("晴天", listOf("周杰伦"), 200_000L, parsed)

        assertEquals("周杰伦 - 晴天.lrc", winner?.file?.displayName)
    }

    @Test
    fun `utf-8 bytes decode as utf-8`() {
        val bytes = "[00:01.00]晴天".toByteArray(Charset.forName("UTF-8"))

        assertEquals("[00:01.00]晴天", LocalFolderMatcher.decodeBytes(bytes))
    }

    @Test
    fun `gbk bytes fall back to gbk decoding`() {
        val bytes = "[00:01.00]晴天".toByteArray(Charset.forName("GBK"))

        assertEquals("[00:01.00]晴天", LocalFolderMatcher.decodeBytes(bytes))
    }

    @Test
    fun `provider returns the local document for a matching file`() = runBlocking {
        val lrc = "[00:01.00]从前从前有个人爱你很久\n[00:02.00]但偏偏风渐渐把距离吹得好远\n"
        val source = FakeSource(
            mapOf(
                "content://test/周杰伦 - 晴天.lrc" to lrc.toByteArray(Charsets.UTF_8),
            ),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(source)

        val document = provider.fetch(request("晴天", "周杰伦", 269_000L))

        assertEquals("local-folder", document?.providerId)
        assertEquals(
            listOf("从前从前有个人爱你很久", "但偏偏风渐渐把距离吹得好远"),
            leadTexts(document),
        )
    }

    @Test
    fun `provider returns the local document for an underscore duet file`() = runBlocking {
        val lrc = "[00:01.00]不要追问对与错\n[00:02.00]毕竟我们深爱过\n"
        val source = FakeSource(
            mapOf(
                "content://test/庄心妍_祁隆 - 一万个舍不得.lrc" to lrc.toByteArray(Charsets.UTF_8),
            ),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(source)

        val document = provider.fetch(request("一万个舍不得", "庄心妍", 312_000L))

        assertEquals("local-folder", document?.providerId)
        assertEquals(listOf("不要追问对与错", "毕竟我们深爱过"), leadTexts(document))
    }

    @Test
    fun `provider returns the local document for 一千年以后 by 林俊杰`() = runBlocking {
        val lrc = "[00:01.00]一千年以后\n[00:02.00]世界早已没有我\n"
        val source = FakeSource(
            mapOf(
                "content://test/林俊杰 - 一千年以后.lrc" to lrc.toByteArray(Charsets.UTF_8),
            ),
            audioFiles = listOf("林俊杰 - 一千年以后.mp3"),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(source)

        val document = provider.fetch(request("一千年以后", "林俊杰", 300_000L))

        assertEquals("local-folder", document?.providerId)
        assertEquals(
            listOf("一千年以后", "世界早已没有我"),
            leadTexts(document),
        )
    }

    @Test
    fun `provider returns the local document when the track reports no artist`() = runBlocking {
        val lrc = "[00:01.00]一千年以后\n[00:02.00]世界早已没有我\n"
        val source = FakeSource(
            mapOf(
                "content://test/林俊杰 - 一千年以后.lrc" to lrc.toByteArray(Charsets.UTF_8),
            ),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(source)

        val document = provider.fetch(request("一千年以后", "", 300_000L))

        assertEquals("local-folder", document?.providerId)
        assertEquals(
            listOf("一千年以后", "世界早已没有我"),
            leadTexts(document),
        )
    }

    @Test
    fun `refreshIndex builds the index and indexedFileCount reports it`() = runBlocking {
        val source = FakeSource(
            mapOf(
                "content://test/林俊杰 - 一千年以后.lrc" to "[00:01.00]一千年以后\n".toByteArray(Charsets.UTF_8),
                "content://test/周杰伦 - 晴天.lrc" to "[00:01.00]晴天\n".toByteArray(Charsets.UTF_8),
            ),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        assertNull(provider.indexedFileCount())
        provider.attach(source)
        provider.refreshIndex()

        assertEquals(2, provider.indexedFileCount())
    }

    @Test
    fun `refreshIndex reports Ready with the file count`() = runBlocking {
        val source = FakeSource(
            mapOf(
                "content://test/林俊杰 - 一千年以后.lrc" to "[00:01.00]一千年以后\n".toByteArray(Charsets.UTF_8),
                "content://test/周杰伦 - 晴天.lrc" to "[00:01.00]晴天\n".toByteArray(Charsets.UTF_8),
            ),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(source)

        assertEquals(
            LocalFolderLyricsProvider.IndexResult.Ready(2),
            provider.refreshIndex(),
        )
    }

    @Test
    fun `refreshIndex reports Unreadable when the folder cannot be read`() = runBlocking {
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(ThrowingSource())

        assertEquals(
            LocalFolderLyricsProvider.IndexResult.Unreadable,
            provider.refreshIndex(),
        )
    }

    @Test
    fun `fetch says to pick the folder again when the folder cannot be read`() = runBlocking {
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(ThrowingSource())

        assertNull(provider.fetch(request("一千年以后", "林俊杰")))

        assertTrue(
            provider.noMatchReason?.contains("pick it again") == true,
        )
    }

    @Test
    fun `indexStatus reports NoFolder when no folder is configured`() = runBlocking {
        val provider = LocalFolderLyricsProvider(
            context = context,
            hasFolder = { false },
        )

        assertEquals(
            LocalFolderLyricsProvider.IndexResult.NoFolder,
            provider.indexStatus(),
        )
    }

    @Test
    fun `provider returns null when no local file matches`() = runBlocking {
        val source = FakeSource(
            mapOf(
                "content://test/周杰伦 - 七里香.lrc" to "[00:01.00]窗外的麻雀\n".toByteArray(Charsets.UTF_8),
            ),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(source)

        assertNull(provider.fetch(request("晴天", "周杰伦", 269_000L)))
    }

    @Test
    fun `provider returns null when no folder is attached`() = runBlocking {
        val provider = LocalFolderLyricsProvider(context = context)

        assertNull(provider.fetch(request("晴天", "周杰伦")))
        assertNull(provider.indexedFileCount())
    }

    @Test
    fun `provider uses the same-stem lrc of the playing audio file`() = runBlocking {
        val lrc = "[00:01.00]不要追问对与错\n[00:02.00]毕竟我们深爱过\n"
        val source = FakeSource(
            files = mapOf(
                "content://test/庄心妍_祁隆 - 一万个舍不得.lrc" to lrc.toByteArray(Charsets.UTF_8),
            ),
            audioFiles = listOf("庄心妍_祁隆 - 一万个舍不得.mp3"),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(source)

        val document = provider.fetch(request("一万个舍不得", "庄心妍", 312_000L))

        assertEquals("local-folder", document?.providerId)
        assertEquals(listOf("不要追问对与错", "毕竟我们深爱过"), leadTexts(document))
    }

    @Test
    fun `provider falls back to metadata matching when no audio file is found`() = runBlocking {
        val lrc = "[00:01.00]从前从前有个人爱你很久\n"
        val source = FakeSource(
            files = mapOf(
                "content://test/周杰伦 - 晴天.lrc" to lrc.toByteArray(Charsets.UTF_8),
            ),
            audioFiles = emptyList(),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(source)

        val document = provider.fetch(request("晴天", "周杰伦", 269_000L))

        assertEquals("local-folder", document?.providerId)
        assertEquals(listOf("从前从前有个人爱你很久"), leadTexts(document))
    }

    @Test
    fun `provider falls back when the audio file has no same-stem lrc`() = runBlocking {
        val lrc = "[00:01.00]不要追问对与错\n"
        val source = FakeSource(
            files = mapOf(
                "content://test/一万个舍不得.lrc" to lrc.toByteArray(Charsets.UTF_8),
            ),
            audioFiles = listOf("庄心妍 - 一万个舍不得.mp3"),
        )
        val provider = LocalFolderLyricsProvider(context = context)
        provider.attach(source)

        val document = provider.fetch(request("一万个舍不得", "庄心妍", 312_000L))

        assertEquals("local-folder", document?.providerId)
        assertEquals(listOf("不要追问对与错"), leadTexts(document))
    }

    private class FakeSource(
        private val files: Map<String, ByteArray>,
        private val audioFiles: List<String> = emptyList(),
    ) : LocalFolderSource {
        override suspend fun listLrcFiles(): List<LocalLrcFile> {
            return files.keys.map { uri ->
                LocalLrcFile(uriString = uri, displayName = uri.substringAfterLast('/'))
            }
        }

        override suspend fun listAudioFiles(): List<LocalAudioFile> {
            return audioFiles.map { name ->
                LocalAudioFile(uriString = "content://test/audio/$name", displayName = name)
            }
        }

        override suspend fun readBytes(file: LocalLrcFile): ByteArray? = files[file.uriString]
    }

    /** A folder whose read fails, as a saved SAF grant that was revoked does. */
    private class ThrowingSource : LocalFolderSource {
        override suspend fun listLrcFiles(): List<LocalLrcFile> {
            throw SecurityException("persisted URI permission revoked")
        }

        override suspend fun readBytes(file: LocalLrcFile): ByteArray? = null
    }
}
