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
            "node(node_id, type_name, property_id)",
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

//    val subTypes = allParents.map { parent -> Pair(parent, children.filter {child -> parent.type.isAssignableFrom(child.type) }) }
//    val parentNodeQueries = subTypes.map { (parent, decendants) ->
//        val tableName = "node_${parent.typeName}_code(property_id, node_id, code)"
//        val query = decendants.map{ decendant -> "select property_id, node_id, code from node_${decendant.typeName}_code" }
//                .joinToString("\nunion\n")
//        c(tableName, query)
//    }

    val nodeCodeQueryParts = listOf(
            *terms.map { genTerm(it) }.toTypedArray()
    )

    val nodeCodeQuery = c("node_code(property_id, node_id, code)", nodeCodeQueryParts.joinToString("\nunion\n"))
    val clauses: List<SqlClause> = listOf(
            nodeQuery,
            nodePropertyQuery,
            nodeCodeQuery
    )

    val query = """
        with recursive
        ${clauses.map { "${it.name} as (${it.query})" }.joinToString(", \n")}
        select property_id, node_id, code from node_code
    """.trimIndent()
    Path("db_to_code.sql").writeText(query)
}

fun genTerm(term: Term): String {
    // node code table name: node_{type_name}_code(property_id, node_id, code)
    val typeName = term.typeName
    val properties = term.properties
    val template = term.makeTemplate()

    fun esc(s: String) = s.replace("'", "''")
    val templateSql = template.map {
        when (it) {
            is Template.Property -> "\"${it.name}\".code"
            is Template.Text -> "'${esc(it.text)}'"
        }
    }.joinToString(" || ")


    // node property code table name: node_{type_name}_property_{property_name}_code(node_id, property_name, code)s
    val joinClauses = properties.map { termProperty ->
        val propertyName = termProperty.propertyName
        val propertyQueryName = "node_${typeName}_property_${propertyName}_code(node_id, property_name, code)"
        val propertyQuery: String = when(termProperty.valueType) {
            ValueType.Node -> {
                val (openParen, closeParen) = termProperty.parenthesis
                val separator = termProperty.separator
                """
           select property.node_id as node_id, property.property_name as property_name, '${openParen}' || group_concat(code,'${separator}') ||'${closeParen}' as code from property
           join node_code on node_code.property_id = property.property_id
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
        """left join (${propertyQuery}) as "${propertyName}" on node.node_id = "${propertyName}".node_id"""
    }

    return """
        select node.property_id as property_id, node.node_id as node_id, ${templateSql} as code from node
        ${joinClauses.joinToString("\n")}
        where node.type_name = '${typeName}'
    """
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
