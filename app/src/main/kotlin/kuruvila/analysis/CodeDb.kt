package kuruvila.analysis

import com.github.javaparser.ast.Node
import com.github.javaparser.metamodel.JavaParserMetaModel
import com.github.javaparser.metamodel.PropertyMetaModel
import java.nio.file.Path
import java.sql.Connection
import kotlin.io.path.absolutePathString

class CodeDb(val connection: Connection) {
    fun createTables() {
        connection.createStatement().use { statement ->
            statement.setQueryTimeout(30) // set timeout to 30 sec.

            statement.executeUpdate("drop table if exists ast_node_type")
            statement.executeUpdate("drop table if exists ast_node_property_type ")
            statement.executeUpdate("drop table if exists ast_node")
            statement.executeUpdate("drop table if exists ast_node_property_string")
            statement.executeUpdate("drop table if exists ast_node_property_boolean")
            statement.executeUpdate("drop table if exists ast_node_property_node")
            statement.executeUpdate("drop table if exists ast_node_property_token")
            statement.executeUpdate("drop table if exists ast_node_property")
            statement.executeUpdate("drop table if exists source_file")

            statement.executeUpdate("create table ast_node_type (id integer primary key autoincrement, name string)")
            statement.executeUpdate("create table ast_node_property_type (id integer primary key autoincrement, parent_ast_node_type_id integer, ast_node_type_id integer, name text)")
            statement.executeUpdate("""create table ast_node (
                |id integer primary key autoincrement ,
                |ast_node_type_id integer, 
                |ast_node_property_id integer, 
                |begin_line integer, 
                |begin_column integer, 
                |end_line integer, 
                |end_column integer, 
                |source_file_id integer)""".trimMargin())
            statement.executeUpdate("create table ast_node_property_string (id integer, value string)")
            statement.executeUpdate("create table ast_node_property_token (id integer, type string, value string)")
            statement.executeUpdate("create table ast_node_property_boolean (id integer, value boolean)")
            statement.executeUpdate("create table ast_node_property_node (id integer, value integer)")
            statement.executeUpdate("create table ast_node_property (id integer primary key autoincrement, value_type string, ast_node_property_type_id integer, ast_node_id integer, idx integer)")
            statement.executeUpdate("create table source_file (id integer primary key autoincrement, absolute_path string)")
        }
    }

    fun getOrCreateAstNodeType(name: String): Int {
        connection.prepareStatement("select id from ast_node_type where name = ?").use { preparedStatement ->
            preparedStatement.setString(1, name)
            val rs = preparedStatement.executeQuery()
            if (rs.next()) {
                return rs.getInt(1)
            }
        }

        connection.prepareStatement("insert into ast_node_type (name) values (?)").use { preparedStatement ->
            preparedStatement.setString(1, name)
            preparedStatement.executeUpdate()
        }
        connection.prepareStatement("select last_insert_rowid()").use { preparedStatement ->
            val rs = preparedStatement.executeQuery()
            assert(rs.next())
            val id = rs.getInt(1)
//            println("Inserted ast_node_type ($id, $name)")
            return id
        }

    }

    fun getOrCreateAstNodePropertyType(propertyMetaModel: PropertyMetaModel): Int {
        val name = propertyMetaModel.name
        val parentNodeName = propertyMetaModel.containingNodeMetaModel.typeName
        val nodeName = propertyMetaModel.typeName
        val parentNodeId = getOrCreateAstNodeType(parentNodeName)
        val nodeId = getOrCreateAstNodeType(nodeName)

        connection.prepareStatement("select id from ast_node_property_type where name = ? and parent_ast_node_type_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, name)
            preparedStatement.setInt(2, parentNodeId)
            val rs = preparedStatement.executeQuery()
            if (rs.next()) {
                return rs.getInt(1)
            }
        }
        connection.prepareStatement("insert into ast_node_property_type (name, parent_ast_node_type_id, ast_node_type_id) values (?, ?, ?)").use { preparedStatement ->
            preparedStatement.setString(1, name)
            preparedStatement.setInt(2, parentNodeId)
            preparedStatement.setInt(3, nodeId)
            preparedStatement.executeUpdate()
        }
        connection.prepareStatement("select last_insert_rowid()").use { preparedStatement ->
            val rs = preparedStatement.executeQuery()
            rs.next()
            val id = rs.getInt(1)
//            println("Inserted ast_node_property_type ($id, $parentNodeName($parentNodeId), $name)")
            return id
        }
    }

    fun loadMetaModel() {
        val nodeMetaModels = JavaParserMetaModel.getNodeMetaModels()
        for (nodeMetaModel in nodeMetaModels) {
            for (propertyMetaModel in nodeMetaModel.allPropertyMetaModels) {
                val propertyName = propertyMetaModel.name
                assert(propertyMetaModel.name != null)
                val propertyTypeName = propertyMetaModel.typeName
                assert(propertyTypeName != nodeMetaModel.typeName)
                getOrCreateAstNodePropertyType(propertyMetaModel)
            }
        }
    }

