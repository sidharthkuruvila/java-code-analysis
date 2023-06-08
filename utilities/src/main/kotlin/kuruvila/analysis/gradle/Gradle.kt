package kuruvila.analysis.gradle

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.converters.BackwardsCompatibleIdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import java.io.File
import java.lang.RuntimeException
import java.nio.file.Path


data class ModuleInfo(val name: String, val jars: List<Path>, val sources: List<Path>)

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
                ModuleInfo(module.name, compileDependencyPaths(module), sources)
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