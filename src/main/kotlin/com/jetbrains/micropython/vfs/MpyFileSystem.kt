package com.jetbrains.micropython.vfs

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.asSafely
import org.jetbrains.annotations.NonNls

class MpyFileSystem : DeprecatedVirtualFileSystem() {
    override fun getProtocol(): @NonNls String = "mpy"
    private val root: MpyDir = MpyDir(this, null, "")

    override fun findFileByPath(path: @NonNls String): VirtualFile? {
        var foundFile: MpyFileNode? = root
        for (pathPart in  path.split('/')) {
            foundFile = foundFile?.children?.firstOrNull { file -> pathPart == file.name }?.asSafely<MpyFileNode>()
            if (foundFile == null) {
                break
            }
        }
        return foundFile
    }

    override fun refresh(asynchronous: Boolean) {
        TODO("Not yet implemented")
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? {
        refresh(false)
        return findFileByPath(path)
    }


    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        TODO("Not yet implemented")
    }

    override fun moveFile(
        requestor: Any?,
        vFile: VirtualFile,
        newParent: VirtualFile
    ) {
        TODO("Not yet implemented")
    }

    override fun renameFile(
        requestor: Any?,
        vFile: VirtualFile,
        newName: String
    ) {
        TODO("Not yet implemented")
    }

    override fun createChildFile(
        requestor: Any?,
        vDir: VirtualFile,
        fileName: String
    ): VirtualFile {
        TODO("Not yet implemented")
    }

    override fun createChildDirectory(
        requestor: Any?,
        vDir: VirtualFile,
        dirName: String
    ): VirtualFile {
        TODO("Not yet implemented")
    }

    override fun copyFile(
        requestor: Any?,
        virtualFile: VirtualFile,
        newParent: VirtualFile,
        copyName: String
    ): VirtualFile {
        TODO("Not yet implemented")
    }

    override fun isReadOnly(): Boolean = isConnected()

    fun isConnected(): Boolean {
        TODO("Not yet implemented")
    }

}