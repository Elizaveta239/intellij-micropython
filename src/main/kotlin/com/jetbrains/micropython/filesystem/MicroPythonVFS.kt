package com.jetbrains.micropython.filesystem

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFile.PROP_NAME
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.application
import com.intellij.util.asSafely
import java.io.*

//VFileContentChangeEvent (com.intellij.openapi.vfs.newvfs.events)

class MicroPythonVFS(val project: Project, private val pythonPath: String, private val portName: String) : NewVirtualFileSystem() {

  val root: MicroPythonDirectory = MicroPythonDirectory("", null, 0)
  val publisher = lazy { application.messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES) }

  private fun runMPRemote(vararg commands: String): String {
    val cmd = GeneralCommandLine()
      .withExePath(pythonPath)
      .withParameters("-m", "mpremote", "connect", portName)
      .withParameters(*commands)
    val output = CapturingProcessHandler(cmd).runProcess(100000)
    if (output.isTimeout || output.exitCode != 0) {
      val message = output.stderr.ifBlank { output.stdout }
      throw IOException("Python error: $message")
    }
    return output.stdout
  }

  private fun readFs() {
    val dirList = runMPRemote("run", "C:\\Users\\elmot\\PycharmProjects\\pythonProject3\\dir-r.py")
    dirList.lines()
      .filter { it.isNotBlank() }
      .forEach { line ->
        val fields = line.split(',')
        val pathComponents = fields[0].split('/')
        var filePtr: MicroPythonDirectory? = root
        for (s in pathComponents.dropLast(1)) {
          val oldParent = filePtr!!
          filePtr = oldParent.findChild(s) as MicroPythonDirectory?
          if (filePtr == null) {
            filePtr = MicroPythonDirectory(s, oldParent, 0/*todo*/)
            oldParent.files.add(filePtr)
          }
        }
        if (fields[1] == "D") {
          filePtr!!.files.add(MicroPythonDirectory(pathComponents.last(), filePtr, 0/*todo*/))
        }
        else {
          filePtr!!.files.add(MicroPythonFile(pathComponents.last(), filePtr, 0/*todo*/, fields[2].toLong()))
        }
      }
  }

  companion object {
    fun readFromBoard(project: Project, pythonPath: String, portName: String): MicroPythonVFS {
      return MicroPythonVFS(project, pythonPath, portName).apply { readFs() }
    }
  }

  override fun exists(file: VirtualFile): Boolean = file.exists()

  override fun getProtocol(): String = "mpfs"


  override fun findFileByPath(path: String): VirtualFile? {
    var pathSplit = path.split('/')
    if (pathSplit.getOrNull(0)?.isNotEmpty() == true) {
      pathSplit = pathSplit.subList(1, pathSplit.size)
    }
    var filePtr = root
    for (pathElement in pathSplit) {
      filePtr = filePtr.files.firstOrNull { it.name == pathElement }.asSafely<MicroPythonDirectory>() ?: return null
    }
    return filePtr
  }

  override fun refresh(asynchronous: Boolean) {
    //TODO("Not yet implemented")
  }

  override fun refreshAndFindFileByPath(path: String): VirtualFile? {
    refresh(false)
    return findFileByPath(path)
  }

  override fun getAttributes(file: VirtualFile): FileAttributes? {
    return if (file.exists()) (file as MicroPythonFileBase).attributes else null
  }

  override fun deleteFile(requestor: Any?, file: VirtualFile) {
    if (file.fileSystem !is MicroPythonVFS) throw IOException()
    val events = listOf(VFileDeleteEvent(requestor, file))
    publisher.value.before(events)
    TODO("Not yet implemented")
    publisher.value.after(events)
  }

  override fun moveFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile) {
    val events = listOf(VFileMoveEvent(requestor, file, newParent))
    publisher.value.before(events)
    TODO("Not yet implemented")
    publisher.value.after(events)
  }

  override fun renameFile(requestor: Any?, file: VirtualFile, newName: String) {
    val events = listOf(VFilePropertyChangeEvent(requestor, file, PROP_NAME, file.name, newName))
    publisher.value.before(events)
    TODO("Not yet implemented")
    publisher.value.after(events)
  }

  override fun createChildFile(requestor: Any?, parent: VirtualFile, name: String): VirtualFile {
    if (parent.fileSystem !is MicroPythonVFS) throw IOException()
    val events = listOf(VFileCreateEvent(requestor, parent, name, false, null, null, null))
    publisher.value.before(events)
    val directory = parent.asSafely<MicroPythonDirectory>() ?: throw IOException("Not a directory")
    runMPRemote("fs", "touch", directory.path + "/" + name)
    val virtualFile = directory.fileSystem.MicroPythonFile(name = name, directory, System.currentTimeMillis(), 0)
    directory.files.add(virtualFile)
    publisher.value.after(events)
    return virtualFile
  }

  override fun createChildDirectory(requestor: Any?, parent: VirtualFile, name: String): VirtualFile {
    if (parent.fileSystem !is MicroPythonVFS) throw IOException()
    val directory = parent.asSafely<MicroPythonDirectory>() ?: throw IOException("Not a directory")
    val events = listOf(VFileCreateEvent(requestor, parent, name, true, null, null, null))
    publisher.value.before(events)
    runMPRemote("fs", "mkdir", directory.path + "/" + name)
    val virtualFile = directory.fileSystem.MicroPythonDirectory(name = name, directory, System.currentTimeMillis())
    directory.files.add(virtualFile)
    publisher.value.after(events)
    return virtualFile
  }

  override fun copyFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
    if (newParent.fileSystem !is MicroPythonVFS) throw IOException()
    val events = listOf(VFileCopyEvent(requestor, file, newParent, copyName))
    publisher.value.before(events)
    TODO("Not yet implemented")
    publisher.value.after(events)
  }

  override fun list(file: VirtualFile): Array<String> =
    file.children.filter { it.exists() }.map { it.name }.toTypedArray()


  override fun isDirectory(file: VirtualFile): Boolean = file.isDirectory

  override fun getTimeStamp(file: VirtualFile): Long = file.timeStamp

  override fun setTimeStamp(file: VirtualFile, timeStamp: Long) {
    (file as MicroPythonFileBase).myTimeStamp = timeStamp
    //todo
  }

  override fun isWritable(file: VirtualFile): Boolean = true

  override fun setWritable(file: VirtualFile, writableFlag: Boolean) {
  }

  override fun contentsToByteArray(file: VirtualFile): ByteArray {
    return file.asSafely<MicroPythonFileBase>()?.contentsToByteArray()
           ?: throw IOException("Cannot read content of ${file.path}")
  }

  override fun getInputStream(file: VirtualFile): InputStream {
    return file.asSafely<MicroPythonFileBase>()?.inputStream
           ?: throw IOException("Cannot read content of ${file.path}")
  }

  override fun getOutputStream(file: VirtualFile, requestor: Any?, modStamp: Long, timeStamp: Long): OutputStream {

    return file.asSafely<MicroPythonFileBase>()?.getOutputStream(requestor, modStamp, timeStamp)
           ?: throw IOException("Cannot write content of ${file.path}")
  }

  override fun getLength(file: VirtualFile): Long {
    return if (file.exists()) (file as MicroPythonFileBase).length else throw IOException("Cannot stat ${file.path}")
  }

  override fun extractRootPath(normalizedPath: String): String = ""

  override fun findFileByPathIfCached(path: String): VirtualFile? {
    //TODO("Not yet implemented")???
    return findFileByPath(path)
  }

  override fun getRank(): Int = 1

  abstract inner class MicroPythonFileBase(
    private var myName: String,
    protected val parent: MicroPythonDirectory?,
    var myTimeStamp: Long
  ) : VirtualFile() {

    override fun getModificationStamp(): Long = timeStamp

    protected var exists: Boolean = true

    override fun getFileSystem(): MicroPythonVFS = this@MicroPythonVFS

    override fun getParent(): VirtualFile? = parent

    override fun getCanonicalFile(): VirtualFile = this

    override fun setWritable(writable: Boolean) {
    }

    override fun getName(): String = myName

    override fun getPath(): String {
      return if (parent != null) "${parent.path}/${myName}"
      else myName
    }

    override fun getTimeStamp(): Long = myTimeStamp
    override fun isValid(): Boolean = exists

    abstract val attributes: FileAttributes
  }

  inner class MicroPythonDirectory(name: String, parent: MicroPythonDirectory?, timeStamp: Long) :
    MicroPythonFileBase(name, parent, timeStamp) {
    internal val files: MutableList<MicroPythonFileBase> = mutableListOf()
    override fun findChild(name: String): VirtualFile? = files.firstOrNull { it.getName() == name }
    override fun contentsToByteArray(): ByteArray = ByteArray(0)

    override fun isDirectory(): Boolean = true
    override fun isWritable(): Boolean = true

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream =
      throw IOException("Not supported")

    override fun getInputStream(): InputStream = throw IOException("Not supported")

    override fun getChildren(): Array<VirtualFile> = files.toTypedArray()

    override fun getLength(): Long = 0

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
      //todo?
      postRunnable?.run()
    }

    override val attributes: FileAttributes
      get() = FileAttributes(true, false, false, false, 0, timeStamp, isWritable)
  }

  inner class MicroPythonFile(name: String, parent: MicroPythonDirectory?, timeStamp: Long, private var initialLength: Long) :
    MicroPythonFileBase(name, parent, timeStamp) {
    private var bytes: MicroPythonByteStream? = null

    override fun findChild(name: String): NewVirtualFile? = null

    override fun isDirectory(): Boolean = false
    override fun getChildren(): Array<VirtualFile> = emptyArray()
    override fun getLength(): Long = bytes?.size()?.toLong() ?: initialLength
    override fun isWritable(): Boolean = false
    private fun loadFromDevice(forced: Boolean): MicroPythonByteStream {
      if (!exists) throw IOException("Does not exists")
      if (!forced && bytes != null) return bytes!!
      val content = runMPRemote("cat", path)
      bytes = MicroPythonByteStream().apply {
        write(content.toByteArray())
      }
      return bytes!!
    }

    fun saveToDevice() {
      TODO()
    }

    override fun contentsToByteArray(): ByteArray {
      loadFromDevice(false)
      return bytes!!.toByteArray()
    }

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
      if (!exists()) throw IOException("Does not exists")
      val events = listOf(VFileContentChangeEvent(requestor, this, timeStamp, newModificationStamp))
      publisher.value.before(events)
      try {
        bytes = MicroPythonByteStream()
        myTimeStamp = newTimeStamp
      }
      finally {
        publisher.value.after(events)
      }
      return bytes!!
    }

    inner class MicroPythonByteStream : ByteArrayOutputStream() {
      override fun close() {
        saveToDevice()
      }
    }

    override fun getInputStream(): InputStream {
      val result = loadFromDevice(false)
      return ByteArrayInputStream(result.toByteArray())
    }

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
      if (asynchronous) {
        application.executeOnPooledThread {
          refresh(false, recursive, postRunnable)
        }
      }
      else {
        readFs()
        postRunnable?.run()
      }

    }

    override val attributes: FileAttributes
      get() = FileAttributes(false, false, false, false, getLength(), timeStamp, isWritable)
  }

}
