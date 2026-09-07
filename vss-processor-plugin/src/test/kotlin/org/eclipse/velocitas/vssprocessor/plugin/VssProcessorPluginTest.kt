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

package org.eclipse.velocitas.vssprocessor.plugin

import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.eclipse.velocitas.kotest.Functional
import org.eclipse.velocitas.vssprocessor.plugin.generator.project.GradleProject.Companion.TEST_FOLDER_NAME_DEFAULT
import org.eclipse.velocitas.vssprocessor.plugin.generator.project.VssProcessorLibProject
import org.eclipse.velocitas.vssprocessor.plugin.generator.project.VssProcessorPluginProject
import org.eclipse.velocitas.vssprocessor.plugin.generator.project.dollar
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

// Note:
// - Debugging into functional gradle test cases do not work.
// - Sometimes test errors are obscured so try looking into the test report to see the actual one.
//
// ./gradlew :vss-processor-plugin:test -Dkotest.tags="Functional"
@OptIn(ExperimentalPathApi::class)
class VssProcessorPluginTest : BehaviorSpec({
    tags(Functional)

    given("A VSSProcessorPlugin project") {
        val pluginProject = VssProcessorPluginProject()
        val rootProjectDir = pluginProject.rootProjectDir.toFile()
        val gradleRunner = GradleRunner.create()
            .forwardOutput()
            .withGradleVersion(GRADLE_VERSION_TEST)
            .withPluginClasspath()
            .withProjectDir(rootProjectDir)

        afterSpec {
            pluginProject.close()
        }

        `when`("the plugin is applied") {
            pluginProject.generate()

            val pluginResult = gradleRunner.build()

            then("it should build successfully") {
                pluginResult.output shouldContain "BUILD SUCCESSFUL"
            }

            xand("an Android library project is added") {
                val androidLibProject =
                    org.eclipse.velocitas.vssprocessor.plugin.generator.project.AndroidLibProject("lib")
                androidLibProject.generate()

                pluginProject.add(androidLibProject)

                afterContainer {
                    pluginProject.refresh() // So the plugin project does not have 2 :lib includes
                }

                and("the generateVssModels task is executed without correct input") {
                    val result = gradleRunner
                        .withArguments(GENERATE_VSS_MODELS_TASK_NAME)
                        .buildAndFail()

                    then("it should throw an Exception") {
                        result.output shouldContain "Could not create task ':lib:generateVssModels'"
                    }
                }
            }

            // The following tests are mainly testing the configuration cache
            // #1: A full clean of the build folder AND gradle cache -> SUCCESS
            // #2: A full clean of the build folder BUT gradle cache stays -> FROM_CACHE
            // #3: No clean -> UP_TO_DATE
            // #4: Gradle + plugin / task input changed -> SUCCESS
            // #5: Input file name changed -> SUCCESS
            xand("a VSS compatible Android library project is added") {
                val vssFile2: File
                val vssProcessorProject = VssProcessorLibProject("lib").apply {
                    copyVssFiles(vssDir, VSS_TEST_FILE_NAME)
                    vssFile2 = copyVssFiles(vssDir2, VSS_TEST_FILE_MINIMAL_NAME)
                    generate()
                }
                val vssDir2 = vssProcessorProject.vssDir2
                val vssDir2Path = vssDir2.pathString

                pluginProject.add(vssProcessorProject)

                and("the generateVssModels task is executed with build cache the #1 time") {
                    pluginProject.localCacheFolder.deleteRecursively()

                    val result = gradleRunner
                        .withArguments("clean", "--build-cache", GENERATE_VSS_MODELS_TASK_NAME)
                        .build()

                    println("generateVssModelsTask + Build Cache #1 output: ${result.output}")

                    then("it should build successfully") {
                        val outcome = result.task(":lib:$GENERATE_VSS_MODELS_TASK_NAME")?.outcome

                        outcome shouldBe TaskOutcome.SUCCESS
                    }
                }

                and("the generateVssModels task is executed with build cache the #2 time") {
                    val result = gradleRunner
                        .withArguments("clean", "--build-cache", GENERATE_VSS_MODELS_TASK_NAME)
                        .build()

                    println("generateVssModelsTask + Build Cache #2 output: ${result.output}")

                    then("it should build from cache") {
                        val outcome = result.task(":lib:$GENERATE_VSS_MODELS_TASK_NAME")?.outcome

                        outcome shouldBe TaskOutcome.FROM_CACHE
                    }
                }

                and("the generateVssModels task is executed with build cache the #3 time") {
                    val kspInputDir = vssProcessorProject.buildDir.resolve(KSP_INPUT_BUILD_DIRECTORY)
                    val result = gradleRunner
                        .withArguments("--build-cache", GENERATE_VSS_MODELS_TASK_NAME)
                        .build()

                    println("generateVssModelsTask + Build Cache #3 output: ${result.output}")

                    then("it should be up to date") {
                        val outcome = result.task(":lib:$GENERATE_VSS_MODELS_TASK_NAME")?.outcome

                        outcome shouldBe TaskOutcome.UP_TO_DATE
                    }

                    then("it should copy all vss files") {
                        val vssFile = kspInputDir.resolve(VSS_TEST_FILE_NAME)

                        vssFile.exists() shouldBe true
                    }
                }

                and("the input of the generateVssModelsTask changes") {
                    val projectVssDir2 = vssDir2Path.substringAfter(TEST_FOLDER_NAME_DEFAULT)
                    vssProcessorProject.generate(
                        """
                        vssProcessor {
                            searchPath = "${dollar}rootDir/$projectVssDir2"
                        }
                        """.trimIndent(),
                    )

                    val result = gradleRunner
                        .withArguments("--build-cache", GENERATE_VSS_MODELS_TASK_NAME)
                        .build()

                    println("generateVssModelsTask + Build Cache #4 output: ${result.output}")

                    then("it should build successfully") {
                        val outcome = result.task(":lib:$GENERATE_VSS_MODELS_TASK_NAME")?.outcome

                        outcome shouldBe TaskOutcome.SUCCESS
                    }
                }

                and("the name of the input of the generateVssModelsTask changes") {
                    vssFile2.renameTo(File("$vssDir2Path/vss_rel_4.0_test_renamed.yml"))
                    val result = gradleRunner
                        .withArguments("--build-cache", GENERATE_VSS_MODELS_TASK_NAME)
                        .build()

                    println("generateVssModelsTask + Build Cache #5 output: ${result.output}")

                    then("it should build successfully") {
                        val outcome = result.task(":lib:$GENERATE_VSS_MODELS_TASK_NAME")?.outcome

                        outcome shouldBe TaskOutcome.SUCCESS
                    }
                }
            }
        }
    }
    given("A java library project with no VSS files in the configured directory") {
        tags(Functional)

        val tempDir = createTempDirectory("empty_vss_test")
        val emptyVssDir = tempDir.resolve("vss-empty").also { it.toFile().mkdirs() }
        val libDir = tempDir.resolve("lib").also { it.toFile().mkdirs() }

        tempDir.resolve("settings.gradle.kts").toFile().writeText(
            """
            pluginManagement {
                includeBuild("${System.getProperty("user.dir")}")
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenLocal()
                    mavenCentral()
                }
            }
            include(":lib")
            """.trimIndent(),
        )

        tempDir.resolve("build.gradle.kts").toFile().writeText("")

        libDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("java-library")
                id("org.eclipse.velocitas.vss-processor-plugin")
            }
            vssProcessor {
                searchPath = "${emptyVssDir.toAbsolutePath()}"
            }
            """.trimIndent(),
        )

        val emptyVssDirRunner = GradleRunner.create()
            .forwardOutput()
            .withGradleVersion(GRADLE_VERSION_TEST)
            .withPluginClasspath()
            .withProjectDir(tempDir.toFile())

        afterSpec { tempDir.toFile().deleteRecursively() }

        `when`("the generateVssModels task is executed") {
            val result = emptyVssDirRunner
                .withArguments(":lib:generateVssModels")
                .buildAndFail()

            then("the build fails with a descriptive error") {
                result.output shouldContain "No VSS files found"
            }
        }
    }
}) {
    companion object {
        private const val VSS_TEST_FILE_NAME = "vss_rel_4.0_test.yaml"
        private const val GENERATE_VSS_MODELS_TASK_NAME = "generateVssModels"
        private const val VSS_TEST_FILE_MINIMAL_NAME = "vss_rel_4.0_test_minimal.yaml"
        private const val GRADLE_VERSION_TEST = "9.7.1"
        private const val KSP_INPUT_BUILD_DIRECTORY = "kspInput"
    }
}
