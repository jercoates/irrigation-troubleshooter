package io.github.jercoates.irrigationtroubleshooter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SearchIndexTest {

    // Unit tests run with the module directory as working dir.
    private val assets = File("src/main/assets")

    private fun real() = SearchIndex.parse(File(assets, "search-index.json").readText())

    @Test fun realIndexLoadsAndEveryPathIsWellFormed() {
        val entries = real()
        assertTrue(entries.size > 10)
        val ids = entries.map { it.id }.toSet()
        for (e in entries) {
            assertEquals("start", e.path.first())
            assertEquals(e.id, e.path.last())
            assertTrue(Protocol.sanitizeStack(e.path) != null)
            assertTrue(ids.containsAll(e.path))
        }
    }

    @Test fun realHelpLoads() {
        val help = HelpData.parse(File(assets, "help.json").readText())
        assertTrue(help.isNotEmpty())
        assertTrue(help.all { it.title.isNotBlank() && it.body.isNotEmpty() })
    }

    @Test fun searchRanksTitleAboveBodyAndRequiresAllTerms() {
        val a = SearchEntry("a", false, "Valve pulses", "Zone", emptyList(), listOf("start", "a"), "chatter")
        val b = SearchEntry("b", false, "Other", "Zone", emptyList(), listOf("start", "b"), "the valve pulses sometimes")
        val hits = SearchIndex.search(listOf(b, a), "valve pulses")
        assertEquals(listOf("a", "b"), hits.map { it.id })
        assertTrue(SearchIndex.search(listOf(a, b), "valve nonsense").isEmpty())
        assertTrue(SearchIndex.search(listOf(a, b), "   ").isEmpty())
    }

    @Test fun searchIsCaseInsensitive() {
        val a = SearchEntry("a", false, "Valve PULSES", "Zone", emptyList(), listOf("start", "a"), "")
        assertEquals(1, SearchIndex.search(listOf(a), "pulses").size)
    }
}
