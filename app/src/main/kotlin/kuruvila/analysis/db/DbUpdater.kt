package kuruvila.analysis.db

import java.sql.Connection

abstract class DbUpdater(val conn: Connection) {
    abstract val tableStatements: List<String>
    fun initialize(){
        conn.createStatement().use { statement ->
            val tableNames = tableStatements.map { statement -> statement.split(" ")[2] }

            for (tableName in tableNames) {
                statement.executeUpdate("drop table if exists $tableName")
            }

            tableStatements.forEach(statement::executeUpdate)
            initializeData()
        }
    }


    abstract fun initializeData()
}
