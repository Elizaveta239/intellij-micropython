package com.jetbrains.micropython.nova.real

import com.jetbrains.micropython.nova.ConnectionParameters
import com.jetbrains.micropython.nova.WebSocketCommTest
import org.junit.jupiter.api.Disabled

private const val URL = "ws://192.168.50.68:8266"

private const val PASSWORD = "passwd"

@Disabled("Works only if a real board is at address ${URL} having password ${PASSWORD}")
class RealWebReplConnect: RealConnectTestBase() {

    override fun doInit() {
        Thread.sleep(200)
        comm = WebSocketCommTest()
        comm.setConnectionParams(ConnectionParameters(URL, PASSWORD))
        Thread.sleep(100)
    }

}