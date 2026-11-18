package io.github.jercoates.irrigationtroubleshooter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test fun parsesReadyAndAck() {
        assertEquals(BridgeMessage.Ready, Protocol.parse("""{"type":"ready"}"""))
        assertEquals(BridgeMessage.Ack, Protocol.parse("""{"type":"ack"}"""))
    }

    @Test fun parsesState() {
        val m = Protocol.parse("""{"type":"state","node":"b","crumb":"Zone","stack":["start","a","b"],"isEnd":true}""")
        assertEquals(BridgeMessage.State("b", "Zone", listOf("start", "a", "b"), true, false), m)
        val back = Protocol.parse("""{"type":"state","node":"a","stack":["start","a"],"canBack":true}""")
        assertEquals(true, (back as BridgeMessage.State).canBack)
    }

    @Test fun rejectsMalformedOrHostileMessages() {
        assertNull(Protocol.parse(null))
        assertNull(Protocol.parse("not json"))
        assertNull(Protocol.parse("""{"type":"evil"}"""))
        assertNull(Protocol.parse("""{"type":"state","node":"b","stack":["start","a"]}""")) // node != top of stack
        assertNull(Protocol.parse("""{"type":"state","node":"a","stack":["x","a"]}"""))      // does not start at "start"
        // injection attempt in a position that is consistent (node == top of stack): rejected by the id check itself
        assertNull(Protocol.parse("""{"type":"state","node":"a\"});alert(1);//","stack":["start","a\"});alert(1);//"]}"""))
        assertNull(Protocol.parse("""{"type":"state","node":"a","stack":["start","a b"]}"""))
        assertNull(Protocol.parse("""{"type":"ready","pad":"${"x".repeat(Protocol.MAX_MESSAGE_CHARS)}"}"""))
    }

    @Test fun rejectsOversizedStack() {
        val big = List(Protocol.MAX_STACK + 1) { "start" }
        assertNull(Protocol.sanitizeStack(big))
        assertNull(Protocol.sanitizeStack(emptyList()))
        assertNull(Protocol.sanitizeStack(null))
    }

    @Test fun truncatesLongCrumb() {
        val m = Protocol.parse("""{"type":"state","node":"start","crumb":"${"c".repeat(500)}","stack":["start"]}""")
        assertEquals(120, (m as BridgeMessage.State).crumb.length)
    }

    @Test fun stackToJsAlwaysProducesSafeLiteral() {
        assertEquals("""["start","a_1"]""", Protocol.stackToJs(listOf("start", "a_1")))
        // an invalid stack (e.g. tampered prefs) falls back to the default
        assertEquals("""["start"]""", Protocol.stackToJs(listOf("start", "x');alert(1);//")))
    }

    @Test fun parseStackHandlesCorruptStorage() {
        assertNull(Protocol.parseStack(null))
        assertNull(Protocol.parseStack("{"))
        assertNull(Protocol.parseStack("[1,2]"))
        assertEquals(listOf("start", "z"), Protocol.parseStack("""["start","z"]"""))
    }

    @Test fun decodesEvaluateJavascriptStrings() {
        assertEquals("a\nb → c", Protocol.decodeJsString("\"a\\nb \\u2192 c\""))
        assertNull(Protocol.decodeJsString("null"))
        assertNull(Protocol.decodeJsString(null))
        assertNull(Protocol.decodeJsString("\"\""))
    }

    @Test fun idValidation() {
        assertTrue(Protocol.isValidId("vnc_pre"))
        assertFalse(Protocol.isValidId(""))
        assertFalse(Protocol.isValidId("a-b"))
        assertFalse(Protocol.isValidId("a".repeat(65)))
    }
}
