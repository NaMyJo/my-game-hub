package com.mygamehub.app

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            IMAGE_DOWNLOAD_CHANNEL,
        ).setMethodCallHandler { call, result ->
            if (call.method != "savePng") {
                result.notImplemented()
                return@setMethodCallHandler
            }

            val bytes = call.argument<ByteArray>("bytes")
            val requestedFileName = call.argument<String>("fileName")

            if (bytes == null || bytes.isEmpty() || requestedFileName.isNullOrBlank()) {
                result.error("invalid_image", "PNG image data is invalid.", null)
                return@setMethodCallHandler
            }

            thread(name = "game-identity-image-save") {
                try {
                    savePngToGallery(bytes, requestedFileName)
                    runOnUiThread { result.success(null) }
                } catch (_: UnsupportedOperationException) {
                    runOnUiThread {
                        result.error(
                            "unsupported_android_version",
                            "Gallery saving requires Android 10 or newer.",
                            null,
                        )
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        result.error(
                            "save_failed",
                            "Unable to save the PNG image.",
                            null,
                        )
                    }
                }
            }
        }

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            THEME_PREFERENCES_CHANNEL,
        ).setMethodCallHandler { call, result ->
            val key = call.argument<String>("key")
            if (key.isNullOrBlank()) {
                result.error("invalid_key", "Theme preference key is invalid.", null)
                return@setMethodCallHandler
            }

            val preferences = getSharedPreferences(THEME_PREFERENCES_NAME, MODE_PRIVATE)
            when (call.method) {
                "read" -> result.success(preferences.getString(key, null))
                "write" -> {
                    val value = call.argument<String>("value")
                    if (value.isNullOrBlank()) {
                        result.error("invalid_value", "Theme preference value is invalid.", null)
                    } else {
                        preferences.edit().putString(key, value).apply()
                        result.success(null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun savePngToGallery(bytes: ByteArray, requestedFileName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw UnsupportedOperationException()
        }

        val baseName = requestedFileName
            .removeSuffix(".png")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "MY_GAME_HUB" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(Date())
        val fileName = "${baseName}_${timestamp}.png"
        val resolver = applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/MY GAME HUB",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Unable to create MediaStore item.")
        var completed = false

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: throw IOException("Unable to open MediaStore output stream.")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            completed = true
        } finally {
            if (!completed) {
                resolver.delete(uri, null, null)
            }
        }
    }

    private companion object {
        const val IMAGE_DOWNLOAD_CHANNEL = "com.mygamehub.app/image_download"
        const val THEME_PREFERENCES_CHANNEL = "com.mygamehub.app/theme_preferences"
        const val THEME_PREFERENCES_NAME = "my_game_hub_preferences"
    }
}
