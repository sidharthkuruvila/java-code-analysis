package kuruvila.analysis.externalapps

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.exists

object GraphvizDot: ExternalApp {
    fun generate(source: String, fileFormat: String): ByteArray {
        val p = ProcessBuilder("dot", "-T${fileFormat}").start()
        p.outputStream.write(source.toByteArray(Charset.defaultCharset()))
        p.outputStream.flush()
        p.outputStream.close()
        return p.inputStream.readAllBytes()
    }

    private fun getCommandPath(command: String): Path? {
        val paths = System.getenv("PATH").split(File.pathSeparator)
        for(path in paths) {
            val fullPath = Path.of(path, command)
            println(fullPath)
            if(fullPath.exists()) {
                return fullPath
            }
        }
        return null
    }

    override fun canRun(): Boolean {
        return getCommandPath("dot") != null;
    }
}