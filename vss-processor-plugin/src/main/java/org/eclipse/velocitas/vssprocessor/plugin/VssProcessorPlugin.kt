/*
 * Copyright (c) 2023 - 2024 Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.eclipse.velocitas.vssprocessor.plugin

import javax.inject.Inject
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.tasks.ExtractAnnotations
import org.eclipse.velocitas.vssprocessor.VssModelGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

open class VssProcessorPluginExtension
@Inject
internal constructor(objectFactory: ObjectFactory) {
    /**
     * The default search path is the $rootProject/vss folder. The defined folder will be crawled for all compatible
     * extension types by this plugin.
     */
    val searchPath: Property<String> = objectFactory.property(String::class.java).convention("")
}

/**
 * This Plugin searches for compatible VSS files, generates VSS Model classes and copies them into an output folder
 * which is added as a main sourceSet.
 */
@Suppress("unused") // Used as a Plugin entry point by Gradle
class VssProcessorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extensions = project.extensions
        val vssProcessorExtension = extensions.create<VssProcessorPluginExtension>(EXTENSION_NAME)

        // The extension variables are only available after the project has been evaluated
        project.afterEvaluate {
            val modelGenerator = VssModelGenerator(project.projectDir, logger)

            val sourceSetBaseDir = modelGenerator.sourceSetBaseDir
            val sourceSetBaseDirProperty = project.objects.directoryProperty()
            sourceSetBaseDirProperty.set(sourceSetBaseDir)

            addGeneratedPathToSourceSets(project, sourceSetBaseDirProperty)

            val generateVssModelsTask = project.tasks.register<GenerateVssModelsTask>(GENERATE_TASK_NAME) {
                vssModelGenerator = modelGenerator

                val vssDirProperty = readVssDir(vssProcessorExtension)
                this.vssDir.set(vssDirProperty)

                generatedOutputDir.set(sourceSetBaseDirProperty)
            }

            // Android build lifecycle
            tasks.matching { it.name == "preBuild" }.configureEach {
                dependsOn(generateVssModelsTask)
            }

            // IDE Gradle Sync integration (automatically generates on Gradle Sync)
            tasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach {
                dependsOn(generateVssModelsTask)
            }

            // Kotlin / Java compilation
            tasks.withType(KotlinCompile::class.java).configureEach {
                dependsOn(generateVssModelsTask.get())
                source(sourceSetBaseDirProperty)
            }
            tasks.withType(JavaCompile::class.java).configureEach {
                dependsOn(generateVssModelsTask.get())
            }
            tasks.withType(ExtractAnnotations::class.java).configureEach {
                dependsOn(generateVssModelsTask.get())
            }
        }
    }

    private fun Project.readVssDir(vssProcessorExtension: VssProcessorPluginExtension): DirectoryProperty {
        val vssDirProperty = project.objects.directoryProperty()
        val vssDirProvider = vssProcessorExtension.searchPath.map { path ->
            val vssPath = path.ifEmpty { VSS_FOLDER_NAME }
            file(vssPath)
        }
        vssDirProperty.fileProvider(vssDirProvider)
        return vssDirProperty
    }

    private fun addGeneratedPathToSourceSets(
        project: Project,
        sourceSetBaseDirProperty: DirectoryProperty,
    ) {
        val extensions = project.extensions
        val pluginManager = project.pluginManager

        val isAndroidApplication = pluginManager.hasPlugin(PLUGIN_ID_ANDROID_APPLICATION)
        val isAndroidLibrary = pluginManager.hasPlugin(PLUGIN_ID_ANDROID_LIBRARY)
        val isJavaProject = javaPlugins.any { pluginManager.hasPlugin(it) }

        val dirPath = sourceSetBaseDirProperty.asFile.get().absolutePath

        if (isAndroidApplication) {
            val androidExtension = extensions.getByType(ApplicationExtension::class.java)
            val mainSourceSet = androidExtension.sourceSets.named(SOURCESET_MAIN_NAME).get()
            mainSourceSet.kotlin.directories.add(dirPath)
        } else if (isAndroidLibrary) {
            val androidExtension = extensions.getByType(LibraryExtension::class.java)
            val mainSourceSet = androidExtension.sourceSets.named(SOURCESET_MAIN_NAME).get()
            mainSourceSet.kotlin.directories.add(dirPath)
        } else if (isJavaProject) {
            val sourceSets = extensions.getByType(SourceSetContainer::class.java)
            val mainSourceSet = sourceSets.named(SOURCESET_MAIN_NAME).get()
            mainSourceSet.java.srcDirs(sourceSetBaseDirProperty)
        } else {
            throw GradleException("Project does not contain any supported plugin")
        }
    }

    companion object {
        private const val EXTENSION_NAME = "vssProcessor"
        private const val VSS_FOLDER_NAME = "vss"
        private const val GENERATE_TASK_NAME = "generateVssModels"
        private const val SOURCESET_MAIN_NAME = "main"
        private const val PLUGIN_ID_ANDROID_APPLICATION = "com.android.application"
        private const val PLUGIN_ID_ANDROID_LIBRARY = "com.android.library"
        private val javaPlugins = arrayOf(
            "java",
            "java-library",
        )
    }
}

/**
 * This task takes an input directory [vssDir] which should contain all available VSS files and an
 * output directory [generatedOutputDir] where all files are copied to so the VSSProcessor can work with them.
 */
@CacheableTask
private abstract class GenerateVssModelsTask : DefaultTask() {
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputDirectory
    abstract val vssDir: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedOutputDir: DirectoryProperty

    @Internal
    lateinit var vssModelGenerator: VssModelGenerator

    @TaskAction
    fun generate() {
        val outputDir = generatedOutputDir.asFile.get()
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val vssFiles = vssDir.asFile.get()
            .walk()
            .filter { it.isFile }
            .filter { validVssExtension.contains(it.extension) }
            .toSet()

        vssFiles.forEach { file ->
            logger.info("Found VSS file: ${file.name}")
        }

        vssModelGenerator.generate(vssFiles)
    }

    companion object {
        private val validVssExtension = setOf("yml", "yaml", "json") // keep VssFileExtension aligned
    }
}
