package com.jetbrains.micropython.nova

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.Closeable
import java.net.InetSocketAddress

private fun logMessage(direction: String, s: String): String {
    val text = s.map { if (it in ' '..'\u007f') it else String.format("\\x%02X", it.code) }.joinToString("")
    println("$direction $text")
    return s
}
class WebSocketTestServer(tcpPort: Int) :
    WebSocketServer(InetSocketAddress("localhost", tcpPort)), Closeable {

    private var idx = 0
    private val exceptions = mutableListOf<Throwable>()

    private var sentences: Array<out Pair<String, String?>> = emptyArray()

    fun expect(vararg sentences: Pair<String, String?>) {
        this.sentences = sentences
    }
    override fun onError(conn: WebSocket?, ex: Exception) {
        exceptions.add(ex)
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        conn.send(sentences[idx].first)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        try {
            assertEquals(sentences[idx].second, message)
            idx++
            conn.send(sentences[idx].first)
        } catch (e: Throwable) {
            exceptions.add(e)
        }
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        try {
            if (sentences.last().second == null) {
                assertEquals(sentences.size - 1, idx, "Sentenced processed")
            } else {
                assertEquals(sentences.size, idx, "Sentenced processed")
            }
        } catch (e: Throwable) {
            exceptions.add(e)
        }
    }

    @Volatile
    var started = false

    override fun onStart(){
        started = true
    }
    override fun close() {
        stop()
        exceptions.forEach { it.printStackTrace() }
        if (exceptions.isNotEmpty()) {
            throw exceptions.first()
        }
    }

    fun test(block: suspend CoroutineScope.(MpyCommForTest) -> Unit) {
        start()
        while (!started) {
            Thread.sleep(100)
        }
        use {
            WebSocketCommTest { exceptions.add(it) }
                .use {
                    runBlocking { block(it) }
                }
        }
    }

}


class WebSocketCommTest(errorLogger: (Throwable) -> Any = {}) : MpyCommForTest(errorLogger) {
    inner class MpyWebSocketClientTest() : MpyWebSocketClient(this) {
        override fun error(ex: Exception) {
            println("== ON ERROR ==")
        }

        override fun message(message: String) {
            logMessage(">>", message)
        }

        override fun close(code: Int, reason: String, remote: Boolean) {
            println("== ON CLOSE ==")
        }

        override fun send(text: String) {
            logMessage("<<", text)
            super.send(text)
        }

        override fun open() {
            println("== ON OPEN ==")
        }
    }

    override fun createClient(): MpyWebSocketClient = MpyWebSocketClientTest()

}