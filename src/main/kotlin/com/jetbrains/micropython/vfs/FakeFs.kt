package com.jetbrains.micropython.vfs

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.annotations.NonNls
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

object FakeFs : DeprecatedVirtualFileSystem() {
    override fun getProtocol(): @NonNls String = "fake"

    override fun refresh(asynchronous: Boolean) = Unit
    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)


    val folder: VirtualFile = object : VirtualFile() {
        override fun getName(): @NlsSafe String = "fld"

        override fun getFileSystem(): VirtualFileSystem = this@FakeFs

        override fun getPath(): @NonNls String = getName()

        override fun isWritable(): Boolean = false

        override fun isDirectory(): Boolean = true

        override fun isValid(): Boolean = true

        override fun getParent(): VirtualFile? = null

        override fun getChildren(): Array<out VirtualFile>? = arrayOf<VirtualFile>(txtFile)

        override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
            throw UnsupportedOperationException()
        }

        override fun contentsToByteArray(): ByteArray {
            throw UnsupportedOperationException()
        }

        override fun getTimeStamp(): Long = 0L

        override fun getLength(): Long = 0L

        override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
            postRunnable?.run()
        }

        override fun getInputStream(): InputStream {
            throw UnsupportedOperationException()
        }

    }

    val txtFile: VirtualFile = object : VirtualFile() {
        val content = "Hello, world!".toByteArray()
        override fun getName(): @NlsSafe String = "file.txt"
        override fun getFileSystem(): VirtualFileSystem = this@FakeFs
        override fun getPath(): @NonNls String = "${folder.name}/$name"
        override fun isWritable(): Boolean = false
        override fun isDirectory(): Boolean = false
        override fun isValid(): Boolean = true
        override fun getParent(): VirtualFile = folder
        override fun getChildren(): Array<out VirtualFile>? = null
        override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
            throw UnsupportedOperationException()
        }

        override fun getTimeStamp(): Long = -1L
        override fun contentsToByteArray(): ByteArray = content.copyOf()
        override fun getLength(): Long = content.size.toLong()
        override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
            postRunnable?.run()
        }

        override fun getInputStream(): InputStream = ByteArrayInputStream(content)
    }

    override fun findFileByPath(path: @NonNls String): VirtualFile? {
        return when (path) {
            folder.path -> folder
            txtFile.path -> txtFile
            else -> null
        }
    }

}