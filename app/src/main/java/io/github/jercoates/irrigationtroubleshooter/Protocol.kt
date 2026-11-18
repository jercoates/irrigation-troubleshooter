package io.github.jercoates.irrigationtroubleshooter

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Messages the page posts to native through `androidBridge.postMessage`. */
sealed interface BridgeMessage {
    data object Ready : BridgeMessage
    data object Ack : BridgeMessage
    data class State(val node: String, val crumb: String, val stack: List<String>, val isEnd: Boolean) : BridgeMessage
}

/**
 * Parsing/validation for everything that crosses the JS boundary or comes back from storage.
 * The page is bundled and trusted, but we still treat its messages as untrusted input.
 */
object Protocol {
    const val MAX_MESSAGE_CHARS = 8192
    const val MAX_STACK = 200
    private const val MAX_CRUMB = 120
    private val ID = Regex("^[A-Za-z0-9_]{1,64}$")

    fun isValidId(s: String?): Boolean = s != null && ID.matches(s)

    /** Returns the stack if it is a plausible node-id path, else null. Tree-level checks happen in the page. */
    fun sanitizeStack(stack: List<String>?): List<String>? {
        if (stack == null || stack.isEmpty() || stack.size > MAX_STACK) return null
        if (stack[0] != "start") return null
        return if (stack.all(::isValidId)) stack.toList() else null
    }

    val DEFAULT_STACK = listOf("start")

    /** JSON array literal; safe to splice into JS because ids are restricted to [A-Za-z0-9_]. */
    fun stackToJs(stack: List<String>): String =
        JSONArray(sanitizeStack(stack) ?: DEFAULT_STACK).toString()

    fun parseStack(json: String?): List<String>? = try {
        if (json == null) null else {
            val a = JSONArray(json)
            sanitizeStack(List(a.length()) { a.optString(it, "") })
        }
    } catch (_: JSONException) {
        null
    }

    fun parse(raw: String?): BridgeMessage? {
        if (raw == null || raw.length > MAX_MESSAGE_CHARS) return null
        return try {
            val o = JSONObject(raw)
            when (o.optString("type")) {
                "ready" -> BridgeMessage.Ready
                "ack" -> BridgeMessage.Ack
                "state" -> {
                    val node = o.optString("node")
                    val arr = o.optJSONArray("stack") ?: return null
                    val stack = sanitizeStack(List(arr.length()) { arr.optString(it, "") }) ?: return null
                    if (!isValidId(node) || stack.last() != node) return null
                    BridgeMessage.State(node, o.optString("crumb").take(MAX_CRUMB), stack, o.optBoolean("isEnd", false))
                }
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

    /** `evaluateJavascript` hands back a JSON-encoded value; unwrap a string result. */
    fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return try {
            JSONArray("[$raw]").optString(0).ifEmpty { null }
        } catch (_: JSONException) {
            null
        }
    }
}