    fun initialize() {
        createTables()
        loadMetaModel()
    }

    fun getOrCreateSourceFile(file: Path): Int {

        connection.prepareStatement("select id from source_file where absolute_path = ?").use { preparedStatement ->
            preparedStatement.setString(1, file.absolutePathString())
            val rs = preparedStatement.executeQuery()
            if (rs.next()) {
                return rs.getInt(1)
            }
        }
        connection.prepareStatement("insert into source_file (absolute_path) values (?)").use { preparedStatement ->
            preparedStatement.setString(1, file.absolutePathString())
            preparedStatement.executeUpdate()
        }
        connection.prepareStatement("select last_insert_rowid()").use { preparedStatement ->
            val rs = preparedStatement.executeQuery()
            rs.next()
            val id = rs.getInt(1)
//            println("Inserted source_file ($id, ${file.absolutePathString()})")
            return id
        }
    }

    /*
         |ast_node_type_id integer,
                |ast_node_property_id integer,
                |begin_line integer,
                |begin_column integer,
                |end_line integer,
                |end_column integer,
                |source_file_id integer
     */
    fun createAstNode(node: Node, sourceFileId: Int): Int {
        val metaModel = node.metaModel
        val nodeTypeId = getOrCreateAstNodeType(metaModel.typeName)
        connection.prepareStatement("""insert into ast_node (ast_node_type_id, begin_line, begin_column, end_line, end_column, source_file_id) 
            |values (?, ?, ?, ?, ?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, nodeTypeId)
            if(node.range.isPresent) {
                val range = node.range.get()
                val begin = range.begin
                val end = range.end
                preparedStatement.setInt(2, begin.line)
                preparedStatement.setInt(3, begin.column)
                preparedStatement.setInt(4, end.line)
                preparedStatement.setInt(5, end.column)

            }else {
                preparedStatement.setInt(2, -1)
                preparedStatement.setInt(3, -1)
                preparedStatement.setInt(4, -1)
                preparedStatement.setInt(5, -1)
            }
            preparedStatement.setInt(6, sourceFileId)
            preparedStatement.executeUpdate()
        }
        connection.prepareStatement("select last_insert_rowid()").use { preparedStatement ->
            val rs = preparedStatement.executeQuery()
            rs.next()
            val id = rs.getInt(1)
//            println("Inserted ast_node ($id, $node)")
            return id
        }
    }

    fun createAstNodeProperty(valueType: String, nodeId: Int, propertyMetaModel: PropertyMetaModel, index: Int): Int {
        val propertyTypeId = getOrCreateAstNodePropertyType(propertyMetaModel)
        connection.prepareStatement("""insert into ast_node_property (value_type, ast_node_property_type_id, ast_node_id, idx) 
            |values (?, ?, ?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setString(1, valueType)
            preparedStatement.setInt(2, propertyTypeId)
            preparedStatement.setInt(3, nodeId)
            preparedStatement.setInt(4, index)
            preparedStatement.executeUpdate()
        }
        connection.prepareStatement("select last_insert_rowid()").use { preparedStatement ->
            val rs = preparedStatement.executeQuery()
            rs.next()
            val id = rs.getInt(1)
//            println("Inserted ast_node_property ($id, $valueType, $nodeId, $index)")
            return id
        }
    }

    fun addAstNodePropertyNodeDetails(parentPropertyId: Int, nodeId: Int) {
        connection.prepareStatement("""insert into ast_node_property_node (id, value) 
            |values (?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, parentPropertyId)
            preparedStatement.setInt(2, nodeId)
            preparedStatement.executeUpdate()
        }
    }

    fun addAstNodePropertyStringDetails(parentPropertyId: Int, value: String) {
        connection.prepareStatement("""insert into ast_node_property_string (id, value) 
            |values (?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, parentPropertyId)
            preparedStatement.setString(2, value)
            preparedStatement.executeUpdate()
        }
    }

    fun addAstNodePropertyBooleanDetails(parentPropertyId: Int, value: Boolean) {
        connection.prepareStatement("""insert into ast_node_property_boolean (id, value) 
            |values (?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, parentPropertyId)
            preparedStatement.setBoolean(2, value)
            preparedStatement.executeUpdate()
        }
    }

    fun addAstNodePropertyTokenDetails(parentPropertyId: Int, type: String, value: String) {
        connection.prepareStatement("""insert into ast_node_property_token (id, type, value) 
            |values (?, ?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, parentPropertyId)
            preparedStatement.setString(2, type)
            preparedStatement.setString(3, value)
            preparedStatement.executeUpdate()
        }
    }
}