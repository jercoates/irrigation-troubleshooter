package io.github.jercoates.irrigationtroubleshooter

import org.json.JSONArray

/** A meter-reference section, generated into assets/help.json. Body paragraphs are simple HTML. */
data class HelpSection(val key: String, val title: String, val body: List<String>)

object HelpData {
    fun parse(json: String): List<HelpSection> {
        val a = JSONArray(json)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val b = o.getJSONArray("body")
            HelpSection(o.getString("key"), o.getString("title"), List(b.length()) { b.getString(it) })
        }
    }
}
