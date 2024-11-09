package com.jetbrains.micropython.nova

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(PROJECT)
class SerialPortListService(private val cs: CoroutineScope) {
    fun listPorts(receiver: suspend (Array<String>) -> Unit) {
        cs.launch {
            val portNames = jssc.SerialPortList.getPortNames() ?: emptyArray<String>()
            receiver(portNames)
        }
    }
}