package kuruvila.gen

import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.UnaryExpr
import com.github.javaparser.ast.stmt.SwitchEntry
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.metamodel.JavaParserMetaModel
import com.github.javaparser.metamodel.PropertyMetaModel
import kotlinx.serialization.Serializable
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import kotlin.jvm.optionals.toList

fun main() {

    gen()
//    MetaModelDiagram().draw()

}


data class SqlClause(val name: String, val query: String)

fun c(name: String, query: String) = SqlClause(name, query)

fun gen() {

    val nodeMetaModels = JavaParserMetaModel.getNodeMetaModels()
    val allParents = nodeMetaModels
            .flatMap { nodeMetaModel -> nodeMetaModel.superNodeMetaModel.toList() }
            .toSet()
    val all = nodeMetaModels.toSet()
    val children = all.subtract(allParents)
    val terms = children.map { node ->
        Term(node.typeName, node.allPropertyMetaModels.map { propertyMetaModel ->
            TermProperty(propertyMetaModel.name, valueType = getValueType(propertyMetaModel),
                    nodeName = propertyMetaModel.nodeReference.getOrNull()?.typeName)
        })
    }

    val nodeQuery = c(
            "node(node_id, type_name, property_id",
            """select node.id                   as node_id,
       node_type.name            as node_type,
       node.ast_node_property_id as property_id
from ast_node as node
         join ast_node_type as node_type on node.ast_node_type_id = node_type.id"""
    )
    val nodePropertyQuery = c(
"property(property_id, property_name, node_id)",
            """select node.id                   as node_id,
       node_type.name            as node_type,
       node.ast_node_property_id as property_id
from ast_node as node
         join ast_node_type as node_type on node.ast_node_type_id = node_type.id"""
    )

    val clauses: List<SqlClause> = listOf(
            nodeQuery,
            nodePropertyQuery,
            *terms.flatMap{genTerm(it)}.toTypedArray()
    )

    //TODO write query to map the super class nodes to their child nodes
    //TODO write select query with case statements to chose correct clause based on type of node

    val query = """
        when
        ${clauses.map { "${it.name} as (${it.query})" }.joinToString(", \n")}
        select * from node limit 10
    """.trimIndent()
    Path("db_to_code.sql").writeText(query)
}


fun genTerm(term: Term): List<SqlClause> {
    // node code table name: node_{type_name}_code(property_id, node_id, code)
    val typeName = term.typeName
    val properties = term.properties
    val template = term.makeTemplate()

    val joinClauses = properties.map { termProperty ->
        val propertyName = termProperty.propertyName
        "join node_${typeName}_property_${propertyName}_code as ${propertyName} on ${propertyName}.node_id = node.node_id and ${propertyName}.property_name = '${propertyName}'"
    }
    fun esc(s: String) = s.replace("'", "''")
    val templateSql = template.map {
        when (it) {
            is Template.Property -> "${it.name}.code"
            is Template.Text -> "'${esc(it.text)}'"
        }
    }.joinToString(" || ")
    val termQuery = """
        select node_id, ${templateSql} as code from node
        ${joinClauses.joinToString("\n")}
    """

    // node property code table name: node_{type_name}_property_{property_name}_code(node_id, property_name, code)s
    val propertyQueries = properties.map { termProperty ->
        val propertyName = termProperty.propertyName
        val propertyQueryName = "node_${typeName}_property_${propertyName}_code(node_id, property_name, code)"
        val propertyQuery: String = when(termProperty.valueType) {
            ValueType.Node -> {
                val nodeTable = "node_${termProperty.nodeName}_code"
                val (openParen, closeParen) = termProperty.parenthesis
                val separator = termProperty.separator
                """
           select property.node_id as node_id, property.property_name as property_name, '${openParen}' || group_concat(code,'${separator}') ||'${closeParen}' as code from property
           join ${nodeTable} on ${nodeTable}.property_id = property.property_id
           group by node_id, property_name
        """
            }
            ValueType.Token -> {
                """
                   select node_id, property_name, value as code from property
                   join ast_node_property_token as token on property.property_id = token.id
                """
            }
            ValueType.Boolean -> {
                """
                   select node_id, property_name, value as code from property
                   join ast_node_property_boolean as token on property.property_id = token.id
                """
            }
            ValueType.String -> {
                """
                   select node_id, property_name, value as code from property
                   join ast_node_property_string as token on property.property_id = token.id
                """
            }

        }
        c(propertyQueryName, propertyQuery)
    }

    return listOf(
            c("node_${typeName}_code(property_id, node_id, code)", termQuery),
            *propertyQueries.toTypedArray()
    )
}

sealed interface Template {
    data class Property(val name: String) : Template
    data class Text(val text: String) : Template
}

fun p(name: String): Template = Template.Property(name)
fun t(text: String): Template = Template.Text(text)

val exampleTemplate = listOf(t("if("), p("condition"),
        t(") { "), p("thenStmt"), t(" } else { "), p("elseStmt"), " }")


@Serializable
enum class ValueType {
    Node, String, Boolean, Token
}

@Serializable
data class TermProperty(
        val propertyName: String,
        val valueType: ValueType,
        val nodeName: String?,
        val separator: String = ",",
        val parenthesis: Pair<String, String> = Pair("", ""))

@Serializable
data class Term(
        val typeName: String,
        val properties: List<TermProperty>,
        val template: List<Template>? = null) {
    fun makeTemplate(): List<Template> {
        if(template != null) {
            return template;
        }
        return listOf(
                t("(${typeName} "),
                // A bit of extra space at the end
                *properties.flatMap { listOf(p(it.propertyName), t( " ") )}.toTypedArray(),
                t(")")
        )
    }
}


val tokenClasses = listOf(
        Modifier.Keyword::class.java,
        AssignExpr.Operator::class.java,
        BinaryExpr.Operator::class.java,
        UnaryExpr.Operator::class.java,
        PrimitiveType.Primitive::class.java,
        ArrayType.Origin::class.java,
        SwitchEntry.Type::class.java,
)


fun getValueType(propertyMetaModel: PropertyMetaModel): ValueType =
        if (com.github.javaparser.ast.Node::class.java.isAssignableFrom(propertyMetaModel.type)
                || NodeList::class.java.isAssignableFrom(propertyMetaModel.type)) {
            ValueType.Node
        } else if (Boolean::class.java.isAssignableFrom(propertyMetaModel.type)) {
            ValueType.Boolean
        } else if (String::class.java.isAssignableFrom(propertyMetaModel.type)) {
            ValueType.String
        } else if (tokenClasses.any { tokenClass -> tokenClass.isAssignableFrom(propertyMetaModel.type) }) {
            ValueType.Token
        } else {
            throw RuntimeException()
        }
