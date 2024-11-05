package com.jetbrains.micropython.nova

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.net.ConnectException
import java.util.concurrent.TimeUnit.MILLISECONDS

class TestConnect {

    private var tcpPort = 58756

    private lateinit var server: WebSocketTestServer

    @BeforeEach
    fun init() {
        Thread.sleep(100)
        server = WebSocketTestServer(tcpPort)
        Thread.sleep(100)
    }

    @AfterEach
    fun teardown() {
        server.close()
    }

    private fun expect(vararg sentences: Pair<String, String?>) = server.expect(*sentences)
    private fun test(block: suspend CoroutineScope.(MpyCommForTest) -> Unit) = server.test(block)

    @Test
    @Timeout(5000, unit = MILLISECONDS)
    fun successfulConnect() {
        expect(
            "Password: " to "pa55wd\n",
            "WebREPL connected" to null,
        )
        test { comm ->
            comm.setConnectionParams(ConnectionParameters("ws://localhost:$tcpPort", "pa55wd"))
            comm.connect()
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
        }
    }

    @Test
    @Timeout(5000, unit = MILLISECONDS)
    fun accessDenied() {
        expect(
            "Password: " to "pa55wd\n",
            "Access denied" to null,
        )
        val ex = assertThrows<ConnectException> {
            test { comm ->
                comm.setConnectionParams(ConnectionParameters("ws://localhost:$tcpPort", "pa55wd"))
                comm.connect()
            }
        }
        assertEquals("Access denied", ex.message)
    }

    @Test
    @Timeout(5000, unit = MILLISECONDS)
    fun blindExecute() {
        expect(
            "Password: " to "passwd\n",
            "WebREPL connected" to "\u0003",
            ">>>" to "\u0003",
            ">>>" to "\u0003",
            ">>>" to "\u0001",
            "raw REPL; CTRL-B to exit\n>" to "print('Test me')\n",
            "" to "\u0004",
            "OKTest me\n\u0004\u0004>" to "\u0002",
            "\nMicroPython\nType \"help()\" for more information.\n>>>" to "\u0003",
            ">>>" to "\u0003",
            ">>>" to "\u0003",
            ">>>" to "\u0001",
            "raw REPL; CTRL-B to exit\n>" to "print('Test me 2')\n",
            "" to "\u0004",
            "OKTest me 2\n\u0004\u0004>" to "\u0002",
            "" to null,
        )
        test { comm ->
            comm.setConnectionParams(ConnectionParameters("ws://localhost:$tcpPort", "passwd"))
            comm.connect()
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            val responseA = comm.blindExecute(LONG_TIMEOUT, "print('Test me')")
            assertEquals("Test me", responseA.extractSingleResponse())
            val responseB = comm.blindExecute(LONG_TIMEOUT, "print('Test me 2')")
            assertEquals("Test me 2", responseB.extractSingleResponse())
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            delay(300)
        }
    }


    @Test
    @Timeout(5000, unit = MILLISECONDS)
    fun instantRun() {
        expect(
            "Password: " to "passwd\n",
            "WebREPL connected" to "\u0003",
            ">>>" to "\u0003",
            ">>>" to "\u0003",
            ">>>" to "\u0005",
            "paste mode; Ctrl-C to cancel, Ctrl-D to finish\n===" to "print('Test me')\n",
            "" to "print('Test me 2')\n",
            "" to "#*********FSOP************\n",
            "#*********FSOP************\n" to "\u0004",
            "Test me\nTest me 2\n>>>" to null
        )
        test { comm ->
            comm.setConnectionParams(ConnectionParameters("ws://localhost:$tcpPort", "passwd"))
            comm.connect()
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            comm.instantRun("print('Test me')\nprint('Test me 2')")
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            assertTrue(comm.ttyConnector.isConnected)
            val buf = CharArray(100)
            delay(500)
            val len = comm.ttyConnector.read(buf, 0, buf.size)
            val linesReceived = String(buf, 0, len).trim().lines()
            assertIterableEquals(
                listOf("Test me", "Test me 2", ">>>"),
                linesReceived
            )
        }
    }

    @Test
    @Timeout(5000, unit = MILLISECONDS)
    fun blindExecuteMultiple() {
        expect(
            "Password: " to "passwd\n",
            "WebREPL connected" to "\u0003",
            ">>>" to "\u0003",
            ">>>" to "\u0003",
            ">>>" to "\u0001",
            "raw REPL; CTRL-B to exit\n>" to "print('Test me 0')\n",
            "" to "\u0004",
            "OKTest me 0\n\u0004\u0004>" to "print('Test me 1')\n",
            "" to "\u0004",
            "OKTest me 1\n\u0004\u0004>" to "\u0002",
            "" to null,
        )
        test { comm ->
            comm.setConnectionParams(ConnectionParameters("ws://localhost:$tcpPort", "passwd"))
            comm.connect()
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            val response = comm.blindExecute(LONG_TIMEOUT, "print('Test me 0')", "print('Test me 1')")
            val expected = listOf(
                SingleExecResponse("Test me 0", ""),
                SingleExecResponse("Test me 1", ""),
            )
            assertIterableEquals(expected, response)
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            delay(300)
        }

    }

}