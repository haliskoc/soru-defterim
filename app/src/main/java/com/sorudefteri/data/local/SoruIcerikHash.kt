package com.sorudefteri.data.local

import java.security.MessageDigest
import java.util.Locale

/**
 * Akıllı icerik hash: icerik parmak izi hesaplar.
 * Aynı icerikli soru farkli icedekelerden gelirse dedup ile tanınır.
 */
object SoruIcerikHash {

    fun hesapla(soru: com.sorudefteri.domain.model.Soru): String {
        return hesapla(
            examType = soru.examType,
            subjectId = soru.subjectId,
            topic = soru.topic,
            questionText = soru.questionText,
            ocrMetni = soru.ocrMetni,
            kaynakYayin = soru.kaynakYayin,
            kaynakDeneme = soru.kaynakDeneme,
            kaynakYil = soru.kaynakYil,
            kaynakSoruNo = soru.kaynakSoruNo
        )
    }

    fun hesapla(
        examType: String,
        subjectId: String,
        topic: String,
        questionText: String?,
        ocrMetni: String?,
        kaynakYayin: String?,
        kaynakDeneme: String?,
        kaynakYil: Int?,
        kaynakSoruNo: Int?
    ): String {
        val metin = normalize(questionText) ?: normalize(ocrMetni)
        val kaynakVar = listOf(kaynakYayin, kaynakDeneme).any { !it.isNullOrBlank() } ||
            (kaynakYil != null && kaynakYil > 0) ||
            (kaynakSoruNo != null && kaynakSoruNo > 0)

        if (metin == null && !kaynakVar) {
            return ""
        }

        val parts = listOf(
            "V1",
            normalizeKey(examType),
            normalizeKey(subjectId),
            normalizeKey(topic),
            metin ?: "",
            normalizeKey(kaynakYayin),
            normalizeKey(kaynakDeneme),
            yilDeger(kaynakYil),
            noDeger(kaynakSoruNo)
        )
        return sha256(parts.joinToString("|"))
    }

    private fun normalizeKey(value: String?): String {
        val norm = normalize(value) ?: return ""
        return norm
    }

    private fun yilDeger(value: Int?): String {
        return if (value != null && value > 0) value.toString() else ""
    }

    private fun noDeger(value: Int?): String {
        return if (value != null && value > 0) value.toString() else ""
    }

    /** Icindeki metni kucuk harf ve tek bosluklu hale getirir. */
    internal fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim().lowercase(Locale("tr"))
        s = s.replace(Regex("\\s+"), " ")
        s = s.replace(Regex("^(\\s*)(-{2,})(\\s*)"), "")
        s = s.replace(Regex("(\\s*)(-{2,})$"), "")
        if (s.isBlank()) return null
        return s
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder()
        for (b in digest) {
            builder.append(String.format("%02x", b))
        }
        return builder.toString()
    }
}
