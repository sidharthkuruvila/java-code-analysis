package kuruvila.analysis.gradle

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.converters.BackwardsCompatibleIdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import java.io.File
import java.lang.RuntimeException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory


data class ModuleInfo(val name: String, val jars: List<Path>, val sources: List<Path>, val compilerOutput: Path?) {

    /**
     * Return all java source files in this module
     */
    fun allJavaSourceFiles(): List<Path> {
        return sources.flatMap { allJavaFiles(it) }
    }

    private fun allJavaFiles(path: Path): List<Path> {
        val fileSystems = FileSystems.getDefault()
        fun f(path: Path): List<Path> {
            val subPaths = Files.newDirectoryStream(path).toList()
            val directories = subPaths.filter { x -> x.isDirectory() }
            val matchingFiles = subPaths.filter { x -> x.absolutePathString().endsWith(".java") }
            return directories.flatMap { x -> f(x) } + matchingFiles
        }
        return f(path)
    }
}

/**
 * Connect to a Gradle project using the Gradle api
 */
class Gradle(val projectPath: Path) {

    /**
     * Extract the gradle project details
     *
     * This uses the idea project features as that appears the to be the easiest way
     * to extract dependency information using the api.
     */
    fun modules(): List<ModuleInfo> {
        val x = GradleConnector.newConnector().forProjectDirectory(projectPath.toFile())
        return x.connect().use { connection ->
            val ideaProject = connection.model(IdeaProject::class.java).get()
            ideaProject.modules.map { module ->
                val sources = module.contentRoots.flatMap { it.sourceDirectories }.map { it.directory.toPath() }
                val buildPath = module.compilerOutput.outputDir?.toPath()
                ModuleInfo(module.name, compileDependencyPaths(module), sources, buildPath)
            }
        }
    }

    private fun compileDependencyPaths(module: IdeaModule): List<Path> {
        return module.dependencies.filter { it.scope.scope == "COMPILE" }.mapNotNull {
            when (it) {
                is IdeaSingleEntryLibraryDependency -> {
                    it.file.toPath()
                }

                is BackwardsCompatibleIdeaModuleDependency -> {
                    println(it.targetModuleName)
                    throw RuntimeException("Not done yet")
                }

                else -> throw RuntimeException("Not implemented for ${it.javaClass.interfaces.map { it.name }.joinToString(", ")}")
            }
        }
    }


}

fun getGradleApiModels(): List<Class<*>> {
    val l = listOf(
            org.gradle.tooling.model.eclipse.HierarchicalEclipseProject::class.java,
            org.gradle.tooling.model.eclipse.EclipseProject::class.java,
            org.gradle.tooling.model.idea.IdeaProject::class.java,
            org.gradle.tooling.model.GradleProject::class.java,
            org.gradle.tooling.model.idea.BasicIdeaProject::class.java,
            org.gradle.tooling.model.build.BuildEnvironment::class.java,
            org.gradle.tooling.model.gradle.GradleBuild::class.java,
            org.gradle.tooling.model.gradle.ProjectPublications::class.java)
    return l
}