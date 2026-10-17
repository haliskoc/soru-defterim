package com.sorudefteri.data.images

import android.content.Context
import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WebPThumbnailTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var imagesDir: File
    private lateinit var gorselDepo: GorselDepo

    @Before
    fun setUp() {
        imagesDir = tempFolder.newFolder("images")
        mockContext = mockk(relaxed = true) {
            every { filesDir } returns tempFolder.root
            every { cacheDir } returns tempFolder.newFolder("cache")
        }
        gorselDepo = GorselDepo(mockContext)
    }

    @After
    fun tearDown() {
        try {
            unmockkStatic(Bitmap::class)
        } catch (_: Exception) {}
    }

    @Test
    fun `constants should match Faz 5_1 specifications`() {
        assertEquals("WebP quality should be 75 for 50% storage reduction", 75, GorselDepo.WEBP_QUALITY)
        assertEquals("Thumbnail max size should be 360px", 360, GorselDepo.THUMBNAIL_SIZE)
        assertEquals("Max dimension should be 1600px", 1600, GorselDepo.MAX_DIMENSION)
    }

    @Test
    fun `getExpectedThumbnailFile maps soru prefix to thumb prefix correctly`() {
        val original = File(imagesDir, "soru_123e4567-e89b-12d3-a456-426614174000.webp")
        val expectedThumb = GorselDepo.getExpectedThumbnailFile(original)
        assertEquals("thumb_123e4567-e89b-12d3-a456-426614174000.webp", expectedThumb.name)
        assertEquals(imagesDir.absolutePath, expectedThumb.parentFile?.absolutePath)
    }

    @Test
    fun `getExpectedThumbnailFile maps cozum prefix to thumb prefix correctly`() {
        val original = File(imagesDir, "cozum_987fcdeb-51a2-43f1-b987-654321098765.webp")
        val expectedThumb = GorselDepo.getExpectedThumbnailFile(original)
        assertEquals("thumb_987fcdeb-51a2-43f1-b987-654321098765.webp", expectedThumb.name)
        assertEquals(imagesDir.absolutePath, expectedThumb.parentFile?.absolutePath)
    }

    @Test
    fun `getExpectedThumbnailFile handles jpg extension and custom filenames`() {
        val jpgOriginal = File(imagesDir, "soru_abc-xyz.jpg")
        val expectedThumb = GorselDepo.getExpectedThumbnailFile(jpgOriginal)
        assertEquals("thumb_abc-xyz.webp", expectedThumb.name)

        val customOriginal = File(imagesDir, "camera_capture_raw.png")
        val customThumb = GorselDepo.getExpectedThumbnailFile(customOriginal)
        assertEquals("thumb_camera_capture_raw.webp", customThumb.name)
    }

    @Test
    fun `getThumbnailPath returns null for blank or null paths`() {
        assertNull(GorselDepo.getThumbnailPath(null))
        assertNull(GorselDepo.getThumbnailPath(""))
        assertNull(GorselDepo.getThumbnailPath("   "))
        assertNull(gorselDepo.getThumbnailPath(""))
    }

    @Test
    fun `getThumbnailPath returns original path for network and content uris`() {
        val httpUrl = "https://example.com/images/soru.jpg"
        assertEquals(httpUrl, GorselDepo.getThumbnailPath(httpUrl))

        val contentUri = "content://media/external/images/media/12345"
        assertEquals(contentUri, GorselDepo.getThumbnailPath(contentUri))
    }

    @Test
    fun `getThumbnailPath returns original path if thumbnail is not yet generated on disk`() {
        val originalFile = File(imagesDir, "soru_not_yet_generated.webp")
        originalFile.writeText("fake image content")

        // Thumbnail file does not exist yet
        val path = gorselDepo.getThumbnailPath(originalFile.absolutePath)
        assertEquals("When thumbnail is missing, getThumbnailPath should return original path", originalFile.absolutePath, path)
    }

    @Test
    fun `getThumbnailPath returns thumbnail path when thumbnail file exists`() {
        val uuid = "1111-2222-3333"
        val originalFile = File(imagesDir, "soru_$uuid.webp")
        originalFile.writeText("original content")

        val thumbFile = File(imagesDir, "thumb_$uuid.webp")
        thumbFile.writeText("thumb content")

        val result = gorselDepo.getThumbnailPath(originalFile.absolutePath)
        assertNotNull(result)
        assertEquals(thumbFile.absolutePath, result)
    }

    @Test
    fun `getThumbnailPath returns original if path is already a thumbnail`() {
        val thumbFile = File(imagesDir, "thumb_5555.webp")
        thumbFile.writeText("thumb content")

        val result = GorselDepo.getThumbnailPath(thumbFile.absolutePath)
        assertEquals(thumbFile.absolutePath, result)
    }

    @Test
    fun `gorselSil deletes both main image and its associated thumbnail`() = runTest {
        val uuid = "delete-test-uuid"
        val mainFile = File(imagesDir, "soru_$uuid.webp")
        mainFile.writeText("original image data")

        val thumbFile = File(imagesDir, "thumb_$uuid.webp")
        thumbFile.writeText("thumbnail image data")

        assertTrue("Main file should exist before delete", mainFile.exists())
        assertTrue("Thumb file should exist before delete", thumbFile.exists())

        val deleteSuccess = gorselDepo.gorselSil(mainFile.absolutePath)
        assertTrue("gorselSil should succeed", deleteSuccess)

        assertFalse("Main image file should be deleted", mainFile.exists())
        assertFalse("Thumbnail file should be deleted alongside main image", thumbFile.exists())
    }

    @Test
    fun `generateThumbnailIfMissing returns current path if file is missing`() {
        val nonexistent = File(imagesDir, "soru_does_not_exist.webp")
        val result = gorselDepo.generateThumbnailIfMissing(nonexistent.absolutePath)
        assertEquals(nonexistent.absolutePath, result)
    }

    @Test
    fun `generateThumbnailIfMissing returns existing thumbnail immediately without reprocessing`() {
        val uuid = "cached-thumb-uuid"
        val mainFile = File(imagesDir, "soru_$uuid.webp")
        mainFile.writeText("original")

        val thumbFile = File(imagesDir, "thumb_$uuid.webp")
        thumbFile.writeText("existing-thumbnail")

        val result = gorselDepo.generateThumbnailIfMissing(mainFile.absolutePath)
        assertEquals(thumbFile.absolutePath, result)
    }
}
