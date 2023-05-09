package kuruvila.analysis.externalapps

import java.awt.Desktop
import java.io.File

object FileOpener: ExternalApp{
    override fun canRun(): Boolean {
        return Desktop.isDesktopSupported()
    }

    fun openFile(file: File) {
        Desktop.getDesktop().open(file)
    }
}