

package com.rishabh.emojipicker

import android.content.Context
import com.rishabh.emojipicker.utils.FileCache
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@SmallTest
class BundledEmojiListLoaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testGetCategorizedEmojiData_loaded_writeToCache() = runBlocking {
        // delete cache dir first
        val fileCache = FileCache.getInstance(context)
        fileCache.emojiPickerCacheDir.deleteRecursively()
        assertFalse(fileCache.emojiPickerCacheDir.exists())

        BundledEmojiListLoader.load(context)
        val result = BundledEmojiListLoader.getCategorizedEmojiData()
        assertTrue(result.isNotEmpty())

        // emoji_picker/osVersion|appVersion/ folder should be created
        val propertyFolder = fileCache.emojiPickerCacheDir.listFiles()!![0]
        assertTrue(propertyFolder!!.isDirectory)

        // Number of cache files should match the size of categorizedEmojiData
        val cacheFiles = propertyFolder.listFiles()
        assertTrue(cacheFiles!!.size == result.size)
    }

    @Test
    fun testGetCategorizedEmojiData_loaded_readFromCache() = runBlocking {
        // delete cache and load again
        val fileCache = FileCache.getInstance(context)
        fileCache.emojiPickerCacheDir.deleteRecursively()
        BundledEmojiListLoader.load(context)

        val cacheFileName = fileCache.emojiPickerCacheDir.listFiles()!![0].listFiles()!![0].name
        val emptyDefaultValue = listOf<EmojiViewItem>()
        // Read from cache instead of using default value
        var output = fileCache.getOrPut(cacheFileName) { emptyDefaultValue }
        assertTrue(output.isNotEmpty())

        // Remove cache, write default value to cache
        fileCache.emojiPickerCacheDir.deleteRecursively()
        output = fileCache.getOrPut(cacheFileName) { emptyDefaultValue }
        assertTrue(output.isEmpty())
    }

    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun testGetEmojiVariantsLookup_loaded() = runBlocking {
        // delete cache and load again
        FileCache.getInstance(context).emojiPickerCacheDir.deleteRecursively()
        BundledEmojiListLoader.load(context)
        val result = BundledEmojiListLoader.getEmojiVariantsLookup()

        // 👃 has variants (👃,👃,👃🏻,👃🏼,👃🏽,👃🏾,👃🏿)
        assertTrue(result["\uD83D\uDC43"]!!.contains("\uD83D\uDC43\uD83C\uDFFD"))
        // 😀 has no variant
        assertFalse(result.containsKey("\uD83D\uDE00"))
    }
}
