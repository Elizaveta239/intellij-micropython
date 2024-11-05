package com.jetbrains.micropython.nova

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.stream.IntStream.range

private const val URL = "ws://192.168.50.68:8266"

private const val PASSWORD = "passwd"

@Disabled("Works only id a real board is at address $URL having password $PASSWORD")
class RealConnect {

    private lateinit var comm: MpyCommForTest

    @BeforeEach
    fun init() {
        Thread.sleep(200)
        comm = WebSocketCommTest { fail(it) }
        Thread.sleep(100)
    }

    @AfterEach
    fun teardown() {
        comm.close()
    }

    @Test
    @Timeout(5000, unit = MILLISECONDS)
    fun realBlindExecute() {
        runBlocking {
            comm.setConnectionParams(ConnectionParameters(URL, PASSWORD))
            comm.connect()
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            val responseA = comm.blindExecute(LONG_TIMEOUT, "print('Test me')")
            assertEquals("Test me", responseA.extractSingleResponse())
            val responseB = comm.blindExecute(LONG_TIMEOUT, "print('Test me 2')")
            assertEquals("Test me 2", responseB.extractSingleResponse())
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
        }
    }

    @Test
    @Timeout(5000, unit = MILLISECONDS)
    fun realBlindExecuteLong() {
        val repeatCount = 60
        runBlocking {
            comm.setConnectionParams(ConnectionParameters(URL, PASSWORD))
            comm.connect()
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            val response = comm.blindExecute(LONG_TIMEOUT,
                "print('Test me ', end='')\n".repeat(repeatCount)
            )
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            assertSingleOkResponse("Test me ".repeat(60).trim(), response)
        }
    }

    private fun assertSingleOkResponse(expectedResponse: String, response: ExecResponse) {
        assertEquals(1, response.size)
        assertTrue(response[0].stderr.isEmpty())
        assertEquals(expectedResponse, response[0].stdout)
    }

    @Test
    @Timeout(5000, unit = MILLISECONDS)
    fun realBlindExecuteWrong() {
        runBlocking {
            comm.setConnectionParams(ConnectionParameters(URL, PASSWORD))
            comm.connect()
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            val response = comm.blindExecute(LONG_TIMEOUT,
                "print('Test me ', end=''\n"
            )
            assertEquals(1, response.size)
            assertTrue(response[0].stdout.isEmpty())
            assertTrue(response[0].stderr.isNotBlank())
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
        }
    }

    @Test
    @Timeout(50000, unit = MILLISECONDS)
    fun realBlindExecuteMultiple() {
        val repeatCount = 50
        runBlocking {
            comm.setConnectionParams(ConnectionParameters(URL, PASSWORD))
            comm.connect()
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            val commands = range(0, repeatCount).mapToObj { "print('Test me $it')" }.toList().toTypedArray()
            val response = comm.blindExecute(LONG_TIMEOUT, *commands)
            assertEquals(repeatCount, response.size)
            for (i in 0 until repeatCount) {
                assertEquals(response[i].stdout, "Test me $i")
                assertTrue(response[i].stderr.isEmpty())
            }
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
        }
    }

    @Test
    @Timeout(5000, unit = MILLISECONDS)
    fun realInstantRun() {
        runBlocking {
            comm.setConnectionParams(ConnectionParameters(URL, PASSWORD))
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

    //    @Test
    @Suppress("unused")
    @Disabled
    fun longRunConnection() {
        runBlocking {
            comm.setConnectionParams(ConnectionParameters(URL, PASSWORD))
            comm.connect()
            assertEquals(State.CONNECTED, comm.state )
            assertFalse(comm.isTtySuspended())
            println("Connected")
            launch {
                while (State.CONNECTED == comm.state) {
                    delay(20000)
                    println("ping")
                    comm.ping()
                }
            }
            val start = System.currentTimeMillis()
            while (State.CONNECTED == comm.state) {
                delay(1000)
                println("Still alive")
            }
            println("Disconnect time: ${System.currentTimeMillis() - start}ms")

        }
    }

}