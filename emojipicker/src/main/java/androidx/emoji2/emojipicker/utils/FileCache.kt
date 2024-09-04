

package com.rishabh.emojipicker.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.rishabh.emojipicker.BundledEmojiListLoader
import com.rishabh.emojipicker.EmojiViewItem
import java.io.File
import java.io.IOException

/**
 * A class that manages cache files for the emoji picker. All cache files are stored in DE (Device
 * Encryption) storage (N+), and will be invalidated if device OS version or App version is updated.
 *
 * Currently this class is only used by [BundledEmojiListLoader]. All renderable emojis will be
 * cached by categories under /app.package.name/cache/emoji_picker/<osVersion.appVersion>
 * /emoji.<emojiPickerVersion>.<emojiCompatMetadataHashCode>.<categoryIndex>.<ifEmoji12Supported>
 */
internal class FileCache(context: Context) {

    @VisibleForTesting @GuardedBy("lock") internal val emojiPickerCacheDir: File
    private val currentProperty: String
    private val lock = Any()

    init {
        val osVersion = "${Build.VERSION.SDK_INT}_${Build.TIME}"
        val appVersion = getVersionCode(context)
        currentProperty = "$osVersion.$appVersion"
        emojiPickerCacheDir =
            File(getDeviceProtectedStorageContext(context).cacheDir, EMOJI_PICKER_FOLDER)
        if (!emojiPickerCacheDir.exists()) emojiPickerCacheDir.mkdir()
    }

    /** Get cache for a given file name, or write to a new file using the [defaultValue] factory. */
    internal fun getOrPut(
        key: String,
        defaultValue: () -> List<EmojiViewItem>
    ): List<EmojiViewItem> {
        synchronized(lock) {
            val targetDir = File(emojiPickerCacheDir, currentProperty)
            Log.i("getCatcheclocation", targetDir.canonicalPath.toString())

            // No matching cache folder for current property, clear stale cache directory if any
            if (!targetDir.exists()) {
                emojiPickerCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                targetDir.mkdirs()
            }

            val targetFile = File(targetDir, key)
            return readFrom(targetFile) ?: writeTo(targetFile, defaultValue)
        }
    }

    private fun readFrom(targetFile: File): List<EmojiViewItem>? {
        if (!targetFile.isFile) return null
        return targetFile
            .bufferedReader()
            .useLines {

                it.toList() }
            .map {
                Log.i("heheh", it.toString())
                it.split(",") }
            .map {
                Log.i("thisiswathigot", it.toString())
                val emoji = it.getOrNull(0) ?: ""
                val description = it.getOrNull(1) ?:""


                EmojiViewItem(emoji, description,it.drop(2) )
                 }
    }

    private fun writeTo(
        targetFile: File,
        defaultValue: () -> List<EmojiViewItem>
    ): List<EmojiViewItem> {
        val data = defaultValue.invoke()
        if (targetFile.exists()) {
            if (!targetFile.delete()) {
                Log.wtf(TAG, "Can't delete file: $targetFile")
            }
        }
        if (!targetFile.createNewFile()) {
            throw IOException("Can't create file: $targetFile")
        }
        targetFile.bufferedWriter().use { out ->
            for (emoji in data) {
                out.write(emoji.emoji)
                out.write(",${emoji.description}")

                emoji.variants.forEach { out.write(",$it") }
                out.newLine()
            }
        }
        return data
    }

    /** Returns a new [context] for accessing device protected storage. */
    private fun getDeviceProtectedStorageContext(context: Context) =
        context.takeIf { ContextCompat.isDeviceProtectedStorage(it) }
            ?: run { ContextCompat.createDeviceProtectedStorageContext(context) }
            ?: context

    /** Gets the version code for a package. */
    @Suppress("DEPRECATION")
    private fun getVersionCode(context: Context): Long =
        try {
            if (Build.VERSION.SDK_INT >= 33) Api33Impl.getAppVersionCode(context)
            else if (Build.VERSION.SDK_INT >= 28) Api28Impl.getAppVersionCode(context)
            else context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        } catch (e: PackageManager.NameNotFoundException) {
            // Default version to 1
            1
        }

    companion object {
        @Volatile private var instance: FileCache? = null

        internal fun getInstance(context: Context): FileCache =
            instance ?: synchronized(this) { instance ?: FileCache(context).also { instance = it } }

        private const val EMOJI_PICKER_FOLDER = "emoji_picker"
        private const val TAG = "emojipicker.FileCache"
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    internal object Api33Impl {
        fun getAppVersionCode(context: Context) =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                .longVersionCode
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.P)
    internal object Api28Impl {
        fun getAppVersionCode(context: Context) =
            context.packageManager
                .getPackageInfo(context.packageName, /* flags= */ 0)
                .longVersionCode
    }
}
