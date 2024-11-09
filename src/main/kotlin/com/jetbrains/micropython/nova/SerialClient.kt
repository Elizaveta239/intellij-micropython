package com.jetbrains.micropython.nova

import com.intellij.openapi.diagnostic.thisLogger
import jssc.SerialPort
import jssc.SerialPort.FLOWCONTROL_NONE
import jssc.SerialPortEvent
import jssc.SerialPortEventListener
import jssc.SerialPortException
import java.io.IOException
import java.nio.charset.StandardCharsets

class SerialClient(private val comm: MpyComm) : Client {

    private val port = SerialPort(comm.connectionParameters.portName)
    override fun send(string: String) {
        this@SerialClient.thisLogger().debug("< $string")
        port.writeString(string)
    }

    override fun hasPendingData(): Boolean = port.inputBufferBytesCount > 0

    override fun close() = closeBlocking()

    private val listener = object : SerialPortEventListener {
        override fun serialEvent(event: SerialPortEvent) {
            if (event.eventType and SerialPort.MASK_RXCHAR != 0) {
                val count = event.eventValue
                val s = port.readBytes(count).toString(StandardCharsets.UTF_8)
                comm.dataReceived(s)
                this@SerialClient.thisLogger().debug("> $s")
            }
        }
    }

    @Throws(IOException::class)
    override suspend fun connect(progressIndicatorText: String):SerialClient {
        try {
            port.openPort()
            port.addEventListener(listener, SerialPort.MASK_RXCHAR)
            port.setParams(
                SerialPort.BAUDRATE_115200,
                SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE
            )
            port.flowControlMode = FLOWCONTROL_NONE
            comm.state = State.CONNECTED
            return this
        } catch (e: SerialPortException) {
            throw IOException("${e.port.portName}: ${e.exceptionType}")
        }
    }

    override fun closeBlocking() {
        port.closePort()
    }

    override fun sendPing() = Unit

    override val isConnected: Boolean
        get() = port.isOpened
}