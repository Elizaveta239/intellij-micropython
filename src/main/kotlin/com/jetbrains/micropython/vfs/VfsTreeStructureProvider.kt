package com.jetbrains.micropython.vfs

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.InplaceCommentAppender
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.ui.SimpleTextAttributes

class VfsTreeStructureProvider : TreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings
    ): Collection<AbstractTreeNode<*>?> {
        if (parent !is ProjectViewProjectNode) return children

        return children.toMutableList().apply {
            add(MpyDeviceTreeNode(parent.project, settings))
        }
    }
}

open class MpyFileViewNode(project: Project, settings: ViewSettings, val nodeFile: VirtualFile) :
    ProjectViewNode<VirtualFile>(project, nodeFile, settings) {

    override fun getCacheableFile(): VirtualFile? = null
    override fun getCacheableFilePath(): String? = null
    override fun getCacheableAttributes(): Map<String, String>? = null

    override fun contains(file: VirtualFile): Boolean = VfsUtil.isAncestor(file, nodeFile, false)

    override fun update(presentation: PresentationData) {
        presentation.setIcon(nodeFile.fileType.icon)
        presentation.setSeparatorAbove(true)
        presentation.presentableText = nodeFile.name
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>?> {
        return nodeFile.children?.map { MpyFileViewNode(project, settings, it) } ?: emptyList()
    }
}

class MpyDeviceTreeNode(project: Project, settings: ViewSettings) :
    ProjectViewNode<FakeFs>(project, FakeFs, settings) {
    override fun appendInplaceComments(appender: InplaceCommentAppender) {
        appender.append(" Disconnected", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }

    override fun contains(file: VirtualFile): Boolean = false

    override fun update(presentation: PresentationData) {
        presentation.presentableText = "COMxx"
    }

    val children = listOf(
        PsiDirectoryNode(
            project,
            PsiDirectoryFactory.getInstance(project).createDirectory(FakeFs.folder),
            settings
        )
    )

    override fun getChildren(): Collection<AbstractTreeNode<*>?> = children

    override fun getCacheableFile(): VirtualFile? = null
    override fun getCacheableFilePath(): String? = null
    override fun getCacheableAttributes(): Map<String, String>? = null
}