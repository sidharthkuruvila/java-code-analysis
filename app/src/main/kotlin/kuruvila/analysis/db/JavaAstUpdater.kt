package kuruvila.analysis.db

import com.github.javaparser.JavaParser
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
import kuruvila.analysis.NodeInfo
import java.lang.reflect.Method
import java.nio.file.Path
import java.sql.Connection
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.jvm.optionals.getOrNull

class JavaAstUpdater(conn: Connection, val jp: JavaParser) : DbUpdater(conn) {
    override val tableStatements: List<String> = listOf(
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
            """create table ast_node_property (id integer primary key autoincrement, ast_node_property_type_id integer, ast_node_id integer, idx integer, property_idx integer)""",
    )


    override fun initializeData() {
        loadMetaModel()
    }

    fun loadMetaModel() {
        val nodeMetaModels = JavaParserMetaModel.getNodeMetaModels()
        for (nodeMetaModel in nodeMetaModels) {
            for (propertyMetaModel in nodeMetaModel.allPropertyMetaModels) {
                val propertyName = propertyMetaModel.name
                check(propertyMetaModel.name != null)
                val propertyTypeName = propertyMetaModel.typeName
                getOrCreateAstNodePropertyType(propertyMetaModel)
            }
        }
    }

    fun getOrCreateAstNodePropertyType(propertyMetaModel: PropertyMetaModel): Int {
        val name = propertyMetaModel.name
        val parentNodeName = propertyMetaModel.containingNodeMetaModel.typeName
        val nodeName = propertyMetaModel.typeName
        val parentNodeId = getOrCreateAstNodeType(propertyMetaModel.containingNodeMetaModel)

        conn.prepareStatement("select id from ast_node_property_type where name = ? and parent_ast_node_type_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, name)
            preparedStatement.setInt(2, parentNodeId)
            val rs = preparedStatement.executeQuery()
            if (rs.next()) {
                return rs.getInt(1)
            }
        }
        conn.prepareStatement("insert into ast_node_property_type (name, parent_ast_node_type_id, ast_node_type_id, value_type) values (?, ?, ?, ?)").use { preparedStatement ->
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
        conn.prepareStatement("select last_insert_rowid()").use { preparedStatement ->
            val rs = preparedStatement.executeQuery()
            rs.next()
            val id = rs.getInt(1)
//            println("Inserted ast_node_property_type ($id, $parentNodeName($parentNodeId), $name)")
            return id
        }
    }

    fun getOrCreateAstNodeType(nodeMetaModel: BaseNodeMetaModel): Int {
        val name = nodeMetaModel.typeName
        val parentNode = nodeMetaModel.superNodeMetaModel.getOrNull()

        conn.prepareStatement("select id from ast_node_type where name = ?").use { preparedStatement ->
            preparedStatement.setString(1, name)
            val rs = preparedStatement.executeQuery()
            if (rs.next()) {
                return rs.getInt(1)
            }
        }

        conn.prepareStatement("insert into ast_node_type (inherits_from_ast_node_type_id, name) values (?, ?)").use { preparedStatement ->
            if (parentNode != null) {
                val parentNodeId = getOrCreateAstNodeType(parentNode)
                preparedStatement.setInt(1, parentNodeId)
            }
            preparedStatement.setString(2, name)
            preparedStatement.executeUpdate()
        }
        conn.prepareStatement("select last_insert_rowid()").use { preparedStatement ->
            val rs = preparedStatement.executeQuery()
            assert(rs.next())
            val id = rs.getInt(1)
            return id
        }
    }

    fun updateFileAst(fileId: Int, path: Path) {
        deleteAstForFileId(fileId)
        val pr = jp.parse(path)
        val cu = pr.result.get();

        val queue = ArrayDeque<NodeInfo>()
        queue.add(NodeInfo(cu, -1))
        while (queue.isNotEmpty()) {
            val nodeInfo = queue.removeFirst()
            val propertyId = nodeInfo.parentPropertyId
            val node = nodeInfo.node
            val metaModel = node.metaModel;
            val propertyMetaModels = metaModel.allPropertyMetaModels
            val nodeId = createAstNode(node, propertyId, fileId)
            addAstNodePropertyNodeDetails(nodeInfo.parentPropertyId, nodeId)
            var propertyIndex = 0
            for (propertyMetaModel in propertyMetaModels) {
                val getterName = propertyMetaModel.getterMethodName
                val method = node.javaClass.methods.first { method: Method? -> method?.name == getterName }

                val res = when (val r = method.invoke(node)) {
                    is Optional<*> -> {
                        if (r.isPresent) {
                            r.get()
                        } else {
                            null
                        }
                    }

                    else -> r
                }

                when (res) {
                    null -> {
                        //Ignore nulls for now
                    }

                    is Node -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        queue.add(NodeInfo(res, propertyId))
                    }

                    is NodeList<*> -> {
                        res.mapIndexed { j, child ->
                            val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, j, propertyIndex)
                            queue.add(NodeInfo(child, propertyId))
                        }
                    }

                    is Boolean -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        addAstNodePropertyBooleanDetails(propertyId, res)
                    }

                    is String -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        addAstNodePropertyStringDetails(propertyId, res)
                    }

                    is Modifier.Keyword -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        addAstNodePropertyTokenDetails(propertyId, "modifier", res.asString())
                    }

