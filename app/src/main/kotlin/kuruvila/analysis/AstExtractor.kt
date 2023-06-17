package kuruvila.analysis

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
import java.lang.reflect.Method
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayDeque

class AstExtractor(private val codeDb: CodeDb, private val jp: JavaParser) {
    fun extractFile(file: Path) {
        val pr = jp.parse(file)
        val cu = pr.result.get();

        val fileId = codeDb.getOrCreateSourceFile(file)
        val queue = ArrayDeque<NodeInfo>()
        queue.add(NodeInfo(cu, -1))
        while (queue.isNotEmpty()) {
            val nodeInfo = queue.removeFirst()
            val propertyId = nodeInfo.parentPropertyId
            val node = nodeInfo.node
            val metaModel = node.metaModel;
            val propertyMetaModels = metaModel.allPropertyMetaModels
            val nodeId = codeDb.createAstNode(node, propertyId, fileId)
            codeDb.addAstNodePropertyNodeDetails(nodeInfo.parentPropertyId, nodeId)
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
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        queue.add(NodeInfo(res, propertyId))
                    }

                    is NodeList<*> -> {
                        res.mapIndexed { j, child ->
                            val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, j, propertyIndex)
                            queue.add(NodeInfo(child, propertyId))
                        }
                    }

                    is Boolean -> {
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        codeDb.addAstNodePropertyBooleanDetails(propertyId, res)
                    }

                    is String -> {
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        codeDb.addAstNodePropertyStringDetails(propertyId, res)
                    }

                    is Modifier.Keyword -> {
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        codeDb.addAstNodePropertyTokenDetails(propertyId, "modifier", res.asString())
                    }

                    is AssignExpr.Operator -> {
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        codeDb.addAstNodePropertyTokenDetails(propertyId, "assign_operator", res.asString())
                    }

                    is BinaryExpr.Operator -> {
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        codeDb.addAstNodePropertyTokenDetails(propertyId, "binary_operator", res.asString())
                    }
                    is UnaryExpr.Operator -> {
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        codeDb.addAstNodePropertyTokenDetails(propertyId, "unary_operator", res.asString())
                    }
                    is PrimitiveType.Primitive -> {
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        codeDb.addAstNodePropertyTokenDetails(propertyId, "primitive_type", res.asString())
                    }
                    is ArrayType.Origin -> {
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        codeDb.addAstNodePropertyTokenDetails(propertyId, "array_type_origin", res.name)
                    }
                    is SwitchEntry.Type -> {
                        val propertyId = codeDb.createAstNodeProperty(nodeId, propertyMetaModel, 0, propertyIndex)
                        codeDb.addAstNodePropertyTokenDetails(propertyId, "switch_entry_type", res.name)
                    }
                    else -> throw IllegalStateException("value $res, has unsupported type ${res.javaClass}")
                }
                propertyIndex += 1
            }
        }
    }
}