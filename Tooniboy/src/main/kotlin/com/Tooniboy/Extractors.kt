package com.Tooniboy

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import java.net.URI
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class StreamSB8 : StreamSB() {
    override var mainUrl = "https://streamsb.net"
}

class Vidstreamxyz : Chillx() {
    override val name = "VidStream"
    override val mainUrl = "https://vidstreaming.xyz"
}


open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "priority" to "u=0, i",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "en-US,en;q=0.9",
        )
        try {
            val res = app.get(url,referer=mainUrl,headers=headers).toString()

            val encodedString = Regex("(?:const|let|var|window\\.(?:Delta|Alpha|Ebolt|Flagon))\\s+\\w*\\s*=\\s*'(.*?)'").find(res)?.groupValues?.get(1) ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }

            // Decrypt the encoded string
            val keyBase64 = "SCkjX0Y9Vy5tY1FNIyZtdg=="
            val decryptedData = decryptData(keyBase64, encodedString)
            // Extract the m3u8 URL from decrypted data
            val m3u8 = Regex("\"?file\"?:\\s*\"([^\"]+)").find(decryptedData)?.groupValues?.get(1)?.trim() ?: ""
            if (m3u8.isEmpty()) {
                throw Exception("m3u8 URL not found")
            }

            // Prepare headers
            val header = mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to mainUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to USER_AGENT
            )

            // Return the extractor link
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = m3u8,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                    this.headers = header
                }
            )

            // Extract and return subtitles
            val subtitles = extractSrtSubtitles(decryptedData)
            subtitles.forEachIndexed { _, (language, url) ->
                subtitleCallback.invoke(SubtitleFile(language, url))
            }

        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        val regex = """tracks:\s*\[(.*?)]""".toRegex()
        val match = regex.find(subtitle)?.groupValues?.get(1) ?: return emptyList()

        return try {
            val subtitles = JSONArray("[$match]") // Wrap in brackets to form valid JSON
            (0 until subtitles.length()).mapNotNull { i ->
                val obj = subtitles.optJSONObject(i) ?: return@mapNotNull null
                val kind = obj.optString("kind")
                if (kind == "captions") {
                    val label = obj.optString("label")
                    val file = obj.optString("file")
                    label to file
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decryptData(base64Key: String, encryptedData: String): String {
        return try {
            // Decode Base64-encoded encrypted data
            val decodedBytes = base64DecodeArray(encryptedData)

            // Extract IV, Authentication Tag, and Ciphertext
            val salt=decodedBytes.copyOfRange(0, 16)
            val iv = decodedBytes.copyOfRange(16, 28)
            val authTag = decodedBytes.copyOfRange(28, 44)
            val ciphertext = decodedBytes.copyOfRange(44, decodedBytes.size)

            // Convert Base64-encoded password to a SHA-256 encryption key
            val password = base64Decode(base64Key)
            val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(
                PBEKeySpec(password.toCharArray(), salt, 999, 32 * 8)
            ).encoded

            // Decrypt the data using AES-GCM
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // Perform decryption
            val decryptedBytes = cipher.doFinal(ciphertext + authTag)
            String(decryptedBytes, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            e.printStackTrace()
            "Decryption failed"
        }
    }

    /** End **/
}

open class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = false

    @Suppress("NAME_SHADOWING")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url=url.replace("/e","")
        val response=app.get(url,referer=url, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).document
        val script = response.selectFirst("script:containsData(vplayer)")?.data().toString()
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to url,
        )

        Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = link,
                    INFER_TYPE
                ) {
                    this.referer = "https://rubystm.com"
                    this.quality = Qualities.P1080.value
                    this.headers = headers
                }
            )
        }
    }
}

class Cdnwish : StreamWishExtractor() {
    override var mainUrl = "https://cdnwish.com"
}

class vidhidevip : VidhideExtractor() {
    override var mainUrl = "https://vidhidevip.com"
}

class D000d : DoodLaExtractor() {
    override var mainUrl = "https://d000d.com"
}


class FileMoonnl : Filesim() {
    override val mainUrl = "https://filemoon.nl"
    override val name = "FileMoon"
}


class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host = getBaseUrl(app.get(url).url)
        val embedId = url.substringAfterLast("/")
        val postData = mapOf("sid" to embedId)

        val responseJson = app.post("$host/embedhelper.php", data = postData).text
        val jsonElement = JsonParser.parseString(responseJson)
        if (!jsonElement.isJsonObject) return

        val root = jsonElement.asJsonObject
        val siteUrls = root["siteUrls"]?.asJsonObject ?: return
        val siteFriendlyNames = root["siteFriendlyNames"]?.asJsonObject

        val decodedMresult: JsonObject = when {
            root["mresult"]?.isJsonObject == true -> {
                root["mresult"]?.asJsonObject!!
            }
            root["mresult"]?.isJsonPrimitive == true -> {
                val mresultBase64 = root["mresult"]?.asString ?: return
                try {
                    val jsonStr = base64Decode(mresultBase64)
                    JsonParser.parseString(jsonStr).asJsonObject
                } catch (e: Exception) {
                    Log.e("Error:", "Failed to decode mresult base64: $e")
                    return
                }
            }
            else -> return
        }

        val commonKeys = siteUrls.keySet().intersect(decodedMresult.keySet())

        for (key in commonKeys) {
            val base = siteUrls[key]?.asString?.trimEnd('/') ?: continue
            val path = decodedMresult[key]?.asString?.trimStart('/') ?: continue
            val fullUrl = "$base/$path"

            val friendlyName = siteFriendlyNames?.get(key)?.asString ?: key

            try {
                when (friendlyName) {
                    "EarnVids" -> {
                        VidhideExtractor().getUrl(fullUrl, referer, subtitleCallback, callback)
                    }
                    "StreamHG" -> {
                        VidHidePro().getUrl(fullUrl, referer, subtitleCallback, callback)
                    }
                    "RpmShare", "UpnShare", "StreamP2p" -> {
                        VidStack().getUrl(fullUrl, referer, subtitleCallback, callback)
                    }
                    else -> {
                        loadExtractor(fullUrl, referer ?: mainUrl, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                Log.e("Error:", "Failed to extract from $friendlyName at $fullUrl")
                continue
            }
        }

    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}