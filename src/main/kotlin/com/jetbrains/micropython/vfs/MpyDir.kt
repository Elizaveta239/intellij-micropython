package com.jetbrains.micropython.vfs

import com.intellij.openapi.vfs.VirtualFile
import java.io.InputStream
import java.io.OutputStream

class MpyDir(fileSystem: MpyFileSystem, parent: MpyDir?, name: String) : MpyFileNode(fileSystem,parent,name) {

    internal val files = arrayOf<MpyFileNode>()
    override fun isDirectory(): Boolean = true

    override fun getChildren(): Array<out VirtualFile> = files
    override fun getLength(): Long  = 0L
    override fun getInputStream(): InputStream {
        throw UnsupportedOperationException()
    }
    override fun contentsToByteArray(): ByteArray {
        throw UnsupportedOperationException()
    }

    override fun getOutputStream(
        requestor: Any?,
        newModificationStamp: Long,
        newTimeStamp: Long
    ): OutputStream {
        throw UnsupportedOperationException()
    }
}