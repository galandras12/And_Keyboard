// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

sealed interface DownloadResult {
    class Success(val file: File) : DownloadResult
    class Failure(val reason: Reason, val detail: String? = null) : DownloadResult
    enum class Reason { INSECURE_URL, NETWORK, HTTP_STATUS, TOO_LARGE, CHECKSUM, CANCELLED }
}

/**
 * Downloads one dictionary file. This is the only network code of the keyboard: it only does a plain GET
 * of a file that the user chose, over https, and sends no data about the user or the device.
 * Blocking, call it from a background thread.
 */
object DictionaryDownloader {
    const val MAX_BYTES = 80L * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT_MILLIS = 20_000

    /**
     * Downloads [url] to [target] (replacing it) and checks the SHA-256 if [sha256] is given.
     * [target] is deleted again if anything goes wrong. [allowInsecure] (plain http) is only meant for tests.
     * [onProgress] gets the downloaded and total bytes, total is -1 if unknown.
     */
    fun download(
        url: String, target: File, sha256: String? = null, allowInsecure: Boolean = false,
        maxBytes: Long = MAX_BYTES, isCancelled: () -> Boolean = { false }, onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): DownloadResult {
        val result = try {
            fetch(url, target, sha256, allowInsecure, maxBytes, isCancelled, onProgress)
        } catch (e: IOException) {
            DownloadResult.Failure(DownloadResult.Reason.NETWORK, e.message)
        }
        if (result !is DownloadResult.Success) target.delete()
        return result
    }

    private fun fetch(
        startUrl: String, target: File, sha256: String?, allowInsecure: Boolean, maxBytes: Long,
        isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit
    ): DownloadResult {
        var url = startUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!isAllowed(url, allowInsecure)) return DownloadResult.Failure(DownloadResult.Reason.INSECURE_URL, url)
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false // every hop must be checked
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.setRequestProperty("User-Agent", "keyboard-dictionary-download")
                val status = connection.responseCode
                if (status in 300..399) {
                    url = URI(url).resolve(connection.getHeaderField("Location") ?: return DownloadResult.Failure(DownloadResult.Reason.HTTP_STATUS, "$status")).toString()
                    return@repeat
                }
                if (status != HttpURLConnection.HTTP_OK) return DownloadResult.Failure(DownloadResult.Reason.HTTP_STATUS, "$status")
                val total = connection.contentLengthLong
                if (total > maxBytes) return DownloadResult.Failure(DownloadResult.Reason.TOO_LARGE, "$total")
                return write(connection, target, total, sha256, maxBytes, isCancelled, onProgress)
            } finally {
                connection.disconnect()
            }
        }
        return DownloadResult.Failure(DownloadResult.Reason.HTTP_STATUS, "too many redirects")
    }

    private fun write(
        connection: HttpURLConnection, target: File, total: Long, sha256: String?, maxBytes: Long,
        isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit
    ): DownloadResult {
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var downloaded = 0L
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    if (isCancelled()) return DownloadResult.Failure(DownloadResult.Reason.CANCELLED)
                    val read = input.read(buffer)
                    if (read < 0) break
                    downloaded += read
                    if (downloaded > maxBytes) return DownloadResult.Failure(DownloadResult.Reason.TOO_LARGE, "$downloaded")
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    onProgress(downloaded, total)
                }
            }
        }
        if (total >= 0 && downloaded != total) return DownloadResult.Failure(DownloadResult.Reason.NETWORK, "incomplete: $downloaded of $total bytes")
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (sha256 != null && !actual.equals(sha256, ignoreCase = true))
            return DownloadResult.Failure(DownloadResult.Reason.CHECKSUM, actual)
        return DownloadResult.Success(target)
    }

    private fun isAllowed(url: String, allowInsecure: Boolean): Boolean {
        val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase()
        return scheme == "https" || (allowInsecure && scheme == "http")
    }
}
