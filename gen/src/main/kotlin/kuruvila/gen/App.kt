package kuruvila.gen

import com.github.javaparser.metamodel.JavaParserMetaModel
import com.github.javaparser.metamodel.PropertyMetaModel
import kuruvila.analysis.externalapps.FileOpener
import kuruvila.analysis.externalapps.GraphvizDot
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.system.exitProcess

fun main() {
    val nodeMetaModels = JavaParserMetaModel.getNodeMetaModels()

    showNodeMetaModel()

}

fun showNodeMetaModel() {
    if(!GraphvizDot.canRun()) {
        println("Unable to run the local dot command")
        exitProcess(1)
    }
    if(!FileOpener.canRun()) {
        println("Unable to open files in external applications")
        exitProcess(1)
    }

    val links = mutableListOf<Triple<String, String, String>>()
    val nodeMetaModels = JavaParserMetaModel.getNodeMetaModels()
    val sr = StringWriter()
    PrintWriter(sr).use {
        it.println("digraph {")
        it.println("rankdir=LR")

        for (e in nodeMetaModels) {
            it.println("\"${e.typeName}\" [")
            it.println("shape=record ")
            it.print("label=\"")
            it.print("${e.typeName}")
            for (p in e.allPropertyMetaModels) {
                it.print("|<${p.name}>${p.name}: ${p.typeName}${getQuantifierLabel(p)}")
                links.add(Triple(e.typeName, p.name, p.typeName))
            }
            it.println("\"")
            it.println("]")
        }

        for (e in nodeMetaModels) {
            if (e.superNodeMetaModel.isPresent) {
                val parent = e.superNodeMetaModel.get()
                it.println(String.format("\"%s\" -> \"%s\"", parent.typeName, e.typeName))

            }

        }
        for ((node, property, child) in links) {
            it.println("\"${node}\":\"${property}\" -> \"${child}\" [color=blue]")
        }
        it.println("}")
    }
    val dot = sr.buffer.toString()
    println("writing dot");
    val png = GraphvizDot.generate(dot, "png")
    Path.of("dump.dot").writeText(dot);
    println("finished writing dot");
    val p = kotlin.io.path.createTempFile(prefix = "can-delete-", suffix = ".png")
    p.writeBytes(png)
    FileOpener.openFile(p.toFile())
}

fun getQuantifierLabel(p: PropertyMetaModel): String {
    if(!p.isOptional && p.isNodeList){
        assert(p.isNonEmpty)
        return "+"
    }
    if(p.isOptional && p.isNodeList){
        assert(p.isNonEmpty)
        return "*"
    }
    if(p.isOptional && !p.isNodeList){
        assert(p.isNonEmpty)
        return "?"
    }

    return ""
}