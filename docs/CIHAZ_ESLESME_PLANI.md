# Telefon ↔ Tablet Yakın-Cihaz Eşleşme Planı (Nearby Connections)

**Belge sürümü:** v1.0 — plan (uygulama bu belgeye göre yapılacak)
**Tarih:** 2026-10-18
**Tür:** Tasarım + olasılık planı
**Temel karar:** Google Nearby Connections API (`play-services-nearby`), `P2P_POINT_TO_POINT`, evrensel soru kimliği (`evrenselId` + `updatedAt` + son-yazan-kazanır)

---

## 1. Mimari kararlar

| Karar | Seçim | Gerekçe |
|---|---|---|
| Taşıma | Nearby Connections, `P2P_POINT_TO_POINT` | 2 cihaz, en yüksek bant; BLE keşif + Wi-Fi aktarım, uçtan uca şifreli, internetsiz. STAR/CLUSTER çok-cihaza göre, bize fazla. |
| Kimlik | `evrenselId` (UUID v4, değişmez) + `updatedAt` | Kullanıcının tarifi birebir. Mevcut `id: Long` cihaza-yerel kalır (FTS docid, FK'lar, backup bozulmaz). |
| Çakışma | Son-yazan-kazanır (`updatedAt` büyük olan), `isFavorite` OR-birleşir, `createdAt` min alınır | Basit, öngörülebilir, test edilebilir. Alan-bazlı merge yok (v1). |
| Saat farkı | ±60 sn tolerans; tolerans içindeyse `contentHash` farklıysa yenisi kazanır | İki cihazın saati genelde aynı ağda NTP'li; yine de koruma. |
| Silme | v1'de yayılmaz (ertelendi); `isDeleted` kolonu şemada hazır | Kullanıcı akışı soru tamamlama; silme senkronu v2. `softDeleteById` motor hazır bekler. |
| Sıra | Tek yönlü fazlar: başlatan önce ALIR, sonra GÖNDERİR (`SIRA_SENDE` jetonu) | Aynı-anda çift-yön = deadlock/karışma riski. Sıralı = basit + idempotent resume. |
| Kapsam v1 | Soru kaydı + soru/çözüm görseli | Ses, karalama, chat, benzer-soru, test sonucu, streak/XP v2 (cihaz-yerel kalır). |
| İdempotans | Her soru paketi bağımsız upsert (`evrenselId` ile) | Kopma → kaldığı yerden devam, tekrar paket zararsız. |

## 2. Protokol (JSON, BYTES ≤ ~900 KB chunk; görsel = FILE payload)

```
HELLO        {proto:1, cihazAdi, soruSayisi, appVersion}
OZET         {paket: i/n, liste:[{e, u, h}]}   // e=evrenselId u=updatedAt h=contentHash
OZET_BITTI   {}
SIRA_SENDE   {}
SORU         {paket: i/n, soru:{...BackupSoruItem + evrenselId + updatedAt + isDeleted}, gorsel:[{tur, dosyaId}]}
SORU_BITTI   {gonderilen, atlanan}
OZET_SONUC   {eklenen, guncellenen, atlanan, cakisan, gorselsiz}   // her iki taraf kendi ekranında gösterir
HATA         {kod, mesaj}
```

- Service ID: `com.sorudefteri.sync.v1`. Uç adı: `Soru Defterim • <MODEL>`.
- PIN: `authenticationDigits` iki tarafta da gösterilir, eşleşmiyorsa reddet.
- BYTES limiti 1.048.576 B → chunk 900 KB. Özet 1000 soruda bile ~100 KB, genelde tek paket.

## 3. Eşleşme akışı (kullanıcı gözüyle)

1. İki cihazda Ayarlar → "Yakındaki Cihazla Eşle" → tek ekran, "Eşleşmeyi Başlat" (aynı anda advertise + discover).
2. Liste: bulunan cihazlar (kendi cihazı elenir). Seç → karşıda PIN dialogu → iki tarafta 5 haneli kod görünür → ikisi de Onayla.
3. Otomatik: HELLO → ÖZET değişimi → "3 soru alınacak, 5 gönderilecek" önizleme → otomatik başlar (onaylı).
4. İlerleme çubuğu (soru i/N + görsel MB). Koparsa: "Bağlantı koptu — kaldığı yerden devam et" butonu.
5. Bitiş özeti iki tarafta: eklenen / güncellenen / zaten güncel / çakışan (karşı taraf yenisiyle ezildi) / görselsiz.

## 4. Olasılık matrisi (hepsi ele alındı)

| # | Durum | Davranış | Nerede |
|---|---|---|---|
| 1 | Aynı soru iki cihazda da değişmiş | `updatedAt` yenisi kazanır; kaybeden "çakışan" sayacına + log | SyncMotor.karar() |
| 2 | Saatler ±60 sn farklı | Tolerans içi + hash aynı → ATLA; hash farklı → yenisi kazanır | SyncMotor.karar() |
| 3 | Bir tarafta silinmiş soru | v1'de silme yayılmaz (normal `deleteSoru` kalıcı); silinen karşıdan geri gelebilir → v2'de soft-delete aktifleşecek | Plan §1 |
| 4 | Silinen soruda karşı taraf değişiklik yapmış | v1'de normal merge (yenisi kazanır); silme-tombstone v2 | Plan §1 |
| 5 | Görsel dosyası eksik/bozuk | Soru metin olarak alınır, `gorselsiz` sayacı | SyncDeposu (import mantığıyla aynı) |
| 6 | Ortada bağlantı kopması | Her paket idempotent upsert; devam = yeniden ÖZET + sadece eksikler | SyncDeposu + UI resume |
| 7 | Aynı anda iki yön gönderim | Yasak: `SIRA_SENDE` jetonu, sırayla | Protokol |
| 8 | Özet/paket > BYTES limiti | 900 KB chunk (`paket: i/n`), alıcı birleştirir | SyncProtokol |
| 9 | Üçüncü cihaz bağlanmak ister | P2P tek bağlantı; meşgulken `rejectConnection` + "meşgul" mesajı | CihazEslestirme |
| 10 | Kendini listede görme | `ANDROID_ID` özeti HELLO'da; eşleşen endpoint yoksayılır | CihazEslestirme |
| 11 | Protokol/uygulama sürüm farkı | HELLO'da `proto` + `appVersion`; uyumsuzsa HATA + "uygulamayı güncelleyin" | SyncDeposu |
| 12 | İzin reddi (BT/Wi-Fi/konum) | Adım adım açıklama + sistem ayarına yönlendirme; reklam/keşif başlamaz | UI izin akışı |
| 13 | Play Services yok/eski | `ConnectionsClient` çağrısı try/catch + "Play Hizmetleri gerekli" mesajı | CihazEslestirme |
| 14 | Uygulama arka plana alınır | Aktarım durur; dönüşte kaldığı yerden devam (özet yeniden) | UI uyarısı + resume |
| 15 | Pil/ısı | Advertise+discover yalnız ekran açıkken; çıkışta `stopAllEndpoints()` | UI lifecycle |
| 16 | Aynı soru aynı anda iki cihazda AÇIK (detay ekranı) | DB Flow otomatik yeniler; kullanıcı fark etmez | Mevcut reaktivite |
| 17 | Favori bir tarafta, not diğerinde yeni | Kayıt yenisi kazanır AMA `isFavorite` OR-birleşir | SyncMotor.birlestir() |
| 18 | `contentHash` boş (metinsiz+kaynaksız) | `evrenselId` yine eşleştirir (hash'e bağımlı değil) | SyncMotor (anahtar=e) |
| 19 | Eski cihazda `evrenselId` yok (v12 öncesi kayıt) | Migration'da geriye dönük UUID üretilir; boş kalan olmaz | MIGRATION_11_12 |
| 20 | Aktarım sırasında yeni soru eklenir | O turda yok sayılır; sonraki eşleşmede gelir (özet anlık snapshot) | SyncDeposu |

## 5. Veri katmanı değişiklikleri (v12)

- `SoruEntity`: `evrenselId: String = ""` (unique index), `isDeleted: Int = 0`.
- `Soru` domain: aynı iki alan.
- `MIGRATION_11_12`: kolonlar + index + mevcut satırlara `lower(hex(randomblob(16)))` formatında UUID backfill (32 hex; yeni kayıtlar standart UUID).
- `SoruRepositoryImpl.soruEkle`: `evrenselId` boşsa `UUID.randomUUID()` üret.
- DAO: `getByEvrenselIdler(ids)`, `getEslestirmeOzeti()` (evrenselId, updatedAt, contentHash, isDeleted projeksiyonu), `upsertEvrensel()` (varsa updateEntityFull + updatedAt, yoksa insert).
- `BackupSoruItem`: `evrenselId`, `updatedAt`, `isDeleted` ekle (geriye uyumlu: varsayılanlı; `ignoreUnknownKeys=true` zaten var).

## 6. Dosya listesi (uygulanacak)

- `data/sync/SyncProtokol.kt` — mesaj modelleri (@Serializable), chunk encode/decode (saf, testli).
- `data/sync/SyncMotor.kt` — `karar(yerel, uzak)` → EKLE/GUNCELLE/ATLA/SIL; `birlestir()` (favori OR, createdAt min) (saf, testli).
- `data/sync/CihazEslestirme.kt` — Nearby wrapper: advertise/discover/bağlan/PIN/gönder-al/dosya/kopma (Hilt @Singleton, @ApplicationContext).
- `data/sync/SyncDeposu.kt` — orkestrasyon: özet→fark→fazlı aktarım→merge→sonuç (SoruDao + GorselDepo kullanır).
- `ui/eslesme/CihazEslemeScreen.kt` + `CihazEslemeViewModel.kt` — keşif/PIN/ilerleme/özet + izin akışı.
- `ui/navigation/Screen.kt`: `CihazEslemeNav`; `AppNavHost`: composable; Ayarlar → giriş butonu.
- `AndroidManifest.xml`: BT trio (31+), NEARBY_WIFI_DEVICES (32+), FINE_LOCATION (29–31), legacy maxSdkVersion'lılar.
- `gradle/libs.versions.toml` + `app/build.gradle.kts`: `play-services-nearby:19.5.0`.
- Test: `SyncMotorTest`, `SyncProtokolTest` (chunk + karar matrisinin her satırı).

## 7. Fazlar

- **Faz A (bu iş):** plan + v12 + motor/protokol + Nearby + depo + UI + izin + test + derleme.
- **v2 (ertelendi):** ses/karalama/chat/benzer-soru/test-sonucu senkronu, tombstone temizliği (30 gün), arka-plan otomatik eşleşme, 3+ cihaz.
