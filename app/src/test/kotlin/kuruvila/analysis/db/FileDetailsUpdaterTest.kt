package kuruvila.analysis.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

class FileDetailsUpdaterTest {


    var update: FileDetailsUpdater? = null
    @BeforeEach
    fun beforEach() {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        update = FileDetailsUpdater(conn)
        update!!.initialize()
    }
    @Test
    fun testAddingAFile() {

        val checkResult1 = update!!.checkFile(Path("/a/non/existant/File.java"))
        assertEquals(CheckResult.MISSING, checkResult1)

        val tempFile = Files.createTempFile("test", ".java")
        tempFile.writeText("Some text")

        val checkResult2 = update!!.checkFile(tempFile)
        assertEquals(CheckResult.NEW, checkResult2)

        update!!.addFile(tempFile, tempFile)

        val checkResult3 = update!!.checkFile(tempFile)
        assertEquals(CheckResult.SAME, checkResult3)

        tempFile.writeText("Some other text")
        val checkResult4 = update!!.checkFile(tempFile)
        assertEquals(CheckResult.CHANGED, checkResult4)

        update!!.updateFile(tempFile)
        val checkResult5 = update!!.checkFile(tempFile)
        assertEquals(CheckResult.SAME, checkResult5)

        tempFile.deleteIfExists()
        val checkResult6 = update!!.checkFile(tempFile)

        assertEquals(CheckResult.DELETED, checkResult6)

        update!!.removeFile(tempFile)

        val checkResult7 = update!!.checkFile(tempFile)
        assertEquals(CheckResult.MISSING, checkResult7)

    }
}