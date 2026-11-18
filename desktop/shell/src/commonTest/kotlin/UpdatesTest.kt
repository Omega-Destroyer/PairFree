package io.github.omegadestroyer.pairfree.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatesTest {
    private val release = """
        {"tag_name":"v0.15.0","html_url":"https://github.com/Omega-Destroyer/PairFree/releases/tag/v0.15.0","name":"x","assets":[
          {"name":"PairFree-0.15.0.apk","browser_download_url":"https://example/apk"},
          {"name":"PairFree-0.15.0.exe","browser_download_url":"https://example/exe"},
          {"name":"PairFree-0.15.0.exe.sig","browser_download_url":"https://example/sig"}]}
    """.trimIndent()

    @Test
    fun theLatestReleaseIsReadWithItsProgramAndSignature() {
        val r = Updates.parseLatest(release)!!
        assertEquals("0.15.0", r.version)
        assertEquals("https://example/exe", r.programUrl)
        assertEquals("https://example/sig", r.signatureUrl)
        assertTrue(r.pageUrl.endsWith("v0.15.0"))
    }

    @Test
    fun aReleaseWithoutTheProgramStillReadsAndAnswerThatIsNoReleaseDoesNot() {
        assertNull(Updates.parseLatest("""{"tag_name":"v0.15.0","assets":[]}""")!!.programUrl)
        assertNull(Updates.parseLatest("""{"message":"Not Found"}"""))
        assertNull(Updates.parseLatest("not json"))
        assertNull(Updates.parseLatest("""{"tag_name":"nightly"}"""))
    }

    @Test
    fun versionsCompareByNumberNotByText() {
        assertTrue(Updates.isNewer("0.15.0", "0.14.0"))
        assertTrue(Updates.isNewer("0.14.10", "0.14.9"))
        assertTrue(Updates.isNewer("1.0.0", "0.99.99"))
        assertFalse(Updates.isNewer("0.14.0", "0.14.0"))
        assertFalse(Updates.isNewer("0.13.9", "0.14.0"))
    }

    private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun sha256MatchesTheKnownAnswers() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(Sha256.digest(ByteArray(0))))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(Sha256.digest("abc".encodeToByteArray())))
        // Longer than one block, so the padding spills into a second.
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", hex(Sha256.digest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())))
    }
}
