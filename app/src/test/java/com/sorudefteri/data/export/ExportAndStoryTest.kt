package com.sorudefteri.data.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.sorudefteri.data.backup.BackupManager
import com.sorudefteri.data.backup.DriveScopes
import com.sorudefteri.data.backup.GoogleDriveBackupHelper
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.ui.istatistik.components.StoryCardGenerator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

/**
 * FAZ 5 Birim Testleri:
 * 1. Faz 5.3: Anki & Flashcard CSV/APKG Dışa Aktarımı (AnkiExportHelper)
 * 2. Faz 5.4: Instagram / WhatsApp Story Başarı Kartı 9:16 (StoryCardGenerator)
 * 3. Faz 5.5: Sessiz Google Drive AppData Senkronizasyonu (GoogleDriveBackupHelper)
 */
class ExportAndStoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var cacheDir: File
    private lateinit var filesDir: File

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        filesDir = tempFolder.newFolder("files")

        mockContext = mockk(relaxed = true)
        every { mockContext.cacheDir } returns cacheDir
        every { mockContext.filesDir } returns filesDir
        every { mockContext.packageName } returns "com.sorudefteri"

        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.fromFile(any()) } returns mockUri
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    // =========================================================================
    // 1. FAZ 5.3: Anki Deste & Flashcard Dışa Aktarım Testleri
    // =========================================================================

    @Test
    fun `ankiExport_bosSoruListesi_gecerliBaslikUretir`() {
        val content = AnkiExportHelper.buildAnkiDeckContent(emptyList())
        assertTrue("Anki ayırıcı direktifi bulunmalı", content.contains("#separator:tab"))
        assertTrue("HTML desteği direktifi bulunmalı", content.contains("#html:true"))
        assertTrue("Etiket sütunu direktifi bulunmalı", content.contains("#tags column:3"))

        val lines = content.trim().lines()
        // Sadece 3 direktif satırı bulunmalı
        assertEquals(3, lines.size)
    }

    @Test
    fun `ankiExport_ornekSorulari_dogruTsvSutunlarinaDonusturur`() {
        val sampleSoru = Soru(
            id = 101L,
            imagePath = "/images/soru_101.jpg",
            correctAnswer = "C",
            studentAnswer = "A",
            examType = "TYT",
            subjectId = "matematik",
            topic = "Köklü Sayılar",
            mistakeType = HataTipi.DIKKAT_HATASI,
            difficulty = "Zor",
            questionText = "Kök 32 sayısı kök 2'nin kaç katıdır?",
            aiExplanation = "Kök 32 = 4 kök 2 olduğundan cevap 4 katıdır.",
            note = "Katsayıları içeri alırken dikkat et",
            reviewStage = 3
        )

        val content = AnkiExportHelper.buildAnkiDeckContent(listOf(sampleSoru))
        val lines = content.trim().lines()
        assertEquals(4, lines.size) // 3 direktif + 1 soru satırı

        val soruRow = lines[3]
        val columns = soruRow.split("\t")
        assertEquals("TSV tam olarak 3 sütun içermeli (Front, Back, Tags)", 3, columns.size)

        val frontCol = columns[0]
        val backCol = columns[1]
        val tagsCol = columns[2]

        // Ön yüz kontrolleri
        assertTrue(frontCol.contains("[TYT - Matematik] Köklü Sayılar"))
        assertTrue(frontCol.contains("Kök 32 sayısı"))
        assertTrue(frontCol.contains("<img src=\"soru_101.jpg\""))

        // Arka yüz kontrolleri
        assertTrue(backCol.contains("🎯 Doğru Cevap: C"))
        assertTrue(backCol.contains("❌ Verilen Cevap: A"))
        assertTrue(backCol.contains("💡 Yapay Zeka Çözümü:"))
        assertTrue(backCol.contains("Kök 32 = 4 kök 2"))
        assertTrue(backCol.contains("📝 Kişisel Not:"))
        assertTrue(backCol.contains("Zorluk: <b>Zor</b>"))
        assertTrue(backCol.contains("Dikkat Hatası"))

        // Etiket kontrolleri
        assertTrue(tagsCol.contains("TYT"))
        assertTrue(tagsCol.contains("Matematik"))
        assertTrue(tagsCol.contains("Köklü_Sayılar"))
        assertTrue(tagsCol.contains("LeitnerStage_3"))
        assertTrue(tagsCol.contains("Hata_DIKKAT_HATASI"))
    }

    @Test
    fun `ankiExport_tabVeSatirSonlariniGuvenleTemizler`() {
        val soruWithTabsAndNewlines = Soru(
            id = 102L,
            imagePath = "",
            correctAnswer = "B",
            examType = "AYT",
            subjectId = "ayt_fizik",
            topic = "Manyetizma",
            questionText = "Satır 1\nSatır 2\tTab içerik\r\nSatır 3",
            aiExplanation = "Çözüm adım 1\nÇözüm adım 2\tDetay"
        )

        val content = AnkiExportHelper.buildAnkiDeckContent(listOf(soruWithTabsAndNewlines))
        val lines = content.trim().lines()
        val soruRow = lines[3]
        val columns = soruRow.split("\t")

        assertEquals("Tab karakterleri sütun sayısını bozmamalı", 3, columns.size)
        // Yeni satırlar <br/> etiketi ile değiştirilmiş olmalı
        assertTrue(columns[0].contains("Satır 1<br/>Satır 2"))
        assertTrue(columns[1].contains("Çözüm adım 1<br/>Çözüm adım 2 Detay"))
    }

    @Test
    fun `ankiExport_exportQuestionsToAnki_gecerliCsvDosyasiOlusturur`() {
        val sorular = listOf(
            Soru(
                id = 1L,
                imagePath = "",
                correctAnswer = "D",
                examType = "TYT",
                subjectId = "turkce",
                topic = "Paragraf",
                questionText = "Paragraf ana düşüncesi nedir?"
            )
        )

        val file = AnkiExportHelper.exportQuestionsToAnki(mockContext, sorular)

        assertNotNull(file)
        assertTrue("Dosya diskte oluşturulmalı", file.exists())
        assertTrue("Dosya .csv uzantılı olmalı", file.name.endsWith(".csv"))
        assertTrue("Dosya boyutu 0'dan büyük olmalı", file.length() > 0L)

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("#separator:tab"))
        assertTrue(text.contains("Paragraf"))
    }

    @Test
    fun `ankiExport_exportQuestionsToAnkiPackage_gecerliZipPaketiOlusturur`() {
        // Geçici bir görsel dosyası oluştur
        val dummyImg = File(cacheDir, "sample_q.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }

        val sorular = listOf(
            Soru(
                id = 99L,
                imagePath = dummyImg.absolutePath,
                correctAnswer = "E",
                examType = "AYT",
                subjectId = "ayt_kimya",
                topic = "Organik Kimya",
                questionText = "Alkanların genel formülü nedir?"
            )
        )

        val zipFile = AnkiExportHelper.exportQuestionsToAnkiPackage(mockContext, sorular)

        assertNotNull(zipFile)
        assertTrue(zipFile.exists())
        assertTrue(zipFile.name.endsWith(".zip"))
        assertTrue(zipFile.length() > 0L)

        // ZIP içeriğini doğrula
        ZipFile(zipFile).use { zip ->
            val deckEntry = zip.getEntry("deck.tsv")
            assertNotNull("deck.tsv ZIP içinde bulunmalı", deckEntry)

            val imgEntry = zip.getEntry("soru_99.jpg")
            assertNotNull("soru_99.jpg görseli ZIP içinde bulunmalı", imgEntry)
        }
    }

    // =========================================================================
    // 2. FAZ 5.4: Instagram / WhatsApp Story Başarı Kartı (9:16) Testleri
    // =========================================================================

    @Test
    fun `storyCard_boyutlariVeGorselSabitleri_9a16DikeyFormattadir`() {
        assertEquals("Hikaye genişliği 1080 px olmalı", 1080, StoryCardGenerator.STORY_WIDTH)
        assertEquals("Hikaye yüksekliği 1920 px olmalı", 1920, StoryCardGenerator.STORY_HEIGHT)

        val ratio = StoryCardGenerator.STORY_HEIGHT.toFloat() / StoryCardGenerator.STORY_WIDTH.toFloat()
        val expectedRatio = 16f / 9f
        assertEquals(expectedRatio, ratio, 0.01f)
    }

    @Test
    fun `storyCard_generateStoryBitmap_hataVermedenUretilir`() {
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.width } returns StoryCardGenerator.STORY_WIDTH
        every { mockBitmap.height } returns StoryCardGenerator.STORY_HEIGHT

        val resultBitmap = StoryCardGenerator.generateStoryBitmap(
            context = mockContext,
            currentStreak = 14,
            successRate = 78.5f,
            levelTitle = "Derece Adayı",
            levelNumber = 4,
            totalXp = 2450,
            unlockedBadges = setOf("ISLEM_HATASI_AVCISI", "YEDILI_SERI"),
            studentTarget = "Hedef: Cerrahpaşa Tıp",
            netIncrease = "+4.5 Net",
            bitmapProvider = { w, h ->
                assertEquals(1080, w)
                assertEquals(1920, h)
                mockBitmap
            }
        )

        assertNotNull(resultBitmap)
        assertEquals(1080, resultBitmap.width)
        assertEquals(1920, resultBitmap.height)
    }

    @Test
    fun `storyCard_rozetOlmayanKullanicida_guvenleCalisir`() {
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.width } returns 1080
        every { mockBitmap.height } returns 1920

        val resultBitmap = StoryCardGenerator.generateStoryBitmap(
            context = mockContext,
            currentStreak = 0,
            successRate = 0f,
            levelTitle = "Çırak",
            levelNumber = 1,
            totalXp = 0,
            unlockedBadges = emptySet(),
            studentTarget = "YKS Hedefim",
            bitmapProvider = { _, _ -> mockBitmap }
        )

        assertNotNull(resultBitmap)
    }

    // =========================================================================
    // 3. FAZ 5.5: Google Drive AppData Senkronizasyon Testleri
    // =========================================================================

    @Test
    fun `driveScopes_appDataVeFileKapsamSabitleri_dogrudur`() {
        assertEquals("https://www.googleapis.com/auth/drive.appdata", DriveScopes.DRIVE_APPDATA)
        assertEquals("https://www.googleapis.com/auth/drive.file", DriveScopes.DRIVE_FILE)
    }

    private class FakeBackupManager : BackupManager(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true)
    ) {
        var exportTempResult: Result<Pair<File, Int>> = Result.failure(Exception("Not set"))
        var importResult: Result<Int> = Result.success(10)

        override suspend fun exportBackupToTempFile(password: String?): Result<Pair<File, Int>> = exportTempResult
        override suspend fun importBackup(sourceUri: Uri, password: String?): Result<Int> = importResult
    }

    @Test
    fun `googleDriveBackupHelper_createCloudBackupPayload_paketiOlustururVeMetadataGunceller`() = runTest {
        val fakeBackupManager = FakeBackupManager()
        val mockDataStore = mockk<AyarlarDataStore>(relaxed = true)

        val tempExportFile = File(cacheDir, "temp_test_backup.sdefteri").apply {
            writeText("SDEFTERI_MOCK_CONTENT")
        }
        fakeBackupManager.exportTempResult = Result.success(tempExportFile to 25)

        val helper = GoogleDriveBackupHelper(
            context = mockContext,
            backupManager = fakeBackupManager,
            ayarlarDataStore = mockDataStore
        )

        val payload = helper.createCloudBackupPayload(mockContext)

        assertNotNull(payload)
        assertTrue("Payload dosyası diskte mevcut olmalı", payload.exists())
        assertEquals("soru_defterim_cloud_backup.sdefteri", payload.name)
        assertTrue("Dosya boyutu 0'dan büyük olmalı", payload.length() > 0L)

        // Metadata kontrolü
        val metadata = helper.getCloudBackupMetadata()
        assertEquals("soru_defterim_cloud_backup.sdefteri", metadata.fileName)
        assertEquals(25, metadata.questionCount)
        assertTrue("Senkronizasyon başarılı işaretlenmeli", metadata.isSyncSuccessful)
        assertTrue("Senkronizasyon zaman damgası atanmalı", metadata.lastSyncTimestamp > 0L)

        coVerify(atLeast = 1) { mockDataStore.setDriveLastSyncTime(any()) }
    }

    @Test
    fun `googleDriveBackupHelper_restoreCloudBackupPayload_gecerliDosyayiYukler`() = runTest {
        val fakeBackupManager = FakeBackupManager()
        val dummyBackupFile = File(cacheDir, "restore_test.sdefteri").apply {
            writeText("MOCK_BACKUP_CONTENT")
        }
        fakeBackupManager.importResult = Result.success(18)

        val helper = GoogleDriveBackupHelper(
            context = mockContext,
            backupManager = fakeBackupManager
        )

        val result = helper.restoreCloudBackupPayload(mockContext, dummyBackupFile)

        assertTrue(result.isSuccess)
        assertEquals(18, result.getOrNull())
    }

    @Test
    fun `googleDriveBackupHelper_restoreCloudBackupPayload_olmayanDosyadaHataVerir`() = runTest {
        val fakeBackupManager = FakeBackupManager()
        val nonExistentFile = File(cacheDir, "non_existent_cloud_backup.sdefteri")

        val helper = GoogleDriveBackupHelper(
            context = mockContext,
            backupManager = fakeBackupManager
        )

        val result = helper.restoreCloudBackupPayload(mockContext, nonExistentFile)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    /**
     * DÜZELTİLEN DAVRANIŞ: `syncToCloud()` Google Drive'a yükleme yapmıyor —
     * yalnızca yerel bir payload dosyası yazıp `Result.success()` dönüyordu.
     * UI bunu kullanıcıya "senkronizasyon başarıyla tamamlandı" diye
     * gösteriyor, yani kullanıcı verisinin bulutta olduğuna inanıyordu.
     *
     * Artık gerçekçi olmayan başarı yerine açık bir hata dönüyor. Bu test,
     * bu DÜZELTİLMİŞ davranışı kilitler.
     */
    fun `googleDriveBackupHelper_syncToCloud_yukleme_yapilmadigi_icin_basari_dondurmez`() = runTest {
        val fakeBackupManager = FakeBackupManager()
        val mockDataStore = mockk<AyarlarDataStore>(relaxed = true)

        val tempExportFile = File(cacheDir, "sync_test.sdefteri").apply {
            writeText("MOCK_SYNC_PAYLOAD")
        }
        fakeBackupManager.exportTempResult = Result.success(tempExportFile to 42)

        val helper = GoogleDriveBackupHelper(
            context = mockContext,
            backupManager = fakeBackupManager,
            ayarlarDataStore = mockDataStore
        )

        val result = helper.syncToCloud(mockContext)

        // Drive'a gerçekten yükleme yapılmadığı için başarı DÖNMEMELİ.
        // Yoksa kullanıcı "bulutta yedeklendi" sanar.
        assertTrue("Yükleme uygulanmadıysa başarı dönmemeli", result.isFailure)
        val hata = result.exceptionOrNull()
        assertTrue(
            "Hata mesajı uygulanmamışlığı açıklamalı",
            hata is UnsupportedOperationException
        )
    }
}
