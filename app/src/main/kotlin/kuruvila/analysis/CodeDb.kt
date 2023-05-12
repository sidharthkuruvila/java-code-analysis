package kuruvila.analysis

import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.UnaryExpr
import com.github.javaparser.ast.stmt.SwitchEntry
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.metamodel.BaseNodeMetaModel
import com.github.javaparser.metamodel.JavaParserMetaModel
import com.github.javaparser.metamodel.PropertyMetaModel
import java.nio.file.Path
import java.sql.Connection
import kotlin.io.path.absolutePathString
import kotlin.jvm.optionals.getOrNull

class CodeDb(val connection: Connection) {
    fun createTables() {
        connection.createStatement().use { statement ->
            statement.queryTimeout = 30 // set timeout to 30 sec.

            val createTableStatements = listOf(
                    """create table ast_node_type (id integer primary key autoincrement, inherits_from_ast_node_type_id integer, name string)""",
                    """create table ast_node_property_type (id integer primary key autoincrement, parent_ast_node_type_id integer, value_type string, ast_node_type_id integer, name text)""",
                    """create table ast_node (
                    |id integer primary key autoincrement ,
                    |ast_node_type_id integer, 
                    |ast_node_property_id integer, 
                    |begin_line integer, 
                    |begin_column integer, 
                    |end_line integer, 
                    |end_column integer, 
                    |source_file_id integer)""".trimMargin(),
                    """create table ast_node_property_string (id integer, value string)""",

                    """create table ast_node_property_token (id integer, type string, value string)""",
                    """create table ast_node_property_boolean (id integer, value boolean)""",
                    """create table ast_node_property_node (id integer, value integer)""",
                    """create table ast_node_property (id integer primary key autoincrement, ast_node_property_type_id integer, ast_node_id integer, idx integer)""",
                    """create table source_file_type (id integer primary key autoincrement, extension string)""",
                    """create table source_file (id integer primary key autoincrement, absolute_path string)"""

            )
            val tableNames = createTableStatements.map { statement -> statement.split(" ")[2] }

            for (tableName in tableNames) {
                statement.executeUpdate("drop table if exists $tableName")
            }

            createTableStatements.forEach(statement::executeUpdate)
        }
    }

    fun getOrCreateAstNodeType(nodeMetaModel: BaseNodeMetaModel): Int {
        val name = nodeMetaModel.typeName
        val parentNode = nodeMetaModel.superNodeMetaModel.getOrNull()

        connection.prepareStatement("select id from ast_node_type where name = ?").use { preparedStatement ->
            preparedStatement.setString(1, name)
            val rs = preparedStatement.executeQuery()
            if (rs.next()) {
                return rs.getInt(1)
            }
        }

        connection.prepareStatement("insert into ast_node_type (inherits_from_ast_node_type_id, name) values (?, ?)").use { preparedStatement ->
            if (parentNode != null) {
                val parentNodeId = getOrCreateAstNodeType(parentNode)
                preparedStatement.setInt(1, parentNodeId)
            }
            preparedStatement.setString(2, name)
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
        val parentNodeId = getOrCreateAstNodeType(propertyMetaModel.containingNodeMetaModel)

        connection.prepareStatement("select id from ast_node_property_type where name = ? and parent_ast_node_type_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, name)
            preparedStatement.setInt(2, parentNodeId)
            val rs = preparedStatement.executeQuery()
            if (rs.next()) {
                return rs.getInt(1)
            }
        }
        connection.prepareStatement("insert into ast_node_property_type (name, parent_ast_node_type_id, ast_node_type_id, value_type) values (?, ?, ?, ?)").use { preparedStatement ->
            preparedStatement.setString(1, name)
            preparedStatement.setInt(2, parentNodeId)
            val tokenClasses = listOf(
                    Modifier.Keyword::class.java,
                    AssignExpr.Operator::class.java,
                    BinaryExpr.Operator::class.java,
                    UnaryExpr.Operator::class.java,
                    PrimitiveType.Primitive::class.java,
                    ArrayType.Origin::class.java,
                    SwitchEntry.Type::class.java,
            )
            val valueType =
                    if (Node::class.java.isAssignableFrom(propertyMetaModel.type)
                            || NodeList::class.java.isAssignableFrom(propertyMetaModel.type)) {
                        "node"
                    } else if (Boolean::class.java.isAssignableFrom(propertyMetaModel.type)) {
                        "boolean"
                    } else if (String::class.java.isAssignableFrom(propertyMetaModel.type)) {
                        "string"
                    } else if (tokenClasses.any { tokenClass -> tokenClass.isAssignableFrom(propertyMetaModel.type) }) {
                        "token"
                    } else {
                        throw RuntimeException()
                    }


            if (propertyMetaModel.nodeReference.isPresent) {
                preparedStatement.setInt(3,
                        getOrCreateAstNodeType(propertyMetaModel.nodeReference.get()))

            }
            preparedStatement.setString(4, valueType)
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
    fun createAstNode(node: Node, parentPropertyId: Int, sourceFileId: Int): Int {
        val metaModel = node.metaModel
        val nodeTypeId = getOrCreateAstNodeType(metaModel)
        connection.prepareStatement("""insert into ast_node (ast_node_type_id, ast_node_property_id, begin_line, begin_column, end_line, end_column, source_file_id)
            |values (?, ?, ?, ?, ?, ?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, nodeTypeId)
            preparedStatement.setInt(1, parentPropertyId)
            if (node.range.isPresent) {
                val range = node.range.get()
                val begin = range.begin
                val end = range.end
                preparedStatement.setInt(3, begin.line)
                preparedStatement.setInt(4, begin.column)
                preparedStatement.setInt(5, end.line)
                preparedStatement.setInt(6, end.column)

            } else {
                preparedStatement.setInt(3, -1)
                preparedStatement.setInt(4, -1)
                preparedStatement.setInt(5, -1)
                preparedStatement.setInt(6, -1)
            }
            preparedStatement.setInt(7, sourceFileId)
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

    fun createAstNodeProperty(nodeId: Int, propertyMetaModel: PropertyMetaModel, index: Int): Int {
        val propertyTypeId = getOrCreateAstNodePropertyType(propertyMetaModel)
        connection.prepareStatement("""insert into ast_node_property (ast_node_property_type_id, ast_node_id, idx)
            |values (?, ?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, propertyTypeId)
            preparedStatement.setInt(2, nodeId)
            preparedStatement.setInt(3, index)
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