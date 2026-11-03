package dev.olympus.desk.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

internal data class PairingCode(val address: String, val code: String) {
    fun exchange(): String {
        val connection = URI("$address/v1/pairing/exchange").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val request = JSONObject().put("code", code).toString().toByteArray()
            connection.setFixedLengthStreamingMode(request.size)
            connection.outputStream.use { it.write(request) }
            require(connection.responseCode == 200)
            val response = connection.inputStream.use { input ->
                val buffer = ByteArray(2049)
                var length = 0
                while (length < buffer.size) {
                    val read = input.read(buffer, length, buffer.size - length)
                    if (read < 0) break
                    length += read
                }
                require(length <= 2048)
                buffer.copyOf(length)
            }
            return JSONObject(response.decodeToString()).getString("token").also {
                require(it.startsWith("olympus-read-v1.") && it.length == 103)
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun parse(value: String): PairingCode {
            require(value.length <= 2048)
            val json = JSONObject(value)
            require(json.getInt("version") == 1)
            val uri = URI(json.getString("url"))
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null && uri.rawPath in listOf("", "/"))
            val code = json.getString("code")
            require(code.matches(Regex("[A-Za-z0-9_-]{43}")))
            return PairingCode(uri.toASCIIString().trimEnd('/'), code)
        }
    }
}
