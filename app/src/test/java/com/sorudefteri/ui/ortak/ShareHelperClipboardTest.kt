package com.sorudefteri.ui.ortak

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tablet odaklı panoya görsel kopyalama (ShareHelper.copyQuestionImageToClipboard) testleri.
 */
class ShareHelperClipboardTest {

    private lateinit var mockContext: Context
    private lateinit var mockClipboardManager: ClipboardManager
    private lateinit var mockContentResolver: ContentResolver
    private var tempFile: File? = null

    private val mockUri = mockk<Uri>(relaxed = true)

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockClipboardManager = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)

        every { mockContext.packageName } returns "com.sorudefteri"
        every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns mockClipboardManager
        every { mockContext.contentResolver } returns mockContentResolver

        ShareHelper.uriProvider = { _, _, _ -> mockUri }
    }

    @After
    fun tearDown() {
        tempFile?.delete()
        ShareHelper.uriProvider = { context, authority, file ->
            FileProvider.getUriForFile(context, authority, file)
        }
        unmockkAll()
    }

    @Test
    fun `copyQuestionImageToClipboard dosya yoksa false doner`() {
        val result = ShareHelper.copyQuestionImageToClipboard(mockContext, "/gecersiz/dosya/yolu.jpg")
        assertFalse("Var olmayan dosya için false dönmelidir", result)
    }

    @Test
    fun `copyQuestionImageToClipboard dosya mevcutsa FileProvider URI ile panoya kopyalar ve true doner`() {
        tempFile = File.createTempFile("soru_test_", ".jpg")

        val result = ShareHelper.copyQuestionImageToClipboard(mockContext, tempFile!!.absolutePath)

        assertTrue("Başarılı kopyalama true dönmelidir", result)
        verify {
            mockClipboardManager.setPrimaryClip(any())
        }
    }

    @Test
    fun `copyQuestionImageToClipboard clipboard manager bulunamazsa false doner`() {
        tempFile = File.createTempFile("soru_test_noclip_", ".jpg")
        every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns null

        val result = ShareHelper.copyQuestionImageToClipboard(mockContext, tempFile!!.absolutePath)
        assertFalse("ClipboardManager null olduğunda false dönmelidir", result)
    }

    @Test
    fun `copyQuestionImageToClipboard FileProvider hata firlattiginda guvenle false doner`() {
        tempFile = File.createTempFile("soru_test_err_", ".jpg")
        ShareHelper.uriProvider = { _, _, _ ->
            throw IllegalArgumentException("File not found in paths")
        }

        val result = ShareHelper.copyQuestionImageToClipboard(mockContext, tempFile!!.absolutePath)
        assertFalse("İstisna durumunda çökmeden false dönmelidir", result)
    }
}
