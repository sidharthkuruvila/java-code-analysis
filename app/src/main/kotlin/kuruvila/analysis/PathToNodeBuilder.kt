package kuruvila.analysis

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.metamodel.PropertyMetaModel
import java.lang.reflect.Method
import java.util.*

class PathToNodeBuilder {

    private fun parentPropertyPath(path: PathToNode): PathToNode {
        val node = path.node
        val parentNode = node.parentNode.get()
        val metaModel = parentNode.metaModel
        val propertyMetaModels = metaModel.allPropertyMetaModels
        for(propertyMetaModel in propertyMetaModels) {
            val getterName = propertyMetaModel.getterMethodName
            val method = parentNode.javaClass.methods.first { method: Method? -> method?.name == getterName }
            val res = when (val r = method.invoke(parentNode)) {
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
                is Node -> {
                    if(res == node) {
                        return PathToNode.Path(parentNode, propertyMetaModel, 0, path)
                    }
                }

                is NodeList<*> -> {
                    val index = res.indexOf(node)
                    if(index >= 0) {
                        return PathToNode.Path(parentNode, propertyMetaModel, index, path)
                    }
                }

                else -> {
                    //Ignore nulls for now
                }
            }

        }
        throw java.lang.RuntimeException("Should not come here")
    }

    private fun buildPath(path: PathToNode): PathToNode {
        return if(path.node.parentNode.isPresent) {
            buildPath(parentPropertyPath(path))
        } else {
            path
        }
    }

    fun buildPath(node: Node): PathToNode {
        return buildPath(PathToNode.Final(node))
    }

}

interface PathToNode {
    val node: Node

    fun length(): Int


    fun displayNode(): String {
        val n = node
        return if(n is ClassOrInterfaceDeclaration) {
            if( n.fullyQualifiedName.isPresent) {
                "class:${n.fullyQualifiedName.get()}"
            } else {
                n.javaClass.name
            }
        } else {
            n.javaClass.name
        }
    }
    data class Path(override val node: Node, val propertyMetaModel: PropertyMetaModel, val index: Int, val next: PathToNode) : PathToNode {
        override fun length(): Int { return 1 + next.length()}

        override fun toString(): String { return "[${displayNode()}, ${propertyMetaModel.name}, ${index}, ${next}]" }
    }
    data class Final(override val node: Node ): PathToNode {
        override fun length(): Int { return 1}

        override fun toString(): String {
            return "[${displayNode()}]"
        }
    }
}