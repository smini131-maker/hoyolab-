package com.smini131.hoyocheckin.api

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: ByteArray? = null
)

data class HttpResponse(
    val status: Int,
    val body: String,
    val serverEpochMillis: Long?
)

interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse
}

class HttpsUrlConnectionTransport : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val parsed = URL(request.url)
        require(parsed.protocol.equals("https", ignoreCase = true)) { "HTTPS 주소만 허용됩니다." }

        val connection = parsed.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            request.body?.let { bytes ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { output -> output.write(bytes) }
            }

            val status = connection.responseCode
            val input = if (status in 200..399) connection.inputStream else connection.errorStream
            val responseBody = input?.use(::readLimitedUtf8).orEmpty()
            val serverDate = connection.date.takeIf { it > 0L }
            return HttpResponse(status, responseBody, serverDate)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimitedUtf8(input: InputStream): String {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val output = StringBuilder()
        val buffer = CharArray(2048)
        while (output.length < MAX_RESPONSE_CHARS) {
            val count = reader.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_CHARS - output.length))
            if (count < 0) break
            output.append(buffer, 0, count)
        }
        return output.toString()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val MAX_RESPONSE_CHARS = 65_536
    }
}
