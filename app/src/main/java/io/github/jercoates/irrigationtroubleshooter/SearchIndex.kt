package io.github.jercoates.irrigationtroubleshooter

import org.json.JSONArray

/** One node of the troubleshooting tree, as generated into assets/search-index.json. */
data class SearchEntry(
    val id: String,
    val isResult: Boolean,
    val title: String,
    val crumb: String,
    val trail: List<String>,
    val path: List<String>,
    val text: String,
) {
    internal val titleWords = SearchIndex.words(title)
    internal val crumbWords = SearchIndex.words(crumb)
    internal val textWords = SearchIndex.words(text)
}

object SearchIndex {
    private const val MAX_RESULTS = 50

    fun parse(json: String): List<SearchEntry> {
        val a = JSONArray(json)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val trail = o.getJSONArray("trail")
            val path = o.getJSONArray("path")
            SearchEntry(
                id = o.getString("id"),
                isResult = o.optBoolean("end"),
                title = o.getString("title"),
                crumb = o.optString("crumb"),
                trail = List(trail.length()) { trail.getString(it) },
                path = List(path.length()) { path.getString(it) },
                text = o.optString("text"),
            )
        }
    }

    /** Lowercase words, split on anything that is not a letter or digit (so "valve?" and "valve" agree). */
    internal fun words(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (ch in s.lowercase()) {
            if (ch.isLetterOrDigit()) cur.append(ch)
            else if (cur.isNotEmpty()) { out.add(cur.toString()); cur.setLength(0) }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun hit(words: List<String>, term: String) = words.any { it.startsWith(term) }

    /**
     * Every term must match the START of some word (so "on" does not match "controller");
     * title hits outrank crumb hits outrank body hits.
     */
    fun search(entries: List<SearchEntry>, query: String): List<SearchEntry> {
        val terms = words(query)
        if (terms.isEmpty()) return emptyList()
        return entries
            .mapNotNull { e ->
                var score = 0
                for (t in terms) {
                    val s = (if (hit(e.titleWords, t)) 3 else 0) + (if (hit(e.crumbWords, t)) 2 else 0) + (if (hit(e.textWords, t)) 1 else 0)
                    if (s == 0) return@mapNotNull null
                    score += s
                }
                e to score
            }
            .sortedByDescending { it.second }   // stable: ties keep tree order
            .take(MAX_RESULTS)
            .map { it.first }
    }
}
