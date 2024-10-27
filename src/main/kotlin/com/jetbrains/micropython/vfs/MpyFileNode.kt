package com.jetbrains.micropython.vfs

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.annotations.NonNls

abstract class MpyFileNode(
    protected val fileSystem: MpyFileSystem,
    protected val parent: MpyDir?,
    private val name: String
) : VirtualFile() {
    override fun getParent(): VirtualFile? = parent
    override fun getName(): @NlsSafe String = name
    override fun getFileSystem(): VirtualFileSystem = fileSystem
    override fun isWritable(): Boolean = true
    override fun isValid(): Boolean = parent?.isValid ?: fileSystem.isConnected()
    override fun getTimeStamp(): Long = 0L

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        TODO("Not yet implemented")
    }

    override fun getPath(): @NonNls String {
        return parent?.path?.let { "$it/$name" } ?: name
    }
}