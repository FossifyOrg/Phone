package org.fossify.phone.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {
    @Test
    fun `strips accents and lowercases`() {
        assertEquals("jose conceicao", normalizeForSearch("José Conceição"))
        assertEquals("joao", normalizeForSearch("JOÃO"))
        assertEquals("ines", normalizeForSearch("Inês"))
    }

    @Test
    fun `collapses and trims whitespace`() {
        assertEquals("ana maria", normalizeForSearch("  Ana   Maria "))
    }

    @Test
    fun `empty stays empty`() {
        assertEquals("", normalizeForSearch(""))
    }

    @Test
    fun `accent insensitive in both directions`() {
        val savedWithAccent = listOf(normalizeForSearch("José Silva"))
        val savedWithoutAccent = listOf(normalizeForSearch("Jose Silva"))

        assertTrue(searchFieldsMatch(normalizeForSearch("jose"), savedWithAccent))
        assertTrue(searchFieldsMatch(normalizeForSearch("José"), savedWithoutAccent))
        assertTrue(searchFieldsMatch(normalizeForSearch("JOSÉ"), savedWithAccent))
    }

    @Test
    fun `matches any field and partial text`() {
        val fields = listOf("Maria", "Câmara Municipal", "maria@example.org").map { normalizeForSearch(it) }
        assertTrue(searchFieldsMatch(normalizeForSearch("camara"), fields))
        assertTrue(searchFieldsMatch(normalizeForSearch("example"), fields))
        assertFalse(searchFieldsMatch(normalizeForSearch("lisboa"), fields))
    }

    @Test
    fun `empty query matches everything`() {
        assertTrue(searchFieldsMatch(normalizeForSearch(""), listOf("anything")))
    }

    @Test
    fun `normalizing five thousand contacts is fast`() {
        val names = (0 until 5000).map { "Contacto Número $it Conceição" }
        val start = System.nanoTime()
        val index = names.map { listOf(normalizeForSearch(it)) }
        val query = normalizeForSearch("4999 Conceicao")
        val hits = index.count { searchFieldsMatch(query, it) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(1, hits)
        assertTrue("took $elapsedMs ms", elapsedMs < 2000)
    }
}
