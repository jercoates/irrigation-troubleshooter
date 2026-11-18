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
    internal val titleLc = title.lowercase()
    internal val crumbLc = crumb.lowercase()
    internal val textLc = text.lowercase()
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

    /** Every whitespace-separated term must match somewhere; title hits outrank crumb hits outrank body hits. */
    fun search(entries: List<SearchEntry>, query: String): List<SearchEntry> {
        val terms = query.lowercase().map { if (it.isWhitespace()) ' ' else it }.joinToString("").split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return entries
            .mapNotNull { e ->
                var score = 0
                for (t in terms) {
                    val s = (if (t in e.titleLc) 3 else 0) + (if (t in e.crumbLc) 2 else 0) + (if (t in e.textLc) 1 else 0)
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
