// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class DictionaryDownloaderTest {
    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val body = ByteArray(100_000) { (it * 31).toByte() }
    private val bodySha = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
    private val base get() = "http://localhost:${server.address.port}"

    @BeforeTest fun setUp() {
        dir = kotlin.io.path.createTempDirectory("dl").toFile()
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/ok") { ex -> ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) } }
        server.createContext("/missing") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
        server.createContext("/redirect") { ex -> ex.responseHeaders.add("Location", "/ok"); ex.sendResponseHeaders(302, -1); ex.close() }
        server.createContext("/to-other-scheme") { ex -> ex.responseHeaders.add("Location", "ftp://example.com/x"); ex.sendResponseHeaders(302, -1); ex.close() }
        server.createContext("/chunked") { ex -> ex.sendResponseHeaders(200, 0); ex.responseBody.use { it.write(body) } } // unknown length
        server.start()
    }

    @AfterTest fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun failure(result: DownloadResult) = (result as? DownloadResult.Failure) ?: fail("expected failure")

    @Test fun `downloads a file and reports progress`() {
        val target = File(dir, "main.dict")
        var last = 0L
        val result = DictionaryDownloader.download("$base/ok", target, bodySha, allowInsecure = true) { done, total ->
            assertEquals(body.size.toLong(), total)
            last = done
        }
        assertTrue(result is DownloadResult.Success)
        assertEquals(body.size.toLong(), last)
        assertTrue(target.readBytes().contentEquals(body))
    }

    @Test fun `plain http is refused by default`() {
        val target = File(dir, "x")
        assertEquals(DownloadResult.Reason.INSECURE_URL, failure(DictionaryDownloader.download("$base/ok", target)).reason)
        assertFalse(target.exists())
    }

    @Test fun `wrong checksum fails and removes the file`() {
        val target = File(dir, "x")
        val result = failure(DictionaryDownloader.download("$base/ok", target, "0".repeat(64), allowInsecure = true))
        assertEquals(DownloadResult.Reason.CHECKSUM, result.reason)
        assertFalse(target.exists())
    }

    @Test fun `http errors are reported`() {
        val result = failure(DictionaryDownloader.download("$base/missing", File(dir, "x"), allowInsecure = true))
        assertEquals(DownloadResult.Reason.HTTP_STATUS, result.reason)
        assertEquals("404", result.detail)
    }

    @Test fun `redirects are followed but every hop must be https`() {
        assertTrue(DictionaryDownloader.download("$base/redirect", File(dir, "a"), bodySha, allowInsecure = true) is DownloadResult.Success)
        val result = failure(DictionaryDownloader.download("$base/to-other-scheme", File(dir, "b"), allowInsecure = true))
        assertEquals(DownloadResult.Reason.INSECURE_URL, result.reason)
    }

    @Test fun `files larger than the limit are refused`() {
        val known = failure(DictionaryDownloader.download("$base/ok", File(dir, "a"), allowInsecure = true, maxBytes = 1000))
        assertEquals(DownloadResult.Reason.TOO_LARGE, known.reason)
        val unknownLength = File(dir, "b")
        val streamed = failure(DictionaryDownloader.download("$base/chunked", unknownLength, allowInsecure = true, maxBytes = 1000))
        assertEquals(DownloadResult.Reason.TOO_LARGE, streamed.reason)
        assertFalse(unknownLength.exists())
    }

    @Test fun `download can be cancelled`() {
        val target = File(dir, "x")
        val result = failure(DictionaryDownloader.download("$base/ok", target, allowInsecure = true, isCancelled = { true }))
        assertEquals(DownloadResult.Reason.CANCELLED, result.reason)
        assertFalse(target.exists())
    }

    @Test fun `unreachable server is a network failure`() {
        val port = server.address.port
        server.stop(0)
        val result = failure(DictionaryDownloader.download("http://localhost:$port/ok", File(dir, "x"), allowInsecure = true))
        assertEquals(DownloadResult.Reason.NETWORK, result.reason)
    }
}
