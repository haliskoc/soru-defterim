package com.sorudefteri.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.entity.toEntity
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.SoruRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * BackupManager için Güvenlik, Şifreleme (AES-256-GCM + PBKDF2),
 * Zip Slip zafiyet koruması ve Manifest doğrulama birim testleri.
 */
class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockSoruRepository: SoruRepository
    private lateinit var mockGorselDepo: GorselDepo
    private lateinit var backupManager: BackupManager
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val MAGIC_HEADER = "SDEFTERI_ENC_V1".toByteArray(Charsets.US_ASCII)

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        mockSoruRepository = mockk(relaxed = true)
        mockGorselDepo = mockk(relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver
        every { mockContext.cacheDir } returns tempFolder.newFolder("cache")
        every { mockContext.filesDir } returns tempFolder.newFolder("files")

        backupManager = BackupManager(
            context = mockContext,
            soruRepository = mockSoruRepository,
            gorselDepo = mockGorselDepo,
            json = json
        )
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val keySpec = PBEKeySpec(password, salt, 10000, 256)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    @Test
    fun aes256Gcm_sifrelemeVeDesifreleme_roundtripBasarili() {
        val password = "CokGucluParola123!?"
        val plainText = "Soru Defterim Yedekleme Verisi - Matematik, Fizik Soruları ve Çözümleri"
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)

        // Şifreleme
        val secureRandom = SecureRandom()
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }

        val secretKey = deriveKey(password.toCharArray(), salt)
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }

        val encryptedPayload = ByteArrayOutputStream().use { out ->
            out.write(MAGIC_HEADER)
            out.write(salt)
            out.write(iv)
            CipherOutputStream(out, encryptCipher).use { cipherOut ->
                cipherOut.write(plainBytes)
            }
            out.toByteArray()
        }

        assertTrue(encryptedPayload.size > MAGIC_HEADER.size + 16 + 12)

        // Deşifreleme
        val input = ByteArrayInputStream(encryptedPayload)
        val header = ByteArray(MAGIC_HEADER.size)
        input.read(header)
        assertTrue("Sihirli başlık doğrulanmalı", header.contentEquals(MAGIC_HEADER))

        val readSalt = ByteArray(16).also { input.read(it) }
        val readIv = ByteArray(12).also { input.read(it) }

        val decryptKey = deriveKey(password.toCharArray(), readSalt)
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, decryptKey, GCMParameterSpec(128, readIv))
        }

        val decryptedBytes = CipherInputStream(input, decryptCipher).use { cipherIn ->
            cipherIn.readBytes()
        }

        val recoveredText = String(decryptedBytes, Charsets.UTF_8)
        assertEquals("Deşifre edilen metin orijinali ile birebir aynı olmalı", plainText, recoveredText)
    }

    @Test
    fun yanlisParola_veyaBozukDosya_desifrelemedeGuvenlikHatasiVerir() {
        val correctPassword = "DogruParola2026"
        val wrongPassword = "YanlisParola2026"
        val plainBytes = "Gizli Soru Verisi".toByteArray(Charsets.UTF_8)

        val secureRandom = SecureRandom()
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }

        val secretKey = deriveKey(correctPassword.toCharArray(), salt)
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }

        val encryptedPayload = ByteArrayOutputStream().use { out ->
            out.write(MAGIC_HEADER)
            out.write(salt)
            out.write(iv)
            CipherOutputStream(out, encryptCipher).use { cipherOut ->
                cipherOut.write(plainBytes)
            }
            out.toByteArray()
        }

        // Yanlış parola ile açma denemesi
        val input = ByteArrayInputStream(encryptedPayload)
        val header = ByteArray(MAGIC_HEADER.size).also { input.read(it) }
        val readSalt = ByteArray(16).also { input.read(it) }
        val readIv = ByteArray(12).also { input.read(it) }

        val wrongKey = deriveKey(wrongPassword.toCharArray(), readSalt)
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, wrongKey, GCMParameterSpec(128, readIv))
        }

        var exceptionThrown = false
        try {
            CipherInputStream(input, decryptCipher).use { cipherIn ->
                cipherIn.readBytes()
            }
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertTrue("Yanlış parolada GCM tag hatası veya istisna fırlatılmalı", exceptionThrown)
    }

    @Test
    fun zipSlip_pathTraversalSaldirisi_engellenirVeSecurityExceptionFirlatilir() = runTest {
        // Zararlı bir ZIP arşivi hazırla ("../malicious.txt")
        val maliciousZipFile = tempFolder.newFile("malicious.zip")
        ZipOutputStream(FileOutputStream(maliciousZipFile)).use { zos ->
            val entry = ZipEntry("../../evil_file.txt")
            zos.putNextEntry(entry)
            zos.write("Malicious payload".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val mockUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(mockUri) } answers {
            FileInputStream(maliciousZipFile)
        }

        val result = backupManager.importBackup(mockUri, password = null)
        assertTrue("Zararlı zip içeriği reddedilmeli", result.isFailure)

        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        val msg = exception?.message.orEmpty()
        assertTrue(
            "Zip Slip uyarısı içermeli: $msg",
            msg.contains("Path Traversal", ignoreCase = true) ||
                    msg.contains("Güvensiz", ignoreCase = true) ||
                    exception is SecurityException
        )
    }

    @Test
    fun manifestDogrulama_gecersizImzadaReddedilir() = runTest {
        val invalidManifest = BackupManifest(
            appSignature = "com.baskauygulama", // Geçersiz imza
            version = 1,
            exportDate = System.currentTimeMillis(),
            questionCount = 1,
            sorular = emptyList()
        )
        val manifestJson = json.encodeToString(invalidManifest)

        val zipFile = tempFolder.newFile("invalid_app.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("backup_manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val mockUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(mockUri) } answers {
            FileInputStream(zipFile)
        }

        val result = backupManager.importBackup(mockUri, password = null)
        assertTrue("Geçersiz imza reddedilmeli", result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("geçerli bir Soru Defterim yedeği değil", ignoreCase = true) == true
        )
    }

    @Test
    fun manifestDogrulama_manifestEksikOldugundaHataVerir() = runTest {
        val zipFile = tempFolder.newFile("no_manifest.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("images/soru_1.jpg"))
            zos.write("Fake image data".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val mockUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(mockUri) } answers {
            FileInputStream(zipFile)
        }

        val result = backupManager.importBackup(mockUri, password = null)
        assertTrue("Manifest bulunamayınca başarısız olmalı", result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("backup_manifest.json bulunamadı", ignoreCase = true) == true
        )
    }

    @Test
    fun isBackupEncrypted_sihirliBasligiDogruTespitEder() = runTest {
        // 1. Şifreli dosya
        val encFile = tempFolder.newFile("encrypted.sdefteri")
        FileOutputStream(encFile).use { out ->
            out.write(MAGIC_HEADER)
            out.write(ByteArray(50))
        }

        val encUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(encUri) } answers { FileInputStream(encFile) }
        assertTrue("Şifreli dosya tespit edilmeli", backupManager.isBackupEncrypted(encUri))

        // 2. Düz dosya
        val plainFile = tempFolder.newFile("plain.zip")
        FileOutputStream(plainFile).use { out ->
            out.write("PK\u0003\u0004".toByteArray(Charsets.US_ASCII))
        }

        val plainUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(plainUri) } answers { FileInputStream(plainFile) }
        assertFalse("Şifresiz dosya şifreli algılanmamalı", backupManager.isBackupEncrypted(plainUri))
    }

    @Test
    fun legacyFallback_10000IterasyonluYedek_120000IleAcilamazsaOtomatikDenenir() = runTest {
        // Eski iterasyon (10000) ile şifrelenmiş bir yedeği hazırla; yeni kodun fallback (120000 -> 10000) ile açabilmesi lazım
        val password = "LegacyTestParola123!"
        val soru = Soru(
            id = 1L,
            imagePath = tempFolder.newFile("test_img.jpg").apply { writeText("fake image") }.absolutePath,
            examType = "TYT",
            subjectId = "matematik",
            topic = "Fonksiyonlar"
        )
        // Repo mock'u boş liste döndürse de, manuel zip oluşturup legacy ile şifreleyeceğiz
        // Adım 1: Geçici ZIP oluştur (manifest + dummy image)
        val plainZipFile = tempFolder.newFile("plain_legacy.zip")
        ZipOutputStream(FileOutputStream(plainZipFile)).use { zos ->
            val manifest = BackupManifest(
                appSignature = "com.sorudefteri",
                version = 2,
                exportDate = System.currentTimeMillis(),
                questionCount = 0,
                sorular = emptyList()
            )
            val manifestJson = json.encodeToString(manifest)
            zos.putNextEntry(ZipEntry("backup_manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        // Adım 2: Legacy PBKDF2 10000 ile şifrele
        val legacySalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val legacyIv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val legacyKeySpec = PBEKeySpec(password.toCharArray(), legacySalt, 10000, 256)
        val legacyKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val legacyKeyBytes = legacyKeyFactory.generateSecret(legacyKeySpec).encoded
        val legacySecret = SecretKeySpec(legacyKeyBytes, "AES")
        val legacyCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, legacySecret, GCMParameterSpec(128, legacyIv))
        }
        val encryptedFile = tempFolder.newFile("legacy_encrypted.sdefteri")
        FileOutputStream(encryptedFile).use { out ->
            out.write(MAGIC_HEADER)
            out.write(legacySalt)
            out.write(legacyIv)
            CipherOutputStream(out, legacyCipher).use { cipherOut ->
                FileInputStream(plainZipFile).use { fis ->
                    fis.copyTo(cipherOut)
                }
            }
        }

        // Adım 3: BackupManager ile şifre çöz ve import et — yeni kod önce 120000 dener, başarısız olunca 10000 fallback'a düşer
        val mockUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(mockUri) } answers { FileInputStream(encryptedFile) }
        // Repo insert çağrısını mockla
        coEvery { mockSoruRepository.getAllSorular() } returns flowOf(emptyList())
        coEvery { mockSoruRepository.insertAllSorular(any()) } returns emptyList()
        coEvery { mockGorselDepo.kaydetDosyadan(any(), any()) } returns "/fake/path.jpg"

        val result = backupManager.importBackup(mockUri, password = password)
        assertTrue("Legacy 10000 iterasyonlu yedek fallback ile açılabilmeli: ${result.exceptionOrNull()?.message}", result.isSuccess)
    }

    @Test
    fun zipSlip_ikinciVaryant_absolutePath_engellenir() = runTest {
        val maliciousZipFile = tempFolder.newFile("malicious2.zip")
        ZipOutputStream(FileOutputStream(maliciousZipFile)).use { zos ->
            val entry = ZipEntry("/etc/passwd")
            zos.putNextEntry(entry)
            zos.write("root:x:0:0".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val mockUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(mockUri) } answers { FileInputStream(maliciousZipFile) }
        val result = backupManager.importBackup(mockUri, password = null)
        assertTrue("Absolute path traversal engellenmeli", result.isFailure)
    }

    // =====================================================================
    // AKILLI İÇE AKTARMA (dedup/merge) TESTLERİ
    // =====================================================================

    /** Gerçek yazma davranışına sahip sahte DAO + dedup DAO'nun bağlı olduğu BackupManager. */
    private fun dedupYoneticiVeDaoAyari(): Pair<BackupManager, com.sorudefteri.data.local.FakeSoruDao> {
        val fakeDao = com.sorudefteri.data.local.FakeSoruDao()
        coEvery { mockSoruRepository.getAllSorular() } returns flowOf(emptyList())
        coEvery { mockGorselDepo.kaydetDosyadan(any(), any()) } returns "/fake/path.jpg"
        // insertAllSorular -> fakeDao'ya gerçek yazma (contentHash dahil korunur)
        coEvery { mockSoruRepository.insertAllSorular(any()) } coAnswers {
            val gelenListe = firstArg<List<Soru>>()
            gelenListe.map { soru -> fakeDao.insertSoru(soru.copy(id = 0).toEntity()) }
        }
        val dedupManager = BackupManager(
            context = mockContext,
            soruRepository = mockSoruRepository,
            gorselDepo = mockGorselDepo,
            json = json,
            soruDedupDao = fakeDao
        )
        return dedupManager to fakeDao
    }

    /** İki soruluk düz (şifresiz) yedek ZIP'i hazırlar. */
    private fun ikiSoruluManifestYaz(
        tempSuffix: String,
        soru2Readjust: (BackupSoruItem) -> BackupSoruItem = { it }
    ): Uri {
        val soru1 = BackupSoruItem(
            originalId = 1L,
            examType = "TYT",
            subjectId = "matematik",
            topic = "Türev",
            questionText = "2x + 3 = 11 denkleminin çözümü kaçtır?"
        )
        val soru2 = soru2Readjust(
            BackupSoruItem(
                originalId = 2L,
                examType = "TYT",
                subjectId = "fizik",
                topic = "Hareket",
                questionText = "Sabit hızlı harekette 5 saniyede 100 m yol kaç metre hızla alınır?"
            )
        )
        val manifest = BackupManifest(
            questionCount = 2,
            sorular = listOf(soru1, soru2)
        )
        val zipFile = tempFolder.newFile("akilli_ice_aktarma_$tempSuffix.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("backup_manifest.json"))
            zos.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val mockUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(mockUri) } answers { FileInputStream(zipFile) }
        return mockUri
    }

    @Test
    fun akilliIceAktarma_ikinciAktarimdaMukerrerSorularEklenmezGuncelSayilir() = runTest {
        val (dedupManager, fakeDao) = dedupYoneticiVeDaoAyari()
        val uri = ikiSoruluManifestYaz("birinci")

        // 1. AKTARIM: iki soru da yeni
        val birinci = dedupManager.importBackup(uri, password = null)
        assertTrue("İlk aktarımda 2 soru eklenmeli", birinci.isSuccess)
        assertEquals(2, birinci.getOrDefault(-1))
        assertEquals(2, fakeDao.satirSayisi())

        val ozet1 = dedupManager.sonImportOzeti
        assertNotNull(ozet1)
        assertEquals(2, ozet1?.soruSayisi)
        assertEquals(0, ozet1?.guncelSoru)
        assertEquals(0, ozet1?.guncellenenSoru)

        // 2. AKTARIM: aynı içerik tekrar gelir → hiçbiri tekrar eklenmemeli
        val ikinci = dedupManager.importBackup(ikiSoruluManifestYaz("ikinci"), password = null)
        assertTrue("İkinci aktarım başarılı olmalı", ikinci.isSuccess)
        assertEquals(0, ikinci.getOrDefault(-1))
        assertEquals("Mükerrer sorular için yeni satır eklenmemeli", 2, fakeDao.satirSayisi())

        val ozet2 = dedupManager.sonImportOzeti
        assertNotNull(ozet2)
        assertEquals(0, ozet2?.soruSayisi)
        assertEquals("İki soru da zaten güncel sayılmalı", 2, ozet2?.guncelSoru)
        assertEquals(0, ozet2?.guncellenenSoru)
    }

    @Test
    fun akilliIceAktarma_ayniSorununZenginlesmisKopyasi_mevcutKaydiGunceller() = runTest {
        val (dedupManager, fakeDao) = dedupYoneticiVeDaoAyari()
        val birinciUri = ikiSoruluManifestYaz("zengin_birinci")
        assertTrue(dedupManager.importBackup(birinciUri, password = null).isSuccess)

        // Aynı içerik (aynı hash), ama not eklenmiş kopya → mevcut kayıt güncellenmeli
        val zenginUri = ikiSoruluManifestYaz("zengin_ikinci") { soru ->
            soru.copy(note = "sınav öncesi tekrar et")
        }
        val ikinciSonuc = dedupManager.importBackup(zenginUri, password = null)
        assertTrue("İkinci aktarım başarılı olmalı", ikinciSonuc.isSuccess)
        assertEquals(0, ikinciSonuc.getOrDefault(-1))
        assertEquals("Yeni satır eklenmemeli, id korunmalı", 2, fakeDao.satirSayisi())

        val ozet = dedupManager.sonImportOzeti
        assertEquals(1, ozet?.guncellenenSoru)
        assertEquals(1, ozet?.guncelSoru)

        // Not gerçekten mevcut kayda yazılmış olmalı
        val notluSoruHash = com.sorudefteri.data.local.SoruIcerikHash.hesapla(
            examType = "TYT",
            subjectId = "fizik",
            topic = "Hareket",
            questionText = "Sabit hızlı harekette 5 saniyede 100 m yol kaç metre hızla alınır?",
            ocrMetni = null,
            kaynakYayin = null,
            kaynakDeneme = null,
            kaynakYil = null,
            kaynakSoruNo = null
        )
        val guncellenenSoru = fakeDao.getByContentHashes(listOf(notluSoruHash)).firstOrNull()
        assertNotNull("Merge edilen soru bulunmalı", guncellenenSoru)
        assertEquals("sınav öncesi tekrar et", guncellenenSoru?.note)
    }
}
