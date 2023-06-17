package kuruvila.analysis

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.resolution.TypeSolver
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import kuruvila.analysis.gradle.Gradle
import java.nio.file.Path
import java.sql.DriverManager
/*
fun main(){
    fun main() {

        DriverManager.getConnection("jdbc:sqlite:sample.db").use { connection ->
            val codeDb = CodeDb(connection)
            connection.autoCommit = false
            codeDb.initialize()
            connection.commit()
            val project = Gradle(Path.of("/Users/sidharth/code/downloads/spring-petclinic"))
            val module = project.modules().first()





            val reflectionTypeSolver: TypeSolver = ReflectionTypeSolver()
            val javaParserTypeSolvers = module.sources.map { JavaParserTypeSolver(it) }
            val jarTypeSolvers = module.jars.map { JarTypeSolver(it) }
            val typeSolver = CombinedTypeSolver()
            typeSolver.add(reflectionTypeSolver)
            for (ts in javaParserTypeSolvers) {
                typeSolver.add(ts)
            }
            for (ts in jarTypeSolvers) {
                typeSolver.add(ts)
            }
            val symbolSolver = JavaSymbolSolver(typeSolver)
            val config = ParserConfiguration()
            config.setSymbolResolver(symbolSolver)
            val jp = JavaParser(config)
            for(file in module.allJavaSourceFiles()) {
                println(file)
                extractFile(jp, file, codeDb)
                connection.commit()
            }
}
 */