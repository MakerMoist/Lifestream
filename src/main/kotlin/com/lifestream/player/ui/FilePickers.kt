package com.lifestream.player.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Windows' own Save and Open dialogs. */
object FilePickers {
    /** Where the last file was saved or opened, so the next dialog starts there. */
    private var lastDir: String? = null

    /** Asks where to save [suggestedName]; the chosen file always ends in [extension]. */
    fun save(title: String, suggestedName: String, extension: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = suggestedName
        lastDir?.let { dialog.directory = it }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        lastDir = dir
        val withExtension = if (name.endsWith(extension, ignoreCase = true)) name else name + extension
        return File(dir, withExtension)
    }

    /** Asks for a file ending in [extension] to open. */
    fun open(title: String, extension: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.file = "*$extension" // the filter Windows' dialog shows
        dialog.setFilenameFilter { _, name -> name.endsWith(extension, ignoreCase = true) }
        lastDir?.let { dialog.directory = it }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        lastDir = dir
        return File(dir, name)
    }
}
