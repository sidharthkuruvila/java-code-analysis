package kuruvila.gen

import com.github.javaparser.metamodel.JavaParserMetaModel
import kuruvila.gen.diagrams.MetaModelDiagram

fun main() {
    val nodeMetaModels = JavaParserMetaModel.getNodeMetaModels()

    MetaModelDiagram().draw()

}