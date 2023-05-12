package kuruvila.analysis

import java.sql.DriverManager

class CodeDbTest {
    fun test(){
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val codeDb = CodeDb(connection)
            codeDb.initialize()
        }
    }
}