                    is AssignExpr.Operator -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        addAstNodePropertyTokenDetails(propertyId, "assign_operator", res.asString())
                    }

                    is BinaryExpr.Operator -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        addAstNodePropertyTokenDetails(propertyId, "binary_operator", res.asString())
                    }

                    is UnaryExpr.Operator -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        addAstNodePropertyTokenDetails(propertyId, "unary_operator", res.asString())
                    }

                    is PrimitiveType.Primitive -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        addAstNodePropertyTokenDetails(propertyId, "primitive_type", res.asString())
                    }

                    is ArrayType.Origin -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        addAstNodePropertyTokenDetails(propertyId, "array_type_origin", res.name)
                    }

                    is SwitchEntry.Type -> {
                        val propertyId = createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        addAstNodePropertyTokenDetails(propertyId, "switch_entry_type", res.name)
                    }

                    else -> throw IllegalStateException("value $res, has unsupported type ${res.javaClass}")
                }
                propertyIndex += 1
            }
        }
    }

    fun createAstNode(node: Node, parentPropertyId: Int, sourceFileId: Int): Int {
        val metaModel = node.metaModel
        val nodeTypeId = getOrCreateAstNodeType(metaModel)
        conn.prepareStatement("""insert into ast_node (ast_node_type_id, ast_node_property_id, begin_line, begin_column, end_line, end_column, source_file_id)
            |values (?, ?, ?, ?, ?, ?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, nodeTypeId)
            preparedStatement.setInt(2, parentPropertyId)
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
        conn.prepareStatement("select last_insert_rowid()").use { preparedStatement ->
            val rs = preparedStatement.executeQuery()
            rs.next()
            val id = rs.getInt(1)
//            println("Inserted ast_node ($id, $node)")
            return id
        }
    }

    fun addAstNodePropertyNodeDetails(parentPropertyId: Int, nodeId: Int) {
        conn.prepareStatement("""insert into ast_node_property_node (id, value) 
            |values (?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, parentPropertyId)
            preparedStatement.setInt(2, nodeId)
            preparedStatement.executeUpdate()
        }
    }

    fun createAstNodeProperty(nodeId: Int, propertyMetaModel: PropertyMetaModel, index: Int, propertyIndex: Int): Int {
        val propertyTypeId = getOrCreateAstNodePropertyType(propertyMetaModel)
        conn.prepareStatement("""insert into ast_node_property (ast_node_property_type_id, ast_node_id, idx, property_idx)
            |values (?, ?, ?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, propertyTypeId)
            preparedStatement.setInt(2, nodeId)
            preparedStatement.setInt(3, index)
            preparedStatement.setInt(4, propertyIndex)
            preparedStatement.executeUpdate()
        }
        conn.prepareStatement("select last_insert_rowid()").use { preparedStatement ->
            val rs = preparedStatement.executeQuery()
            rs.next()
            val id = rs.getInt(1)
//            println("Inserted ast_node_property ($id, $valueType, $nodeId, $index)")
            return id
        }
    }

    fun addAstNodePropertyBooleanDetails(parentPropertyId: Int, value: Boolean) {
        conn.prepareStatement("""insert into ast_node_property_boolean (id, value)
            |values (?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, parentPropertyId)
            preparedStatement.setBoolean(2, value)
            preparedStatement.executeUpdate()
        }
    }

    fun addAstNodePropertyStringDetails(parentPropertyId: Int, value: String) {
        conn.prepareStatement("""insert into ast_node_property_string (id, value)
            |values (?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, parentPropertyId)
            preparedStatement.setString(2, value)
            preparedStatement.executeUpdate()
        }
    }

    fun addAstNodePropertyTokenDetails(parentPropertyId: Int, type: String, value: String) {
        conn.prepareStatement("""insert into ast_node_property_token (id, type, value)
            |values (?, ?, ?)""".trimMargin()).use { preparedStatement ->
            preparedStatement.setInt(1, parentPropertyId)
            preparedStatement.setString(2, type)
            preparedStatement.setString(3, value)
            preparedStatement.executeUpdate()
        }
    }

    fun deleteAstForFileId(fileId: Int) {
        val types = listOf(
                "token", "string", "boolean", "node"
        )
        for (type in types) {
            val query = """WITH ids(id) AS (
  select ast_node_property.id from ast_node_property_${type}
  join ast_node_property on (ast_node_property.id = ast_node_property_${type}.id)
  join ast_node on (ast_node_property.id = ast_node_property.ast_node_id)
  where ast_node.source_file_id = ?
  )
  delete from ast_node_property_${type} where id in ids"""
            conn.prepareStatement(query).use { stmt ->
                stmt.setInt(1, fileId)
                stmt.executeUpdate()
            }
        }
        val deletePropertiesQuery = """WITH ids(id) AS (
  select ast_node_property.id from ast_node_property
  join ast_node on (ast_node_property.id = ast_node_property.ast_node_id)
  where ast_node.source_file_id = ?
  )
  delete from ast_node_property where id in ids"""
        conn.prepareStatement(deletePropertiesQuery).use { stmt ->
            stmt.setInt(1, fileId)
            stmt.executeUpdate()
        }
        val deleteNodesQuery = """delete from ast_node where source_file_id = ?"""

        conn.prepareStatement(deleteNodesQuery).use { stmt ->
            stmt.setInt(1, fileId)
            stmt.executeUpdate()
        }
    }
}