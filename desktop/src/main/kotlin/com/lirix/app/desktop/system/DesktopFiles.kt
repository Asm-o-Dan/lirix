package com.lirix.app.desktop.system

import java.awt.Component
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

object DesktopFiles {
    fun choose(title: String, extension: String, save: Boolean, suggested: String = "lirix.$extension"): File? {
        var selected: File? = null
        val action = Runnable {
            val chooser = JFileChooser().apply {
                dialogTitle = title; fileFilter = FileNameExtensionFilter(extension.uppercase(), extension)
                if (save) selectedFile = File(suggested)
            }
            val result = if (save) chooser.showSaveDialog(null as Component?) else chooser.showOpenDialog(null as Component?)
            if (result == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile.let { if (save && !it.name.endsWith(".$extension", true)) File(it.parentFile, it.name + ".$extension") else it }
                if (!save || !file.exists() || JOptionPane.showConfirmDialog(null, "Заменить ${file.name}?", title,
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) selected = file
            }
        }
        if (SwingUtilities.isEventDispatchThread()) action.run() else SwingUtilities.invokeAndWait(action)
        return selected
    }
}
