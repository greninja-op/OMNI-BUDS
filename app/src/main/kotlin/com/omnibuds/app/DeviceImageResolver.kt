package com.omnibuds.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Resolves authentic product display images for connected audio devices.
 *
 * 1. Checks bundled offline product hero assets for known device families (realme Buds T300,
 *    Redmi Buds 5A, CMF Buds, JBL Tune 130NC, boAt Rockerz 450, Sony WI-C100).
 * 2. Filters out generic Bluetooth peripherals (e.g. FP-BT, CAR KIT, JioSTB, TV) so they
 *    cleanly display standard vector outline category icons and never show spurious search results.
 * 3. Automatically queries verified online audio product hero endpoints for any new future connected earbuds.
 * 4. Downloads, optimizes, and caches the result as a high-quality WebP file,
 *    notifying the UI immediately for real-time display.
 */
object DeviceImageResolver {

    private const val TAG = "DeviceImageResolver"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun ensureDeviceImage(
        context: Context,
        scope: CoroutineScope,
        address: String,
        displayName: String,
        forceUpdate: Boolean = false,
        onResolved: (String) -> Unit,
    ) {
        val sanitized = address.replace(":", "_").replace("-", "_").lowercase()
        val dir = File(context.filesDir, "device_images").apply { if (!exists()) mkdirs() }
        val targetFile = File(dir, "$sanitized.webp")

        // 1. Check bundled offline drawables FIRST
        val bundledResId = findBundledDrawable(displayName)
        if (bundledResId != 0) {
            scope.launch(Dispatchers.IO) {
                try {
                    val needsCopy = forceUpdate || !targetFile.exists() || targetFile.length() < 1000
                    if (needsCopy) {
                        context.resources.openRawResource(bundledResId).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    if (targetFile.exists() && targetFile.length() > 0) {
                        withContext(Dispatchers.Main) {
                            onResolved(targetFile.absolutePath)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy bundled drawable for $displayName: ${e.message}")
                }
            }
            return
        }

        // 2. Not bundled. If not eligible for online search, remove any old cache and return.
        if (!isEligibleForOnlineSearch(displayName)) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            return
        }

        // 3. Check existing cache for eligible devices
        if (!forceUpdate && targetFile.exists() && targetFile.length() > 1000) {
            onResolved(targetFile.absolutePath)
            return
        }

        // 4. Automatic online image fetcher for future connected earphones
        scope.launch(Dispatchers.IO) {
            try {
                val imageUrl = searchProductHeroImage(displayName)
                if (imageUrl != null) {
                    val downloaded = downloadAndOptimizeImage(imageUrl, targetFile)
                    if (downloaded) {
                        withContext(Dispatchers.Main) {
                            onResolved(targetFile.absolutePath)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch image online for $displayName: ${e.message}")
            }
        }
    }

    fun findBundledDrawable(name: String): Int {
        val lower = name.lowercase().trim()
        return when {
            lower.contains("t300") || lower.contains("buds t300") || lower.contains("realme buds t300") ->
                R.drawable.product_realme_buds_t300
            lower.contains("redmi buds 5a") || lower.contains("buds 5a") ->
                R.drawable.product_redmi_buds_5a
            lower.contains("130nc") || lower.contains("tune130") || lower.contains("tune 130") ->
                R.drawable.product_jbl_tune_130nc
            lower.contains("cmf buds") || lower.contains("cmf by nothing") ->
                R.drawable.product_cmf_buds
            lower.contains("rockerz 450") || lower.contains("boat 450") || lower.contains("boat rockerz 450") ->
                R.drawable.product_boat_rockerz_450
            lower.contains("wi-c100") || lower.contains("wi c100") ->
                R.drawable.product_sony_wi_c100
            else -> 0
        }
    }

    fun isEligibleForOnlineSearch(displayName: String): Boolean {
        val lower = displayName.trim().lowercase()
        if (lower.length < 4) return false

        val blacklist = listOf(
            "fp-bt", "fp bt", "car kit", "carkit", "jiostb", "jio", "stb", "tv", "remote",
            "keyboard", "mouse", "dongle", "obd", "adapter", "printer", "esp32", "arduino", "hc-05", "handsfree"
        )
        if (blacklist.any { lower.contains(it) }) return false

        val audioKeywords = listOf(
            "buds", "tws", "earphone", "earphones", "headphone", "headphones", "headset",
            "earbuds", "earbud", "airpods", "freebuds", "in-ear", "rockerz", "bassheads",
            "soundcore", "airdopes", "tune", "linkbuds", "realme", "redmi", "boat",
            "sony", "jbl", "sennheiser", "bose", "oppo", "oneplus", "nothing", "cmf",
            "galaxy buds", "pixel buds", "skullcandy", "edifier", "marshall", "beats", "boult", "noise"
        )
        return audioKeywords.any { lower.contains(it) }
    }

    private fun searchProductHeroImage(displayName: String): String? {
        val cleanName = displayName
            .replace(Regex("(?i)\\b(tws|wireless|bluetooth|earbuds|earphones|headphones)\\b"), "")
            .trim()
            .ifEmpty { displayName.trim() }

        // Wikipedia API with explicit verification that the page is an audio/headphone article
        try {
            val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&format=json&prop=pageimages|extracts&exintro=1&explaintext=1&pithumbsize=800&generator=search&gsrsearch=" +
                    URLEncoder.encode("$cleanName headphones earbuds", "UTF-8") + "&gsrlimit=3"
            val json = fetchString(wikiUrl)
            if (json != null) {
                val obj = JSONObject(json)
                val pages = obj.optJSONObject("query")?.optJSONObject("pages")
                if (pages != null) {
                    val keys = pages.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val page = pages.optJSONObject(key)
                        val title = page?.optString("title")?.lowercase() ?: ""
                        val extract = page?.optString("extract")?.lowercase() ?: ""
                        val isAudioRelated = listOf("earphone", "headphone", "earbud", "audio", "headset", "tws", "sound")
                            .any { title.contains(it) || extract.contains(it) }
                        if (isAudioRelated) {
                            val thumb = page?.optJSONObject("thumbnail")?.optString("source")
                            if (!thumb.isNullOrBlank()) {
                                return thumb
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Wikipedia search error: ${e.message}")
        }

        return null
    }

    private fun fetchString(urlStr: String): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadAndOptimizeImage(imgUrl: String, destination: File): Boolean {
        val conn = (URL(imgUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return false
            val bitmap = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return false

            val maxDim = 800
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val targetW: Int
                val targetH: Int
                if (ratio > 1f) {
                    targetW = maxDim
                    targetH = (maxDim / ratio).toInt()
                } else {
                    targetH = maxDim
                    targetW = (maxDim * ratio).toInt()
                }
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            FileOutputStream(destination).use { out ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                scaled.compress(format, 90, out)
            }
            destination.exists() && destination.length() > 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed downloading/saving $imgUrl: ${e.message}")
            false
        } finally {
            conn.disconnect()
        }
    }
}
