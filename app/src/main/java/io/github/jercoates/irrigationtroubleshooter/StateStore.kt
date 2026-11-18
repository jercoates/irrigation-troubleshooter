package io.github.jercoates.irrigationtroubleshooter

import android.content.Context
import androidx.core.content.edit

/** Everything the app remembers, all on-device, none of it transmitted. */
class StateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("state", Context.MODE_PRIVATE)

    var acknowledged: Boolean
        get() = prefs.getBoolean(KEY_ACK, false)
        set(v) = prefs.edit { putBoolean(KEY_ACK, v) }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ON, true)
        set(v) = prefs.edit { putBoolean(KEY_KEEP_ON, v) }

    var stack: List<String>
        get() = Protocol.parseStack(prefs.getString(KEY_STACK, null)) ?: Protocol.DEFAULT_STACK
        set(v) = prefs.edit { putString(KEY_STACK, Protocol.stackToJs(v)) }

    private companion object {
        const val KEY_ACK = "ack"
        const val KEY_KEEP_ON = "keep_on"
        const val KEY_STACK = "stack"
    }
}
