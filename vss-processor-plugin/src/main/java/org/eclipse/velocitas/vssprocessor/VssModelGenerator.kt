/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.velocitas.vssprocessor

import java.io.File
import com.squareup.kotlinpoet.FileSpec
import org.eclipse.kuksa.vsscore.model.className
import org.eclipse.velocitas.vssprocessor.parser.factory.VssParserFactory
import org.eclipse.velocitas.vssprocessor.spec.VssNodeSpecModel
import org.eclipse.velocitas.vssprocessor.spec.VssPath
import org.gradle.api.logging.Logger

/**
 * Generates a [org.eclipse.kuksa.vsscore.model.VssNode] for every entry listed in the input file.
 * These nodes are a usable kotlin data class reflection of the element.
 *
 * @param projectDir absolute path to the project which has embedded the vss-processor-plugin.
 * @param logger to log output with
 */
class VssModelGenerator(
    projectDir: File,
    private val logger: Logger,
) {
    private val vssParserFactory = VssParserFactory()

    private val packageName = "org.eclipse.velocitas.vss"

    private val buildDir = File(projectDir, "build")
    private val generatedDir = File(buildDir, "generated")
    private val vssDir = File(generatedDir, "vss")
    val sourceSetBaseDir = File(vssDir, "kotlin")
    private val outputDir = File(sourceSetBaseDir, packageName.replace(".", File.separator))

    fun generate(vssFiles: Set<File>) {
        val simpleNodeElements = mutableListOf<VssNodeSpecModel>()

        vssFiles.forEach { definitionFile ->
            logger.info("Parsing models for definition file: ${definitionFile.name}")
            val vssParser = vssParserFactory.create(definitionFile)
            val specModels = vssParser.parseNodes(definitionFile)

            simpleNodeElements.addAll(specModels)
        }

        val vssPathToVssNodeElement = simpleNodeElements
            .distinctBy { it.vssPath }
            .associateBy({ VssPath(it.vssPath) }, { it })

        generateModelFiles(vssPathToVssNodeElement)
    }

    private fun generateModelFiles(vssPathToVssNode: Map<VssPath, VssNodeSpecModel>) {
        val duplicateNodeNames = vssPathToVssNode.keys
            .groupBy { it.leaf }
            .filter { it.value.size > 1 }
            .keys

        logger.info("Ambiguous VssNode - Generate nested classes: $duplicateNodeNames")

        for ((vssPath, specModel) in vssPathToVssNode) {
            // Every duplicate is produced as a nested class - No separate file should be generated
            if (duplicateNodeNames.contains(vssPath.leaf)) {
                continue
            }

            specModel.logger = logger
            val classSpec = specModel.createClassSpec(
                packageName,
                vssPathToVssNode.values,
                duplicateNodeNames,
            )

            val className = classSpec.name ?: throw NoSuchFieldException("Class spec $classSpec has no name field!")
            val fileSpecBuilder = FileSpec.builder(packageName, className)

            val parentImport = buildParentImport(specModel, vssPathToVssNode, duplicateNodeNames)
            if (parentImport.isNotEmpty()) {
                fileSpecBuilder.addImport(packageName, parentImport)
            }

            val file = fileSpecBuilder
                .addType(classSpec)
                .build()

            outputDir.mkdirs()
            outputDir.resolve(file.name + ".kt").writeText(file.toString())
        }
    }

    @Suppress("LoopWithTooManyJumpStatements") // needs to be refactored to be more readable
    // Uses a map of vssPaths to specModels which are validated if it contains a parent of the given specModel.
    private fun buildParentImport(
        specModel: VssNodeSpecModel,
        vssPathToVssNode: Map<VssPath, VssNodeSpecModel>,
        duplicateNodeNames: Collection<String>,
    ): String {
        if (!specModel.vssPath.contains(".")) {
            return ""
        }

        val parentClassNames = mutableListOf<String>()
        var currentVssPath = specModel.vssPath.substringBeforeLast(".")

        while (currentVssPath.isNotEmpty()) {
            val node = vssPathToVssNode[VssPath(currentVssPath)]
            val className = node?.className
                ?: ("Vss" + currentVssPath.substringAfterLast(".").replaceFirstChar { it.uppercase() })
            parentClassNames.add(0, className)

            val leaf = currentVssPath.substringAfterLast(".")
            if (!duplicateNodeNames.contains(leaf)) {
                break
            }

            if (!currentVssPath.contains(".")) {
                break
            }
            currentVssPath = currentVssPath.substringBeforeLast(".")
        }

        if (parentClassNames.isEmpty()) {
            logger.info("Could not create import string for: ${specModel.vssPath} - No parent was found")
            return ""
        }

        return parentClassNames.joinToString(".")
    }
}
