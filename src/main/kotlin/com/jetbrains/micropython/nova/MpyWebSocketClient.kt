package com.jetbrains.micropython.nova

import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.util.progress.withProgressText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

private const val PASSWORD_PROMPT = "Password:"
private const val LOGIN_SUCCESS = "WebREPL connected"
private const val LOGIN_FAIL = "Access denied"

open class MpyWebSocketClient(private val comm: MpyComm) : Client {

    protected open fun open() = Unit
    protected open fun close(code: Int, reason: String, remote: Boolean) = Unit
    protected open fun error(ex: Exception) = Unit

    protected open fun message(message: String) = Unit

    private val loginBuffer = StringBuffer()
    @Volatile
    private var connectInProcess = true

    val webSocketClient = object : WebSocketClient(URI(comm.connectionParameters.url)) {
        override fun onOpen(handshakedata: ServerHandshake) = open() //Nothing to do

        override fun onMessage(message: String) {
            this@MpyWebSocketClient.message(message)
            if(connectInProcess) {
                loginBuffer.append(message)
            } else {
                comm.dataReceived(message)
            }
        }

        override fun onMessage(bytes: ByteBuffer) = onMessage(String(bytes.array(), StandardCharsets.UTF_8))

        override fun onError(ex: Exception) {
            error(ex)
            comm.errorLogger(ex)
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            close(code, reason, remote)
            try {
                if (remote && comm.state == State.CONNECTED) {
                    //Counterparty closed the connection
                    throw IOException("Connection closed. Code:$code ($reason)")
                }
            } finally {
                connectInProcess = false
                comm.state = State.DISCONNECTED
            }
        }

    }
    init {
        webSocketClient.isTcpNoDelay = true
        webSocketClient.connectionLostTimeout = 0
    }

    override suspend fun connect(progressIndicatorText: String): MpyWebSocketClient {
        loginBuffer.setLength(0)
        connectInProcess = true
        webSocketClient.connect()
        try {
            var time = LONG_TIMEOUT
            withProgressText(progressIndicatorText) {
                while (!isConnected && time > 0) {
                    @Suppress("UnstableApiUsage")
                    checkCanceled()
                    delay(SHORT_DELAY)
                    time -= SHORT_DELAY.toInt()
                }
                if (!isConnected) {
                    throw ConnectException("Webrepl connection failed")
                }
            }
            withTimeout(TIMEOUT) {
                while (isConnected) {
                    when {
                        loginBuffer.length < PASSWORD_PROMPT.length -> delay(SHORT_DELAY)
                        loginBuffer.length > PASSWORD_PROMPT.length * 2 -> {
                            loginBuffer.setLength(PASSWORD_PROMPT.length * 2)
                            throw ConnectException("Password exchange error. Received prompt: $loginBuffer")
                        }

                        loginBuffer.toString().contains(PASSWORD_PROMPT) -> break
                        else -> throw ConnectException("Password exchange error. Received prompt: $loginBuffer")
                    }
                }
                loginBuffer.setLength(0)
                send("${comm.connectionParameters.password}\n")
                while (connectInProcess && isConnected) {
                    when {
                        loginBuffer.contains(LOGIN_SUCCESS) -> break
                        loginBuffer.contains(LOGIN_FAIL) -> throw ConnectException("Access denied")
                        else -> delay(SHORT_DELAY)
                    }
                }
                connectInProcess = false
                comm.state = State.CONNECTED
            }
        } catch (e: Exception) {
            try {
                close()
            } catch (_: IOException) {
            }
            connectInProcess = false
            comm.state = State.DISCONNECTED
            when (e) {
                is TimeoutCancellationException -> throw ConnectException("Password exchange timeout. Received prompt: $loginBuffer")
                is InterruptedException -> throw ConnectException("Connection interrupted")
                else -> throw e
            }

        } finally {
            connectInProcess = false
            loginBuffer.setLength(0)
            loginBuffer.trimToSize()
        }
        return this
    }

    override fun close() = webSocketClient.close()

    override fun closeBlocking() = webSocketClient.closeBlocking()

    override fun send(string: String) = webSocketClient.send(string)

    override fun sendPing() = webSocketClient.sendPing()

    override fun hasPendingData(): Boolean = webSocketClient.hasBufferedData()

    override val isConnected: Boolean
        get() = webSocketClient.isOpen
}