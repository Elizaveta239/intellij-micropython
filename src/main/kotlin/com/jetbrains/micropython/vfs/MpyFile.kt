package com.jetbrains.micropython.vfs

import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.io.output.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

class MpyFile(fileSystem: MpyFileSystem, parent: MpyDir?, name: String) : MpyFileNode(fileSystem, parent, name) {
    override fun isDirectory(): Boolean = false
    override fun getChildren(): Array<out VirtualFile?>? = null

    internal val content = ByteArrayOutputStream()

    override fun contentsToByteArray(): ByteArray = content.toByteArray()
    override fun getLength(): Long =content.size().toLong()
    override fun getOutputStream(
        requestor: Any?,
        newModificationStamp: Long,
        newTimeStamp: Long
    ): OutputStream {
        content.reset()
        return content
    }

    override fun getInputStream(): InputStream = ByteArrayInputStream(content.toByteArray())
}