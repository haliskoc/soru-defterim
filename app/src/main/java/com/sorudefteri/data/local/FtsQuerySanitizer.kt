package com.sorudefteri.data.local

import java.util.Locale

/**
 * SQLite FTS (Full-Text Search) için kullanıcı aramalarını güvenli hale getiren yardımcı nesne.
 * Dengesiz tırnak, iki nokta, parantez, joker karakterler (*) veya FTS boolean operatörleri
 * (AND, OR, NOT, NEAR) nedeniyle oluşabilecek SQLiteException: syntax error çökmelerini engeller.
 */
object FtsQuerySanitizer {

    private val FTS_SPECIAL_CHARS = Regex("[*\"'():^+\\-/{}\\[\\]~?!=><|;,`\\\\]")
    private val RESERVED_FTS_WORDS = setOf("AND", "OR", "NOT", "NEAR")

    /**
     * Ham arama metnini sanitize eder.
     * Boş veya geçersiz sorgularda boş string döner.
     */
    fun sanitize(rawQuery: String?): String {
        if (rawQuery.isNullOrBlank()) return ""

        val cleaned = rawQuery.replace(FTS_SPECIAL_CHARS, " ").trim()
        if (cleaned.isBlank()) return ""

        val tokens = cleaned.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it.uppercase(Locale.ROOT) !in RESERVED_FTS_WORDS }

        if (tokens.isEmpty()) return ""

        return tokens.joinToString(" ") { token ->
            val escaped = token.replace("\"", "\"\"")
            "\"$escaped\"*"
        }
    }
}
