package com.sorudefteri.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsQuerySanitizerTest {

    @Test
    fun `null and empty inputs return empty string`() {
        assertEquals("", FtsQuerySanitizer.sanitize(null))
        assertEquals("", FtsQuerySanitizer.sanitize(""))
        assertEquals("", FtsQuerySanitizer.sanitize("   "))
    }

    @Test
    fun `special FTS characters are stripped safely`() {
        assertEquals("", FtsQuerySanitizer.sanitize("\""))
        assertEquals("", FtsQuerySanitizer.sanitize("\"\"\""))
        assertEquals("", FtsQuerySanitizer.sanitize("***"))
        assertEquals("", FtsQuerySanitizer.sanitize("():::---"))
    }

    @Test
    fun `reserved FTS keywords alone return empty string`() {
        assertEquals("", FtsQuerySanitizer.sanitize("AND"))
        assertEquals("", FtsQuerySanitizer.sanitize("OR"))
        assertEquals("", FtsQuerySanitizer.sanitize("NOT"))
        assertEquals("", FtsQuerySanitizer.sanitize("NEAR"))
        assertEquals("", FtsQuerySanitizer.sanitize("and or not near"))
    }

    @Test
    fun `valid search words are converted to safe prefix search tokens`() {
        val result = FtsQuerySanitizer.sanitize("fizik dinamik")
        assertEquals("\"fizik\"* \"dinamik\"*", result)
    }

    @Test
    fun `complex mixed input with quotes and boolean operators is sanitized safely`() {
        val result = FtsQuerySanitizer.sanitize("(fizik:kuvvet) AND NOT kimya*")
        assertTrue(result.contains("\"fizik\"*"))
        assertTrue(result.contains("\"kuvvet\"*"))
        assertTrue(result.contains("\"kimya\"*"))
        // AND and NOT must not appear as unescaped operators
        assertTrue(!result.contains("AND"))
        assertTrue(!result.contains("NOT"))
    }
}
