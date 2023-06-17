package kuruvila.analysis

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.RecordDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import java.nio.file.Path

class ClassHierarchyBuilder(val javaParser: JavaParser, val files: List<Path>, val codeDb: CodeDb) {

    fun build() {

        val fileDecls = files.map {file ->
            val res = javaParser.parse(file)
            val cu = res.result.get()
            val decls = mutableListOf<ClassOrInterfaceDeclaration>()
            val visitor = object: VoidVisitorAdapter<MutableList<ClassOrInterfaceDeclaration>> () {
                override fun visit(n: ClassOrInterfaceDeclaration?, arg: MutableList<ClassOrInterfaceDeclaration>) {
                    decls.add(n!!)
                    super.visit(n, arg)
                }
            }
            cu.accept(visitor, decls)
            Pair(file, decls)
        }

        for ((_, decls) in fileDecls) {
            for(decl in decls) {
                if(decl.fullyQualifiedName.isPresent) {
                    codeDb.getOrCreateClassDefinition(decl.fullyQualifiedName.get(), decl)
                }
            }
        }
        codeDb.connection.commit()

        for ((_, decls) in fileDecls) {
            for(decl in decls) {
                if(decl.fullyQualifiedName.isPresent) {
                    val resolvedTypeDeclaration = decl.symbolResolver.toTypeDeclaration(decl)
                    val classDefinitionId = codeDb.getOrCreateClassDefinition(decl.fullyQualifiedName.get(), null)
                    val ancestorsQualifiedNames = resolvedTypeDeclaration.ancestors.map { it.qualifiedName }
                    for (ancestorQualifiedName in ancestorsQualifiedNames) {
                        val ancestorClassDefinitionId = codeDb.getOrCreateClassDefinition(ancestorQualifiedName, null)
                        codeDb.addClassDefinitionInheritance(classDefinitionId, ancestorClassDefinitionId)
                    }
                }
            }
        }
        codeDb.connection.commit()

    }
}