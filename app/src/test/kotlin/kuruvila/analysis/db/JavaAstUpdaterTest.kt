package kuruvila.analysis.db

import com.github.javaparser.JavaParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import kotlin.io.path.writeText

class JavaAstUpdaterTest {
    @Test
    fun testCreatingAnAst() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use {conn ->
            val jp = JavaParser()
            val updater = JavaAstUpdater(conn, jp)
            updater.initialize()
            val tempFile = Files.createTempFile("test", ".java")
            tempFile.writeText("""
            package kuruvila;
            public class Klass {
                int a = 5;
                Map<String, Long> map = new HashMap<>();
                String f() {
                    if(true) {
                    System.out.println("hello");
                    }
                    return "world";
                }
            }
        """.trimIndent())
            updater.updateFileAst(1, tempFile)
            assertEquals(41, listAllNodes(conn, 1))
            updater.deleteAstForFileId(1)
            assertEquals(0, listAllNodes(conn, 1))
        }
    }
    fun listAllNodes(conn: Connection, fileId: Int): Int {
        val query = """
            select count(*) from ast_node where source_file_id = ?
        """.trimIndent()
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, fileId)
            val rs = stmt.executeQuery()
            assertTrue(rs.next())
            return rs.getInt(1)
        }
    }
}