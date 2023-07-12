package kuruvila.analysis.db

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.sql.Connection
import java.time.format.DateTimeFormatter
import java.util.HexFormat
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readBytes


enum class CheckResult {
    DELETED, // Present in db but not in file system
    NEW, // Present in file system but not in db
    SAME, // Present in both with same hash
    CHANGED, // Present in both but with different hashes
    MISSING // Not present in either
}
class FileDetailsUpdater(conn: Connection): DbUpdater(conn) {

    val md =  MessageDigest.getInstance("SHA-256");

    override val tableStatements = listOf(
            "create table file_type (id integer primary key autoincrement, file_type string, extension string)",
            "create table file (id integer primary key autoincrement, absolute_path string, relative_path string, file_type_id integer, last_update string, hash string)"
    )

    override fun initializeData(){
        val fileTypes = listOf(
                ".java" to "java",
                ".class" to "class",
                ".properties" to "properties",
                ".kt" to "kotlin"
        )

        val insertQuery = "insert into file_type (extension, file_type) values (?, ?)"
        conn.prepareStatement(insertQuery).use { stmt ->
            for ((extension, fileType) in fileTypes) {
                stmt.setString(1, extension)
                stmt.setString(2, fileType)
                check(stmt.executeUpdate() == 1)
            }
        }
    }

    fun checkFile(absolutePath: Path): CheckResult {
        val present = absolutePath.exists()

        val dbHash = getFileHash(absolutePath)
        if(dbHash == null) {
            if(present) {
                return CheckResult.NEW
            }
            return CheckResult.MISSING
        } else {
            if(present) {
                val fileHash = hash(absolutePath)
                if(dbHash == fileHash) {
                    return CheckResult.SAME
                }
                return CheckResult.CHANGED
            }
            return CheckResult.DELETED
        }
    }

    fun addFile(absolutePath: Path, fileRelativePath: Path): Int {
        val checkResult = checkFile(absolutePath)
        check(checkResult == CheckResult.NEW)
        val fileAttributes = Files.readAttributes(absolutePath, BasicFileAttributes::class.java)
        val modifiedOnInstant = fileAttributes.lastModifiedTime().toInstant()
        val modifiedOn = DateTimeFormatter.ISO_INSTANT.format(modifiedOnInstant)
        val hash = hash(absolutePath)
        val query = "insert into file (absolute_path, relative_path, file_type_id, last_update, hash) select ? as absolute_path, ?, id as file_type_id, ?, ? " +
                "from file_type where extension = substring(absolute_path, length(absolute_path) - length(extension) + 1, length(absolute_path))"

        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, absolutePath.absolutePathString())
            stmt.setString(2, fileRelativePath.toString())
            stmt.setString(3, modifiedOn)
            stmt.setString(4, hash)
            println(absolutePath.absolutePathString())
            check(stmt.executeUpdate() == 1)
        }
        return getFileId(absolutePath)
    }


    fun updateFile(absolutePath: Path) {
        val checkResult = checkFile(absolutePath)
        check(checkResult == CheckResult.CHANGED)
        val fileAttributes = Files.readAttributes(absolutePath, BasicFileAttributes::class.java)
        val modifiedOnInstant = fileAttributes.lastModifiedTime().toInstant()
        val modifiedOn = DateTimeFormatter.ISO_INSTANT.format(modifiedOnInstant)
        val hash = hash(absolutePath)
        val query = "update file set last_update=?, hash=? where absolute_path = ?"

        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, modifiedOn)
            stmt.setString(2, hash)
            stmt.setString(3, absolutePath.absolutePathString())
            check(stmt.executeUpdate() == 1)
        }
    }

    fun hash(path: Path): String {
        val hash = md.digest(path.readBytes());
        return HexFormat.of().formatHex(hash)
    }

    fun getFileHash(path: Path): String? {
        val query = "select hash from file where absolute_path = ?"
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, path.absolutePathString())
            val rs = stmt.executeQuery()
            if(!rs.next()){
                return null
            }
            val hash = rs.getString(1);
            return hash
        }
    }

    fun getFileId(path: Path): Int {
        val query = "select id from file where absolute_path = ?"
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, path.absolutePathString())
            val rs = stmt.executeQuery()
            check(rs.next())
            return rs.getInt(1)
        }
    }

    fun removeFile(absolutePath: Path) {
        val checkResult = checkFile(absolutePath)
        check(checkResult == CheckResult.DELETED)
        val query = "delete from file where absolute_path = ?"

        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, absolutePath.absolutePathString())
            check(stmt.executeUpdate() == 1)
        }
    }
}